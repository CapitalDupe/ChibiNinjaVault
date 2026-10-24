package com.ninjavault.app.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

/**
 * Persists lockout state so that restarting the app (or rotating the screen)
 * does not hand an attacker a fresh set of attempts.
 */
interface LockoutStore {
    var failedAttempts: Int
    var lockedUntilMillis: Long
}

/**
 * Validates the user-configured unlock sequence (a pattern of swipes performed
 * during a Ninja Run — e.g. left, right, up, down, up) against live input.
 *
 * Because the unlock gesture is made of the same swipes the player uses to
 * play the game, the matcher is built so that ordinary play does not trip it:
 *  - the swipe buffer is discarded after [inputGapMillis] without input;
 *  - after a mismatch the current swipe is re-evaluated as the possible start
 *    of a new attempt (so L,L,R still completes a pattern of L,R,...);
 *  - a mismatch only counts as a failed unlock attempt once at least
 *    [minPrefixForFailure] swipes of the pattern had been matched, i.e. when
 *    the user was clearly trying to unlock rather than just playing.
 *
 * Failed attempts trigger a lockout with exponential backoff (capped at
 * [maxLockoutMillis]). When a [store] is supplied the lockout survives restarts.
 *
 * This is a user-owned, user-configured interaction gate for their own vault.
 * It intentionally does not hide, disguise, or evade anything from the device
 * or its owner.
 */
class ActivationSequenceManager(
    private val configuredSequence: List<SwipeDirection>,
    private val maxAttemptsBeforeLockout: Int = 5,
    private val lockoutDurationMillis: Long = 30_000L,
    private val maxLockoutMillis: Long = 60 * 60 * 1000L,
    private val inputGapMillis: Long = 3_000L,
    minPrefixForFailure: Int = 2,
    private val store: LockoutStore? = null
) {
    private val buffer = mutableListOf<SwipeDirection>()
    private var lastSwipeMillis = 0L
    private var failedAttempts = store?.failedAttempts ?: 0
    private var lockedUntilMillis: Long = store?.lockedUntilMillis ?: 0L
    private var failureSeq = 0

    // Never demand a longer matched prefix than the pattern can provide.
    private val failureThreshold =
        minPrefixForFailure.coerceAtMost((configuredSequence.size - 1).coerceAtLeast(1))

    private val _state = MutableStateFlow<ActivationState>(initialState())
    val state: StateFlow<ActivationState> = _state

    sealed class ActivationState {
        data class InProgress(val matchedCount: Int) : ActivationState()
        object Unlocked : ActivationState()
        /** [sequence] changes on every failure so repeated failures are re-emitted by the StateFlow. */
        data class Failed(val sequence: Int = 0) : ActivationState()
        data class LockedOut(val remainingMillis: Long) : ActivationState()
    }

    private fun initialState(): ActivationState {
        val now = System.currentTimeMillis()
        return if (isLockedOut(now)) ActivationState.LockedOut(lockedUntilMillis - now)
        else ActivationState.InProgress(0)
    }

    fun isLockedOut(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis < lockedUntilMillis

    /** Feed one swipe input performed during gameplay. */
    fun onSwipe(direction: SwipeDirection, nowMillis: Long = System.currentTimeMillis()) {
        if (configuredSequence.isEmpty()) return

        if (isLockedOut(nowMillis)) {
            buffer.clear()
            _state.value = ActivationState.LockedOut(lockedUntilMillis - nowMillis)
            return
        }

        if (buffer.isNotEmpty() && nowMillis - lastSwipeMillis > inputGapMillis) buffer.clear()
        lastSwipeMillis = nowMillis

        val matchedBefore = buffer.size // always < configuredSequence.size
        if (direction == configuredSequence[matchedBefore]) {
            buffer.add(direction)
            if (buffer.size == configuredSequence.size) {
                buffer.clear()
                clearFailures()
                _state.value = ActivationState.Unlocked
            } else {
                _state.value = ActivationState.InProgress(buffer.size)
            }
            return
        }

        // Mismatch: this swipe may still be the start of a fresh attempt.
        val attempt = buffer + direction
        buffer.clear()
        for (k in minOf(attempt.size, configuredSequence.size - 1) downTo 1) {
            if (attempt.takeLast(k) == configuredSequence.take(k)) {
                buffer.addAll(configuredSequence.take(k))
                break
            }
        }

        if (matchedBefore >= failureThreshold) {
            registerFailure(nowMillis)
            // Never reveal which specific move was wrong — just that the sequence reset.
            _state.value =
                if (isLockedOut(nowMillis)) ActivationState.LockedOut(lockedUntilMillis - nowMillis)
                else ActivationState.Failed(++failureSeq)
        } else {
            _state.value = ActivationState.InProgress(buffer.size)
        }
    }

    private fun registerFailure(nowMillis: Long) {
        failedAttempts++
        if (failedAttempts >= maxAttemptsBeforeLockout) {
            // Exponential backoff beyond the base lockout window discourages brute forcing.
            val exponent = (failedAttempts - maxAttemptsBeforeLockout).coerceAtMost(20)
            val total = lockoutDurationMillis + (lockoutDurationMillis shl exponent)
            lockedUntilMillis = nowMillis + total.coerceAtMost(maxLockoutMillis)
            buffer.clear()
        }
        persist()
    }

    private fun clearFailures() {
        failedAttempts = 0
        lockedUntilMillis = 0L
        persist()
    }

    private fun persist() {
        store?.failedAttempts = failedAttempts
        store?.lockedUntilMillis = lockedUntilMillis
    }

    fun reset() {
        buffer.clear()
        clearFailures()
        _state.value = ActivationState.InProgress(0)
    }
}
