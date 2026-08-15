package com.ninjavault.app.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

/**
 * Validates the user-configured unlock sequence (a pattern of swipes performed
 * during a Ninja Run — e.g. left, right, up, down, up) against live input.
 *
 * This is a user-owned, user-configured interaction gate for their own vault.
 * It intentionally does not hide, disguise, or evade anything from the device
 * or its owner — it is simply a custom "lock combination" expressed as game
 * moves instead of a PIN pad, with the same lockout protections a PIN would have.
 */
class ActivationSequenceManager(
    private val configuredSequence: List<SwipeDirection>,
    private val maxAttemptsBeforeLockout: Int = 5,
    private val lockoutDurationMillis: Long = 30_000L
) {
    private val buffer = mutableListOf<SwipeDirection>()
    private var failedAttempts = 0
    private var lockedUntilMillis: Long = 0L

    private val _state = MutableStateFlow<ActivationState>(ActivationState.InProgress(0))
    val state: StateFlow<ActivationState> = _state

    sealed class ActivationState {
        data class InProgress(val matchedCount: Int) : ActivationState()
        object Unlocked : ActivationState()
        data class Failed(val resetTo: Int = 0) : ActivationState()
        data class LockedOut(val remainingMillis: Long) : ActivationState()
    }

    fun isLockedOut(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis < lockedUntilMillis

    /** Feed one swipe input performed during gameplay. */
    fun onSwipe(direction: SwipeDirection, nowMillis: Long = System.currentTimeMillis()) {
        if (isLockedOut(nowMillis)) {
            _state.value = ActivationState.LockedOut(lockedUntilMillis - nowMillis)
            return
        }

        buffer.add(direction)

        val prefixMatches = buffer.indices.all { i -> buffer[i] == configuredSequence.getOrNull(i) }

        if (!prefixMatches) {
            buffer.clear()
            registerFailure(nowMillis)
            // Never reveal which specific move was wrong — just that the sequence reset.
            _state.value = ActivationState.Failed()
            return
        }

        if (buffer.size == configuredSequence.size) {
            buffer.clear()
            failedAttempts = 0
            _state.value = ActivationState.Unlocked
            return
        }

        _state.value = ActivationState.InProgress(buffer.size)
    }

    private fun registerFailure(nowMillis: Long) {
        failedAttempts++
        if (failedAttempts >= maxAttemptsBeforeLockout) {
            // Exponential backoff beyond the base lockout window discourages brute forcing.
            val extra = lockoutDurationMillis * 2.0.pow(failedAttempts - maxAttemptsBeforeLockout).toLong()
            lockedUntilMillis = nowMillis + lockoutDurationMillis + extra.toLong()
        }
    }

    fun reset() {
        buffer.clear()
        failedAttempts = 0
        lockedUntilMillis = 0L
        _state.value = ActivationState.InProgress(0)
    }
}
