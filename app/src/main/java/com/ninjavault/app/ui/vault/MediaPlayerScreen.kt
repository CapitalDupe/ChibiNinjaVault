package com.ninjavault.app.ui.vault

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ninjavault.app.data.VaultFileEntity
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays a decrypted media file. The plaintext only ever exists as a temp file
 * in the app's private cache dir for the duration of playback and is deleted
 * when this screen leaves composition (see DisposableEffect below) — no
 * permanent plaintext copy is created.
 */
@Composable
fun MediaPlayerScreen(
    file: VaultFileEntity,
    viewModel: VaultViewModel
) {
    val context = LocalContext.current
    var decryptedFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file.id) {
        viewModel.prepareForPlayback(file)
            .onSuccess { decryptedFile = it }
            .onFailure { error = "Couldn't decrypt this file: ${it.message ?: "unknown error"}" }
    }

    DisposableEffect(Unit) {
        onDispose {
            decryptedFile?.delete()
            viewModel.clearPlaybackTempFiles()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!)
            decryptedFile == null -> CircularProgressIndicator()
            else -> {
                val exoPlayer = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(decryptedFile)))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(Unit) {
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
        }
    }
}
