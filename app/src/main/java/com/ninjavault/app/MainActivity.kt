package com.ninjavault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.ninjavault.app.data.SecurePrefs
import com.ninjavault.app.ui.nav.NinjaRunNavGraph
import com.ninjavault.app.ui.theme.NinjaRunTheme

/**
 * Uses FragmentActivity (not plain ComponentActivity) so androidx.biometric's
 * BiometricPrompt has the FragmentManager it needs — Compose's setContent works
 * the same way on either, since FragmentActivity extends ComponentActivity.
 */
class MainActivity : FragmentActivity() {

    private lateinit var prefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = SecurePrefs(this)

        // Prevents screenshots/screen-recording and hides content in the
        // recent-apps thumbnail while the vault (or its unlock flow) is on
        // screen — the whole activity, since the vault can be reached from
        // any of its screens.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            NinjaRunTheme(darkTheme = prefs.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NinjaRunNavGraph(activity = this)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Auto-lock-on-background is enforced by returning to the game/unlock
        // screen; a full production build would drive this from a shared
        // lock-state ViewModel observed at the nav-graph root.
    }
}
