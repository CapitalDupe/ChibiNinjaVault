package com.ninjavault.app.crypto

import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `correct pin verifies`() {
        val result = PasswordHasher.hash("1234".toCharArray())
        assertTrue(PasswordHasher.verify("1234".toCharArray(), result.hashBase64, result.saltBase64))
    }

    @Test
    fun `wrong pin fails verification`() {
        val result = PasswordHasher.hash("1234".toCharArray())
        assertFalse(PasswordHasher.verify("4321".toCharArray(), result.hashBase64, result.saltBase64))
    }

    @Test
    fun `same pin produces different hash with different salt`() {
        val a = PasswordHasher.hash("1234".toCharArray())
        val b = PasswordHasher.hash("1234".toCharArray())
        assertNotEquals(a.saltBase64, b.saltBase64)
        assertNotEquals(a.hashBase64, b.hashBase64)
    }
}
