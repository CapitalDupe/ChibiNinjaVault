package com.ninjavault.app.crypto

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException
import kotlin.random.Random

/**
 * Tests the streaming encrypt/decrypt logic directly with a random file key,
 * bypassing Android Keystore (unavailable in plain JVM unit tests). The Keystore
 * wrap/unwrap path has no automated test yet; it needs an instrumented (on-device) test.
 */
class CryptoManagerStreamTest {

    private val crypto = CryptoManager()

    @Test
    fun `round trip produces identical plaintext`() {
        val original = "The quick brown ninja jumps over the lazy vault.".toByteArray()
        val key = Random.nextBytes(32)

        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(ByteArrayInputStream(original), encrypted, key)

        val decrypted = ByteArrayOutputStream()
        crypto.decryptStream(ByteArrayInputStream(encrypted.toByteArray()), decrypted, key)

        assertArrayEquals(original, decrypted.toByteArray())
    }

    @Test
    fun `wrong key fails authentication`() {
        val original = "sensitive data".toByteArray()
        val key = Random.nextBytes(32)
        val wrongKey = Random.nextBytes(32)

        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(ByteArrayInputStream(original), encrypted, key)

        assertThrows(AEADBadTagException::class.java) {
            crypto.decryptStream(ByteArrayInputStream(encrypted.toByteArray()), ByteArrayOutputStream(), wrongKey)
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val original = "sensitive data".toByteArray()
        val key = Random.nextBytes(32)

        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(ByteArrayInputStream(original), encrypted, key)
        val tampered = encrypted.toByteArray()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1] + 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            crypto.decryptStream(ByteArrayInputStream(tampered), ByteArrayOutputStream(), key)
        }
    }

    @Test
    fun `large data round trips across multiple buffer chunks`() {
        val original = Random.nextBytes(500_000) // spans several 64KB buffer reads
        val key = Random.nextBytes(32)

        val encrypted = ByteArrayOutputStream()
        crypto.encryptStream(ByteArrayInputStream(original), encrypted, key)

        val decrypted = ByteArrayOutputStream()
        crypto.decryptStream(ByteArrayInputStream(encrypted.toByteArray()), decrypted, key)

        assertArrayEquals(original, decrypted.toByteArray())
    }
}
