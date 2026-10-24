package com.ninjavault.app.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes the user's optional PIN/passphrase with PBKDF2-HMAC-SHA256 and a random
 * per-user salt. Only the salted hash is ever persisted — never the plaintext PIN.
 */
object PasswordHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    data class HashResult(val hashBase64: String, val saltBase64: String)

    fun hash(secret: CharArray, salt: ByteArray = randomSalt()): HashResult {
        val spec = PBEKeySpec(secret, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return HashResult(
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            saltBase64 = Base64.getEncoder().encodeToString(salt)
        )
    }

    fun verify(secret: CharArray, storedHashBase64: String, storedSaltBase64: String): Boolean {
        val salt = Base64.getDecoder().decode(storedSaltBase64)
        val candidate = hash(secret, salt).hashBase64
        return constantTimeEquals(candidate, storedHashBase64)
    }

    private fun randomSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
