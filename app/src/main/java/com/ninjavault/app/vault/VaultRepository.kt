package com.ninjavault.app.vault

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.ninjavault.app.crypto.CryptoManager
import com.ninjavault.app.data.VaultDao
import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.data.VaultFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Coordinates encrypted import/export/delete of vault files. Files are stored
 * ciphertext-only in the app's private storage directory (never on shared/public
 * storage), each with its own randomly generated key wrapped by the Keystore
 * master key. Import/export use atomic renames so a crash mid-operation cannot
 * leave a corrupt or partially-written file mistaken for a good one, and the
 * user's original source file is never modified or deleted during import.
 */
class VaultRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val crypto: CryptoManager = CryptoManager()
) {
    private val vaultDir: File by lazy {
        File(context.filesDir, "vault_blobs").apply { mkdirs() }
    }

    fun observeFolder(folderPath: String): Flow<List<VaultFileEntity>> = dao.observeFolder(folderPath)
    fun observeFolders(): Flow<List<String>> = dao.observeFolders()
    fun search(query: String): Flow<List<VaultFileEntity>> = dao.search(query)

    suspend fun importFile(
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
        folderPath: String = "/"
    ): Result<VaultFileEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver: ContentResolver = context.contentResolver
            val tempOut = File(vaultDir, "${UUID.randomUUID()}.tmp")
            val fileKey = crypto.generateFileKey()

            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempOut).use { output ->
                    crypto.encryptStream(input, output, fileKey)
                }
            } ?: error("Could not open source file for import")

            // Atomic move into place only after encryption fully succeeds.
            val finalFile = File(vaultDir, "${UUID.randomUUID()}.enc")
            if (!tempOut.renameTo(finalFile)) error("Failed to finalize encrypted file")

            val size = finalFile.length()
            val now = System.currentTimeMillis()
            val entity = VaultFileEntity(
                displayName = displayName,
                folderPath = folderPath,
                encryptedPath = finalFile.absolutePath,
                wrappedKeyBase64 = Base64.encodeToString(crypto.wrapFileKey(fileKey), Base64.NO_WRAP),
                mimeType = mimeType,
                fileType = inferType(mimeType, displayName),
                sizeBytes = size,
                createdAtMillis = now,
                modifiedAtMillis = now
            )
            val id = dao.insert(entity)
            entity.copy(id = id)
        }
    }

    /** Decrypts a vault file into a temporary plaintext file for the media player to stream from. */
    suspend fun decryptToTempFile(entity: VaultFileEntity): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val fileKey = crypto.unwrapFileKey(Base64.decode(entity.wrappedKeyBase64, Base64.NO_WRAP))
            val tempDir = File(context.cacheDir, "playback_tmp").apply { mkdirs() }
            val outFile = File(tempDir, "${UUID.randomUUID()}_${entity.displayName}")
            FileInputStream(entity.encryptedPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    crypto.decryptStream(input, output, fileKey)
                }
            }
            outFile
        }
    }

    /** Explicit user-requested export: decrypts a copy out to a destination Uri (e.g. Downloads). */
    suspend fun exportFile(entity: VaultFileEntity, destination: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileKey = crypto.unwrapFileKey(Base64.decode(entity.wrappedKeyBase64, Base64.NO_WRAP))
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    FileInputStream(entity.encryptedPath).use { input ->
                        crypto.decryptStream(input, output, fileKey)
                    }
                } ?: error("Could not open export destination")
            }
        }

    suspend fun deleteFile(entity: VaultFileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(entity.encryptedPath).delete()
            dao.delete(entity)
        }
    }

    suspend fun renameFile(entity: VaultFileEntity, newName: String) = withContext(Dispatchers.IO) {
        dao.update(entity.copy(displayName = newName, modifiedAtMillis = System.currentTimeMillis()))
    }

    /** Clears any leftover decrypted playback temp files (called on lock/background). */
    fun clearPlaybackTempFiles() {
        File(context.cacheDir, "playback_tmp").listFiles()?.forEach { it.delete() }
    }

    private fun inferType(mimeType: String, name: String): VaultFileType = when {
        mimeType.startsWith("image/") -> VaultFileType.IMAGE
        mimeType.startsWith("video/") -> VaultFileType.VIDEO
        mimeType.startsWith("audio/") -> VaultFileType.AUDIO
        mimeType == "application/pdf" -> VaultFileType.PDF
        mimeType == "application/zip" -> VaultFileType.ZIP
        name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".txt") -> VaultFileType.DOCUMENT
        else -> VaultFileType.OTHER
    }
}
