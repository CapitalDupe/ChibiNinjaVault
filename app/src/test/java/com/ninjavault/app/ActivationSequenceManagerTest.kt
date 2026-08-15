package com.ninjavault.app.vault

import org.junit.Assert.*
import org.junit.Test

class ActivationSequenceManagerTest {

    @Test
    fun `correct sequence unlocks`() {
        val manager = ActivationSequenceManager(
            configuredSequence = listOf(SwipeDirection.LEFT, SwipeDirection.RIGHT, SwipeDirection.UP)
        )
        manager.onSwipe(SwipeDirection.LEFT)
        manager.onSwipe(SwipeDirection.RIGHT)
        manager.onSwipe(SwipeDirection.UP)
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.Unlocked)
    }

    @Test
    fun `wrong swipe resets buffer without revealing position`() {
        val manager = ActivationSequenceManager(
            configuredSequence = listOf(SwipeDirection.LEFT, SwipeDirection.RIGHT, SwipeDirection.UP)
        )
        manager.onSwipe(SwipeDirection.LEFT)
        manager.onSwipe(SwipeDirection.DOWN) // wrong
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.Failed)

        // Buffer should have reset; completing full correct sequence from scratch still works.
        manager.onSwipe(SwipeDirection.LEFT)
        manager.onSwipe(SwipeDirection.RIGHT)
        manager.onSwipe(SwipeDirection.UP)
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.Unlocked)
    }

    @Test
    fun `repeated failures trigger lockout`() {
        val manager = ActivationSequenceManager(
            configuredSequence = listOf(SwipeDirection.LEFT, SwipeDirection.RIGHT),
            maxAttemptsBeforeLockout = 3,
            lockoutDurationMillis = 1000L
        )
        var now = 0L
        repeat(3) {
            manager.onSwipe(SwipeDirection.DOWN, now) // always wrong -> 3 failures
            now += 10
        }
        assertTrue(manager.isLockedOut(now))
        // Even a correct sequence should be ignored while locked out.
        manager.onSwipe(SwipeDirection.LEFT, now)
        assertTrue(manager.state.value is ActivationSequenceManager.ActivationState.LockedOut)
    }

    @Test
    fun `lockout expires after duration`() {
        val manager = ActivationSequenceManager(
            configuredSequence = listOf(SwipeDirection.LEFT),
            maxAttemptsBeforeLockout = 1,
            lockoutDurationMillis = 500L
        )
        manager.onSwipe(SwipeDirection.DOWN, 0L) // triggers lockout
        assertTrue(manager.isLockedOut(100L))
        assertFalse(manager.isLockedOut(10_000L))
    }
}
