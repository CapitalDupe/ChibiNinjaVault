package com.ninjavault.app.ui.vault

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontFamily
import com.ninjavault.app.vault.AppLockController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.data.VaultFileType
import com.ninjavault.app.vault.VaultNames
import com.ninjavault.app.vault.effectiveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Previews a vault file: audio/video play in ExoPlayer, images are decoded and
 * shown, HTML runs in a sandboxed WebView, and other types explain that they can
 * only be exported. Plaintext only ever exists as a temp file in the app's private
 * cache dir while this screen is showing it; it is deleted when the screen leaves
 * composition (or, for images and HTML, as soon as the content is loaded).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MediaPlayerScreen(
    file: VaultFileEntity,
    viewModel: VaultViewModel,
    appLock: AppLockController? = null
) {
    val context = LocalContext.current
    var decryptedFile by remember(file.id) { mutableStateOf<File?>(null) }
    var bitmap by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }
    var htmlText by remember(file.id) { mutableStateOf<String?>(null) }
    var plainText by remember(file.id) { mutableStateOf<String?>(null) }

    // Derived from the stored MIME type first, then the name; a rename to "x.adx" can't break it.
    val effectiveType = file.effectiveType()
    val isHtml = VaultNames.isHtml(file.mimeType, file.displayName)
    val isText = !isHtml && VaultNames.isText(file.mimeType, file.displayName)

    // Explicit, user-requested export of a decrypted copy (for docs, executables, anything without a viewer).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> if (uri != null) viewModel.exportFile(file, uri) }

    val previewable = isHtml || isText ||
        effectiveType == VaultFileType.VIDEO ||
        effectiveType == VaultFileType.AUDIO ||
        effectiveType == VaultFileType.IMAGE

    LaunchedEffect(file.id) {
        if (!previewable) return@LaunchedEffect
        val result = viewModel.prepareForPlayback(file)
        val decrypted = result.getOrNull()
        if (decrypted == null) {
            error = "Couldn't decrypt this file: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            return@LaunchedEffect
        }
        if (isHtml) {
            // Read the page into memory and delete the plaintext temp file straight away.
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    if (decrypted.length() > MAX_HTML_BYTES) null else decrypted.readText()
                }.getOrNull()
            }
            decrypted.delete()
            if (text == null) error = "This HTML file is too large or couldn't be read" else htmlText = text
        } else if (isText) {
            val text = withContext(Dispatchers.IO) { runCatching { readTextCapped(decrypted) }.getOrNull() }
            decrypted.delete()
            if (text == null) error = "This file couldn't be read as text" else plainText = text
        } else if (effectiveType == VaultFileType.IMAGE) {
            val decoded = withContext(Dispatchers.IO) { decodeSampled(decrypted) }
            decrypted.delete() // plaintext no longer needed once decoded
            if (decoded == null) error = "Couldn't display this image" else bitmap = decoded
        } else {
            decryptedFile = decrypted
        }
    }

    DisposableEffect(file.id) {
        onDispose {
            decryptedFile?.delete()
            viewModel.clearPlaybackTempFiles()
        }
    }

    val currentError = error
    val currentBitmap = bitmap
    val currentFile = decryptedFile
    val currentHtml = htmlText
    val currentText = plainText

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !previewable -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "This file can't be previewed inside the vault.\n" +
                        "Export a decrypted copy to open it with another app.",
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        // The system file picker backgrounds the app; don't treat that as leaving the vault.
                        appLock?.suppressNextBackgroundLock()
                        exportLauncher.launch(file.displayName)
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text("Export a copy") }
            }
            currentError != null -> Text(currentError, modifier = Modifier.padding(24.dp))
            currentHtml != null -> AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // Sandboxed: scripts run, but the page can't touch the network,
                        // files, or other apps, and can't navigate away.
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        settings.blockNetworkLoads = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = true
                        }
                        loadDataWithBaseURL(null, currentHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            currentText != null -> SelectionContainer {
                Text(
                    currentText,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
            currentBitmap != null -> Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = file.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            currentFile != null -> {
                val exoPlayer = remember(currentFile) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(currentFile)))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> CircularProgressIndicator()
        }
    }
}

/** Decodes [file] down to at most [maxDim] pixels per side to avoid exhausting memory on large photos. */
private fun decodeSampled(file: File, maxDim: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private const val MAX_HTML_BYTES = 5L * 1024 * 1024
private const val MAX_TEXT_BYTES = 1024 * 1024

/** Reads at most [MAX_TEXT_BYTES] of [file] as UTF-8 and notes when the rest was cut off. */
private fun readTextCapped(file: File): String {
    val buffer = ByteArray(MAX_TEXT_BYTES)
    var read = 0
    file.inputStream().buffered().use { stream ->
        while (read < MAX_TEXT_BYTES) {
            val n = stream.read(buffer, read, MAX_TEXT_BYTES - read)
            if (n < 0) break
            read += n
        }
    }
    val text = String(buffer, 0, read, Charsets.UTF_8)
    return if (file.length() > MAX_TEXT_BYTES) text + "\n\n\u2026 truncated at 1 MB. Export a copy to see the whole file." else text
}
