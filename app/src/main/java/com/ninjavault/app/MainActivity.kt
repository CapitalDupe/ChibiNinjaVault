package com.ninjavault.app

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.ninjavault.app.data.SecurePrefs
import com.ninjavault.app.ui.nav.NinjaRunNavGraph
import com.ninjavault.app.ui.theme.NinjaRunTheme
import com.ninjavault.app.vault.AppLockController

/**
 * Uses FragmentActivity (not plain ComponentActivity) so androidx.biometric's
 * BiometricPrompt has the FragmentManager it needs — Compose's setContent works
 * the same way on either, since FragmentActivity extends ComponentActivity.
 */
class MainActivity : FragmentActivity() {

    private lateinit var prefs: SecurePrefs
    private val appLock = AppLockController()
    private var darkTheme by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Deliberately discard saved instance state. Restoring it after the process was
        // killed would put the navigation back inside the vault without any unlock;
        // starting from the game screen means every fresh process must be unlocked.
        // (Normal rotation doesn't recreate the activity: see android:configChanges.)
        super.onCreate(null)
        enableEdgeToEdge()
        prefs = SecurePrefs(this)
        darkTheme = prefs.darkTheme

        // Prevents screenshots/screen-recording and hides content in the
        // recent-apps thumbnail while the vault (or its unlock flow) is on
        // screen — the whole activity, since the vault can be reached from
        // any of its screens.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            NinjaRunTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NinjaRunNavGraph(
                        activity = this@MainActivity,
                        prefs = prefs,
                        appLock = appLock,
                        onDarkThemeChange = { darkTheme = it }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            appLock.onActivityStopped(SystemClock.elapsedRealtime(), prefs.lockOnBackground)
        }
    }

    override fun onStart() {
        super.onStart()
        if (::prefs.isInitialized) {
            appLock.onActivityStarted(SystemClock.elapsedRealtime(), prefs.autoLockTimeoutSeconds)
        }
    }
}
