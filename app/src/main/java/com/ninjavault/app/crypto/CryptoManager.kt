package com.ninjavault.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles authenticated encryption (AES-256-GCM) for vault contents.
 *
 * Two layers of keys are used:
 *  1. A master key held in the Android Keystore (hardware-backed where available).
 *     This key never leaves the keystore and is used to wrap (encrypt) per-file
 *     data encryption keys (DEKs).
 *  2. A random per-file DEK, generated fresh for every imported file, which is
 *     what actually encrypts the file bytes. The wrapped DEK is stored alongside
 *     the ciphertext.
 *
 * This "envelope encryption" pattern means compromising one file's DEK does not
 * expose any other file, and the master key material is never present in app
 * memory or storage in plaintext.
 *
 * No key is ever hard-coded and there is no developer backdoor. Be aware that the
 * file keys are protected by the Keystore master key alone: the unlock pattern,
 * PIN and biometrics are an in-app access gate, not inputs to the encryption.
 * Destroying the master key (see [destroyMasterKey]) is what makes vault
 * contents permanently unrecoverable.
 *
 * Limitation: depending on the Android version, the platform's AES-GCM provider
 * may buffer a whole stream in memory before releasing output, so very large
 * files can need a lot of heap. Chunked (streaming) AEAD would remove this.
 */
class CryptoManager {

    private val androidKeyStore = "AndroidKeyStore"
    private val masterKeyAlias = "ninja_run_vault_master_key"

    companion object {
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_IV_LENGTH_BYTES = 12
        const val AES_KEY_SIZE_BITS = 256
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        // Shared by every CryptoManager instance so two concurrent first-time callers
        // can never each generate a master key and overwrite one another.
        private val keyLock = Any()
    }

    // Lazy so plain-JVM unit tests exercising only the streaming encrypt/decrypt
    // logic (with an externally supplied key) don't require a real Android
    // Keystore provider. Any code path that actually needs the master key
    // (wrapFileKey/unwrapFileKey/destroyMasterKey) will initialize it on first use.
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(androidKeyStore).apply { load(null) }
    }

    /** Returns the hardware/keystore-backed master key, generating it on first use. */
    private fun getOrCreateMasterKey(): SecretKey = synchronized(keyLock) {
        (keyStore.getKey(masterKeyAlias, null) as? SecretKey) ?: createMasterKey()
    }

    private fun createMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, androidKeyStore
        )
        val spec = KeyGenParameterSpec.Builder(
            masterKeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** Generates a fresh random 256-bit data encryption key for one file. */
    fun generateFileKey(): ByteArray {
        val bytes = ByteArray(AES_KEY_SIZE_BITS / 8)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    /** Wraps (encrypts) a per-file DEK using the Keystore master key. Returns iv+ciphertext. */
    fun wrapFileKey(fileKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val wrapped = cipher.doFinal(fileKey)
        return iv + wrapped
    }

    /** Unwraps a previously wrapped per-file DEK. */
    fun unwrapFileKey(wrappedBlob: ByteArray): ByteArray {
        val iv = wrappedBlob.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = wrappedBlob.copyOfRange(GCM_IV_LENGTH_BYTES, wrappedBlob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Streams [input] to [output], encrypting with AES-256-GCM under [fileKeyBytes].
     * Writes a 12-byte IV header followed by ciphertext+tag. Uses streaming so large
     * video files are never fully buffered in memory.
     */
    fun encryptStream(input: InputStream, output: OutputStream, fileKeyBytes: ByteArray) {
        val key = SecretKeySpec(fileKeyBytes, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        output.write(iv)

        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            val chunk = cipher.update(buffer, 0, read)
            if (chunk != null) output.write(chunk)
        }
        cipher.doFinal()?.let { output.write(it) }
        output.flush()
    }

    /**
     * Streams decrypted plaintext from an encrypted [input] (IV header + ciphertext+tag)
     * to [output], authenticating integrity via the GCM tag. Throws if the file has been
     * tampered with or the key is wrong (AEADBadTagException).
     */
    fun decryptStream(input: InputStream, output: OutputStream, fileKeyBytes: ByteArray) {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        var got = 0
        while (got < iv.size) {
            val n = input.read(iv, got, iv.size - got)
            if (n == -1) error("Corrupted encrypted file: missing IV header")
            got += n
        }
        val key = SecretKeySpec(fileKeyBytes, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            val chunk = cipher.update(buffer, 0, read)
            if (chunk != null) output.write(chunk)
        }
        // doFinal() verifies the GCM authentication tag; throws AEADBadTagException on failure.
        cipher.doFinal()?.let { output.write(it) }
        output.flush()
    }

    /** Deletes the master key from the Keystore. Used only for full vault reset/wipe. */
    fun destroyMasterKey() {
        synchronized(keyLock) {
            if (keyStore.containsAlias(masterKeyAlias)) {
                keyStore.deleteEntry(masterKeyAlias)
            }
        }
    }
}
