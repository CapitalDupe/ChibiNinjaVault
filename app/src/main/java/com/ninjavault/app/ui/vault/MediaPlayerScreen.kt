package com.ninjavault.app.ui.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Previews a vault file: audio/video play in ExoPlayer, images are decoded and
 * shown, and other types explain that they can only be exported. Plaintext only
 * ever exists as a temp file in the app's private cache dir while this screen is
 * showing it; it is deleted when the screen leaves composition (or, for images,
 * as soon as the bitmap is decoded).
 */
@Composable
fun MediaPlayerScreen(
    file: VaultFileEntity,
    viewModel: VaultViewModel
) {
    val context = LocalContext.current
    var decryptedFile by remember(file.id) { mutableStateOf<File?>(null) }
    var bitmap by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }

    val previewable = file.fileType == VaultFileType.VIDEO ||
        file.fileType == VaultFileType.AUDIO ||
        file.fileType == VaultFileType.IMAGE

    LaunchedEffect(file.id) {
        if (!previewable) return@LaunchedEffect
        val result = viewModel.prepareForPlayback(file)
        val decrypted = result.getOrNull()
        if (decrypted == null) {
            error = "Couldn't decrypt this file: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            return@LaunchedEffect
        }
        if (file.fileType == VaultFileType.IMAGE) {
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !previewable -> Text(
                "Preview isn't available for ${file.fileType.name.lowercase()} files.\n" +
                    "Go back and use \"Export a copy\" from the file's menu.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
            currentError != null -> Text(currentError, modifier = Modifier.padding(24.dp))
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
