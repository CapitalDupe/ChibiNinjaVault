package com.ninjavault.app.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when a key file is malformed, the password is wrong, or the file was modified. */
class KeyFileException(message: String) : Exception(message)

/**
 * Password-protected export/import of a single file's data-encryption key (DEK).
 *
 * The DEK itself is never written in the clear: it is encrypted with AES-256-GCM under a key
 * derived from the user's password (PBKDF2-HMAC-SHA256, random salt). The file name and MIME
 * type are bound in as authenticated data so they can't be swapped without detection.
 *
 * Text format (".adxkey"), one `field=value` per line, binary fields Base64:
 *   ADXKEY1 / iter / salt / iv / name / mime / key (ciphertext + 16-byte GCM tag)
 *
 * Pure JVM crypto (no Android classes) so it is unit-testable.
 */
object KeyExport {
    const val EXTENSION = "adxkey"
    const val MIN_PASSWORD_LENGTH = 8
    const val PBKDF2_ITERATIONS = 210_000

    private const val MAGIC = "ADXKEY1"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val MAX_FILE_CHARS = 16 * 1024
    private const val NOT_A_KEY_FILE = "That doesn't look like a valid key file."
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    class Opened(val fileKey: ByteArray, val displayName: String, val mimeType: String)

    fun seal(
        fileKey: ByteArray,
        password: CharArray,
        displayName: String,
        mimeType: String,
        iterations: Int = PBKDF2_ITERATIONS
    ): String {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters" }
        require(fileKey.size == KEY_BYTES) { "Unexpected key length" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val kek = derive(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(displayName, mimeType))
            val sealed = cipher.doFinal(fileKey)
            val enc = Base64.getEncoder()
            return listOf(
                MAGIC,
                "iter=$iterations",
                "salt=${enc.encodeToString(salt)}",
                "iv=${enc.encodeToString(iv)}",
                "name=${enc.encodeToString(displayName.toByteArray(Charsets.UTF_8))}",
                "mime=${enc.encodeToString(mimeType.toByteArray(Charsets.UTF_8))}",
                "key=${enc.encodeToString(sealed)}"
            ).joinToString("\n") + "\n"
        } finally {
            kek.fill(0)
        }
    }

    fun open(text: String, password: CharArray): Opened {
        if (text.length > MAX_FILE_CHARS) throw KeyFileException(NOT_A_KEY_FILE)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != MAGIC) throw KeyFileException(NOT_A_KEY_FILE)
        val fields = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()

        try {
            val iterations = fields["iter"]?.toIntOrNull() ?: throw KeyFileException(NOT_A_KEY_FILE)
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw KeyFileException(NOT_A_KEY_FILE)
            val dec = Base64.getDecoder()
            fun field(name: String): ByteArray = dec.decode(fields[name] ?: throw KeyFileException(NOT_A_KEY_FILE))
            val salt = field("salt")
            val iv = field("iv")
            val name = String(field("name"), Charsets.UTF_8)
            val mime = String(field("mime"), Charsets.UTF_8)
            val sealed = field("key")
            if (salt.size < 8 || iv.size != IV_BYTES) throw KeyFileException(NOT_A_KEY_FILE)

            val kek = derive(password, salt, iterations)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
                cipher.updateAAD(aad(name, mime))
                val key = cipher.doFinal(sealed)
                if (key.size != KEY_BYTES) throw KeyFileException(NOT_A_KEY_FILE)
                return Opened(key, name, mime)
            } finally {
                kek.fill(0)
            }
        } catch (e: AEADBadTagException) {
            throw KeyFileException("Wrong password, or the key file was changed.")
        } catch (e: GeneralSecurityException) {
            throw KeyFileException(NOT_A_KEY_FILE)
        } catch (e: IllegalArgumentException) {
            throw KeyFileException(NOT_A_KEY_FILE)
        }
    }

    private fun aad(name: String, mime: String): ByteArray =
        "$MAGIC|$name|$mime".toByteArray(Charsets.UTF_8)

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
