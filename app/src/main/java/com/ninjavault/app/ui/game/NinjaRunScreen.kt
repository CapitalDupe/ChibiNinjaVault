package com.ninjavault.app.ui.game

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import com.ninjavault.app.vault.ActivationSequenceManager
import com.ninjavault.app.vault.SwipeDirection

/**
 * Three.js endless runner hosted in a local WebView. The game ships with the APK and works
 * offline. Every gameplay swipe is forwarded to ActivationSequenceManager so the configured
 * vault activation pattern remains the source of truth.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NinjaRunScreen(
    activationManager: ActivationSequenceManager,
    hapticsEnabled: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activationState by activationManager.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(activationState) {
        if (activationState is ActivationSequenceManager.ActivationState.Unlocked) {
            onUnlocked()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.rgb(7, 12, 25))
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                // The only loaded document is our bundled, trusted game asset.
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = !request.url.toString().startsWith("file:///android_asset/ninja-runner/")
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onSwipe(direction: String) {
                        val swipe = when (direction.lowercase()) {
                            "left" -> SwipeDirection.LEFT
                            "right" -> SwipeDirection.RIGHT
                            "up" -> SwipeDirection.UP
                            "down" -> SwipeDirection.DOWN
                            else -> return
                        }
                        mainHandler.post {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            activationManager.onSwipe(swipe)
                        }
                    }
                }, "NinjaBridge")
                loadUrl("file:///android_asset/ninja-runner/index.html")
            }
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("NinjaBridge")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    )
}
