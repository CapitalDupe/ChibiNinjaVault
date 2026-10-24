package com.ninjavault.app.vault

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-wide lock signal. The activity reports when it stops/starts and the
 * navigation graph collects [lockEvents] to send the user back to the game
 * screen (and wipe decrypted playback files).
 *
 * - If "lock when app goes to background" is on, the vault locks as soon as the
 *   activity stops.
 * - Otherwise it locks on return if the app was away for at least the
 *   auto-lock timeout.
 * - Screens that deliberately hand control to the system (document picker for
 *   import/export) call [suppressNextBackgroundLock] first so the user is not
 *   thrown out of the vault the moment the picker closes.
 */
class AppLockController {

    private val _lockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockEvents: SharedFlow<Unit> = _lockEvents

    private var suppressedAtMillis = NOT_SUPPRESSED
    private var stoppedAtMillis = 0L

    fun suppressNextBackgroundLock(nowMillis: Long = SystemClock.elapsedRealtime()) {
        suppressedAtMillis = nowMillis
    }

    private fun isSuppressed(nowMillis: Long): Boolean =
        suppressedAtMillis != NOT_SUPPRESSED && nowMillis - suppressedAtMillis < SUPPRESS_WINDOW_MILLIS

    fun onActivityStopped(nowMillis: Long, lockOnBackground: Boolean) {
        stoppedAtMillis = nowMillis
        if (isSuppressed(nowMillis)) return
        if (lockOnBackground) _lockEvents.tryEmit(Unit)
    }

    fun onActivityStarted(nowMillis: Long, autoLockTimeoutSeconds: Int) {
        val wasSuppressed = isSuppressed(nowMillis)
        suppressedAtMillis = NOT_SUPPRESSED
        val stoppedAt = stoppedAtMillis
        stoppedAtMillis = 0L
        if (wasSuppressed || stoppedAt == 0L) return
        if (nowMillis - stoppedAt >= autoLockTimeoutSeconds * 1000L) _lockEvents.tryEmit(Unit)
    }

    private companion object {
        const val NOT_SUPPRESSED = Long.MIN_VALUE
        const val SUPPRESS_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
