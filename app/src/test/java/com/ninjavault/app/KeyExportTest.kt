package com.ninjavault.app

import com.ninjavault.app.crypto.KeyExport
import com.ninjavault.app.crypto.KeyFileException
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class KeyExportTest {
    private val key = ByteArray(32) { it.toByte() }
    private val pw = "correct horse".toCharArray()

    private fun seal(name: String = "photo.jpg", mime: String = "image/jpeg") =
        KeyExport.seal(key, pw.copyOf(), name, mime, iterations = 1_000)

    @Test
    fun `round trip restores key name and mime`() {
        val opened = KeyExport.open(seal("na\u00efve file.json", "application/json"), pw.copyOf())
        assertArrayEquals(key, opened.fileKey)
        assertEquals("na\u00efve file.json", opened.displayName)
        assertEquals("application/json", opened.mimeType)
    }

    @Test
    fun `key file never contains the raw key`() {
        val text = seal()
        assertFalse(text.contains(Base64.getEncoder().encodeToString(key)))
    }

    @Test
    fun `wrong password is rejected`() {
        try {
            KeyExport.open(seal(), "wrong password".toCharArray())
            fail("expected KeyFileException")
        } catch (e: KeyFileException) {
            assertTrue(e.message!!.contains("password", ignoreCase = true))
        }
    }

    @Test
    fun `tampering with the name is detected`() {
        val enc = Base64.getEncoder()
        val tampered = seal("a.txt").lines().joinToString("\n") {
            if (it.startsWith("name=")) "name=" + enc.encodeToString("evil.exe".toByteArray()) else it
        }
        try {
            KeyExport.open(tampered, pw.copyOf())
            fail("expected KeyFileException")
        } catch (e: KeyFileException) { /* expected */ }
    }

    @Test
    fun `garbage is rejected cleanly`() {
        for (junk in listOf("", "hello", "ADXKEY1\niter=abc", "ADXKEY1\niter=5000\nsalt=!!!")) {
            try {
                KeyExport.open(junk, pw.copyOf())
                fail("expected KeyFileException for: $junk")
            } catch (e: KeyFileException) { /* expected */ }
        }
    }

    @Test
    fun `short passwords are refused on export`() {
        try {
            KeyExport.seal(key, "short".toCharArray(), "a", "b", iterations = 1_000)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `two exports of the same key differ`() {
        assertNotEquals(seal(), seal())
    }
}
