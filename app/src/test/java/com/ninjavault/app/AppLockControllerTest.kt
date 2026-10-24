package com.ninjavault.app.vault

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockControllerTest {

    /** Collects lock events into [events] for the duration of the test. */
    private fun TestScope.collectLockEvents(
        controller: AppLockController,
        events: MutableList<Unit>
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.lockEvents.collect { events.add(it) }
        }
    }

    @Test
    fun `locks immediately on background when lock-on-background is enabled`() = runTest {
        val controller = AppLockController()
        val events = mutableListOf<Unit>()
        collectLockEvents(controller, events)

        controller.onActivityStopped(nowMillis = 1_000L, lockOnBackground = true)
        assertEquals(1, events.size)
    }

    @Test
    fun `without lock-on-background it locks on return only after the timeout`() = runTest {
        val controller = AppLockController()
        val events = mutableListOf<Unit>()
        collectLockEvents(controller, events)

        controller.onActivityStopped(nowMillis = 1_000L, lockOnBackground = false)
        controller.onActivityStarted(nowMillis = 11_000L, autoLockTimeoutSeconds = 60) // 10s away
        assertEquals(0, events.size)

        controller.onActivityStopped(nowMillis = 20_000L, lockOnBackground = false)
        controller.onActivityStarted(nowMillis = 100_000L, autoLockTimeoutSeconds = 60) // 80s away
        assertEquals(1, events.size)
    }

    @Test
    fun `suppressed background trip such as the document picker does not lock`() = runTest {
        val controller = AppLockController()
        val events = mutableListOf<Unit>()
        collectLockEvents(controller, events)

        controller.suppressNextBackgroundLock(nowMillis = 500L)
        controller.onActivityStopped(nowMillis = 1_000L, lockOnBackground = true)
        controller.onActivityStarted(nowMillis = 30_000L, autoLockTimeoutSeconds = 10)
        assertEquals(0, events.size)

        // The suppression is one-shot: the next real background trip locks again.
        controller.onActivityStopped(nowMillis = 60_000L, lockOnBackground = true)
        assertEquals(1, events.size)
    }
}
