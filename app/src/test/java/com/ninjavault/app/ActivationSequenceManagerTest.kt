package com.ninjavault.app.vault

import org.junit.Assert.*
import org.junit.Test

class ActivationSequenceManagerTest {

    private val pattern = listOf(SwipeDirection.LEFT, SwipeDirection.RIGHT, SwipeDirection.UP)

    private class FakeStore : LockoutStore {
        override var failedAttempts: Int = 0
        override var lockedUntilMillis: Long = 0L
    }

    @Test
    fun `correct sequence unlocks`() {
        val manager = ActivationSequenceManager(configuredSequence = pattern)
        manager.onSwipe(SwipeDirection.LEFT, 0)
        manager.onSwipe(SwipeDirection.RIGHT, 10)
        manager.onSwipe(SwipeDirection.UP, 20)
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.Unlocked)
    }

    @Test
    fun `wrong swipe after a partial match resets buffer without revealing position`() {
        val manager = ActivationSequenceManager(configuredSequence = pattern)
        manager.onSwipe(SwipeDirection.LEFT, 0)
        manager.onSwipe(SwipeDirection.RIGHT, 10)
        manager.onSwipe(SwipeDirection.DOWN, 20) // wrong third move
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.Failed)

        // Buffer should have reset; completing the full correct sequence from scratch still works.
        manager.onSwipe(SwipeDirection.LEFT, 30)
        manager.onSwipe(SwipeDirection.RIGHT, 40)
        manager.onSwipe(SwipeDirection.UP, 50)
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.Unlocked)
    }

    @Test
    fun `mismatching swipe is re-evaluated as the start of a new attempt`() {
        val manager = ActivationSequenceManager(configuredSequence = pattern)
        manager.onSwipe(SwipeDirection.LEFT, 0)
        manager.onSwipe(SwipeDirection.LEFT, 10) // wrong, but could start a fresh attempt
        manager.onSwipe(SwipeDirection.RIGHT, 20)
        manager.onSwipe(SwipeDirection.UP, 30)
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.Unlocked)
    }

    @Test
    fun `ordinary gameplay swipes do not count as failed attempts`() {
        val manager = ActivationSequenceManager(
            configuredSequence = pattern,
            maxAttemptsBeforeLockout = 2
        )
        var now = 0L
        repeat(50) {
            manager.onSwipe(SwipeDirection.DOWN, now) // never matches even the first move
            now += 10
        }
        assertFalse(manager.isLockedOut(now))
    }

    @Test
    fun `buffer is discarded after a pause in input`() {
        val manager = ActivationSequenceManager(configuredSequence = pattern, inputGapMillis = 1_000L)
        manager.onSwipe(SwipeDirection.LEFT, 0)
        manager.onSwipe(SwipeDirection.RIGHT, 100)
        manager.onSwipe(SwipeDirection.UP, 10_000) // far too late to continue the attempt
        assertFalse(manager.state.value is ActivationSequenceManager.ActivationState.Unlocked)
    }

    @Test
    fun `repeated failed attempts trigger lockout`() {
        val manager = ActivationSequenceManager(
            configuredSequence = pattern,
            maxAttemptsBeforeLockout = 3,
            lockoutDurationMillis = 1000L
        )
        var now = 0L
        repeat(3) {
            manager.onSwipe(SwipeDirection.LEFT, now); now += 10
            manager.onSwipe(SwipeDirection.RIGHT, now); now += 10
            manager.onSwipe(SwipeDirection.DOWN, now); now += 10 // wrong third move
        }
        assertTrue(manager.isLockedOut(now))
        // Even a correct sequence should be ignored while locked out.
        manager.onSwipe(SwipeDirection.LEFT, now)
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.LockedOut)
    }

    @Test
    fun `lockout expires after duration`() {
        val manager = ActivationSequenceManager(
            configuredSequence = listOf(SwipeDirection.LEFT, SwipeDirection.RIGHT),
            maxAttemptsBeforeLockout = 1,
            lockoutDurationMillis = 500L
        )
        manager.onSwipe(SwipeDirection.LEFT, 0L)
        manager.onSwipe(SwipeDirection.DOWN, 10L) // wrong second move triggers lockout
        assertTrue(manager.isLockedOut(100L))
        assertFalse(manager.isLockedOut(10_000L))
    }

    @Test
    fun `lockout survives creating a new manager`() {
        val store = FakeStore()
        val first = ActivationSequenceManager(
            configuredSequence = pattern,
            maxAttemptsBeforeLockout = 1,
            lockoutDurationMillis = 1_000L,
            store = store
        )
        first.onSwipe(SwipeDirection.LEFT, 0L)
        first.onSwipe(SwipeDirection.RIGHT, 10L)
        first.onSwipe(SwipeDirection.DOWN, 20L)
        assertTrue(first.isLockedOut(100L))

        // Simulates an app restart / screen recreation.
        val second = ActivationSequenceManager(
            configuredSequence = pattern,
            maxAttemptsBeforeLockout = 1,
            lockoutDurationMillis = 1_000L,
            store = store
        )
        assertTrue(second.isLockedOut(100L))
    }

    @Test
    fun `successful unlock clears stored failures`() {
        val store = FakeStore()
        val manager = ActivationSequenceManager(configuredSequence = pattern, store = store)
        manager.onSwipe(SwipeDirection.LEFT, 0L)
        manager.onSwipe(SwipeDirection.RIGHT, 10L)
        manager.onSwipe(SwipeDirection.DOWN, 20L)
        assertEquals(1, store.failedAttempts)
        manager.onSwipe(SwipeDirection.LEFT, 30L)
        manager.onSwipe(SwipeDirection.RIGHT, 40L)
        manager.onSwipe(SwipeDirection.UP, 50L)
        assertEquals(0, store.failedAttempts)
    }

    @Test
    fun `lockout duration is capped`() {
        val manager = ActivationSequenceManager(
            configuredSequence = pattern,
            maxAttemptsBeforeLockout = 1,
            lockoutDurationMillis = 1_000L,
            maxLockoutMillis = 5_000L
        )
        var now = 0L
        repeat(40) {
            now += 10_000L // always past the previous lockout (cap is 5s)
            manager.onSwipe(SwipeDirection.LEFT, now)
            manager.onSwipe(SwipeDirection.RIGHT, now + 1)
            manager.onSwipe(SwipeDirection.DOWN, now + 2)
        }
        assertTrue(manager.isLockedOut(now + 3))
        assertFalse(manager.isLockedOut(now + 6_000L))
    }
}
