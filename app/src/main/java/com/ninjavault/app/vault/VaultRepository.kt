package com.ninjavault.app.vault

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.ninjavault.app.crypto.CryptoManager
import com.ninjavault.app.crypto.KeyExport
import com.ninjavault.app.crypto.KeyFileException
import com.ninjavault.app.data.VaultDao
import com.ninjavault.app.data.VaultFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import javax.crypto.AEADBadTagException
import java.util.UUID

/**
 * Coordinates encrypted import/export/delete of vault files. Files are stored
 * ciphertext-only in the app's private storage directory (never on shared/public
 * storage), each with its own randomly generated key wrapped by the Keystore
 * master key. Import uses an atomic rename so a crash mid-operation cannot
 * leave a partially-written file mistaken for a good one, and the user's
 * original source file is never modified or deleted during import.
 */
class VaultRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val crypto: CryptoManager = CryptoManager()
) {
    private val vaultDir: File by lazy {
        File(context.filesDir, "vault_blobs").apply { mkdirs() }
    }

    private val playbackDir: File
        get() = File(context.cacheDir, "playback_tmp")

    fun observeFolder(folderPath: String): Flow<List<VaultFileEntity>> = dao.observeFolder(folderPath)
    fun observeFolders(): Flow<List<String>> = dao.observeFolders()
    fun search(query: String): Flow<List<VaultFileEntity>> = dao.search(escapeLike(query))

    suspend fun getFile(id: Long): VaultFileEntity? = dao.getById(id)

    suspend fun importFile(
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
        folderPath: String = "/"
    ): Result<VaultFileEntity> = withContext(Dispatchers.IO) {
        val tempOut = File(vaultDir, "${UUID.randomUUID()}.tmp")
        val finalFile = File(vaultDir, "${UUID.randomUUID()}.adx")
        runCatching {
            val resolver: ContentResolver = context.contentResolver
            val fileKey = crypto.generateFileKey()

            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempOut).use { output ->
                    crypto.encryptStream(input, output, fileKey)
                }
            } ?: error("Could not open source file for import")

            // Atomic move into place only after encryption fully succeeds.
            if (!tempOut.renameTo(finalFile)) error("Failed to finalize encrypted file")

            // Stored size is the plaintext size: ciphertext minus IV header and GCM tag.
            val overhead = CryptoManager.GCM_IV_LENGTH_BYTES + CryptoManager.GCM_TAG_LENGTH_BITS / 8
            val size = (finalFile.length() - overhead).coerceAtLeast(0L)
            // Pickers often report application/octet-stream; infer from the name so the type/preview still works.
            val resolvedMime = VaultNames.resolveMime(mimeType, displayName)
            val now = System.currentTimeMillis()
            val entity = VaultFileEntity(
                displayName = displayName,
                folderPath = folderPath,
                encryptedPath = finalFile.absolutePath,
                wrappedKeyBase64 = Base64.encodeToString(crypto.wrapFileKey(fileKey), Base64.NO_WRAP),
                mimeType = resolvedMime,
                fileType = VaultNames.typeFor(resolvedMime, displayName),
                sizeBytes = size,
                createdAtMillis = now,
                modifiedAtMillis = now
            )
            val id = dao.insert(entity)
            entity.copy(id = id)
        }.onFailure {
            // Don't leave orphaned ciphertext behind when any step fails.
            tempOut.delete()
            finalFile.delete()
        }
    }

    /**
     * Best-effort delete of the user's original (plaintext) file after a successful import.
     * Fails if the provider doesn't allow deletion; callers should tell the user.
     */
    suspend fun deleteSourceFile(sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deleted = if (DocumentsContract.isDocumentUri(context, sourceUri)) {
                DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
            } else {
                context.contentResolver.delete(sourceUri, null, null) > 0
            }
            if (!deleted) error("Provider refused to delete the original")
        }
    }

    /** Copies the still-encrypted blob (IV + ciphertext + tag) out. Unreadable without its key. */
    suspend fun exportEncryptedBlob(entity: VaultFileEntity, destination: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    FileInputStream(entity.encryptedPath).use { it.copyTo(output) }
                } ?: error("Could not open export destination")
            }
        }

    /**
     * Writes this file's data key to [destination], protected by [password] (see [KeyExport]).
     * The raw key never leaves memory unencrypted; [password] is wiped afterwards.
     */
    suspend fun exportKey(entity: VaultFileEntity, destination: Uri, password: CharArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var fileKey: ByteArray? = null
            val result = runCatching {
                fileKey = crypto.unwrapFileKey(Base64.decode(entity.wrappedKeyBase64, Base64.NO_WRAP))
                val text = KeyExport.seal(fileKey!!, password, entity.displayName, entity.mimeType)
                context.contentResolver.openOutputStream(destination)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open export destination")
            }
            fileKey?.fill(0)
            password.fill('\u0000')
            result
        }

    /**
     * Imports an encrypted blob together with its exported key file. The key is checked against
     * the blob (full GCM authentication) before anything is stored, then re-wrapped with this
     * device's Keystore master key like any other vault file.
     */
    suspend fun importEncrypted(
        blobUri: Uri,
        keyUri: Uri,
        password: CharArray,
        folderPath: String = "/"
    ): Result<VaultFileEntity> = withContext(Dispatchers.IO) {
        val tempOut = File(vaultDir, "${UUID.randomUUID()}.tmp")
        val finalFile = File(vaultDir, "${UUID.randomUUID()}.adx")
        var fileKey: ByteArray? = null
        val result = runCatching {
            val resolver = context.contentResolver
            val keyText = resolver.openInputStream(keyUri)?.use { stream ->
                val limit = 16 * 1024
                val buf = ByteArray(limit + 1)
                var n = 0
                while (n < buf.size) {
                    val r = stream.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                if (n > limit) throw KeyFileException("That doesn't look like a valid key file.")
                String(buf, 0, n, Charsets.UTF_8)
            } ?: error("Could not open the key file")

            val opened = KeyExport.open(keyText, password)
            fileKey = opened.fileKey

            resolver.openInputStream(blobUri)?.use { input ->
                FileOutputStream(tempOut).use { input.copyTo(it) }
            } ?: error("Could not open the encrypted file")

            try {
                FileInputStream(tempOut).use { crypto.decryptStream(it, DiscardOutputStream, opened.fileKey) }
            } catch (e: AEADBadTagException) {
                throw KeyFileException("This key doesn't match that encrypted file, or the file is damaged.")
            }

            if (!tempOut.renameTo(finalFile)) error("Failed to finalize encrypted file")

            val overhead = CryptoManager.GCM_IV_LENGTH_BYTES + CryptoManager.GCM_TAG_LENGTH_BITS / 8
            val mime = VaultNames.resolveMime(opened.mimeType, opened.displayName)
            val now = System.currentTimeMillis()
            val entity = VaultFileEntity(
                displayName = opened.displayName,
                folderPath = folderPath,
                encryptedPath = finalFile.absolutePath,
                wrappedKeyBase64 = Base64.encodeToString(crypto.wrapFileKey(opened.fileKey), Base64.NO_WRAP),
                mimeType = mime,
                fileType = VaultNames.typeFor(mime, opened.displayName),
                sizeBytes = (finalFile.length() - overhead).coerceAtLeast(0L),
                createdAtMillis = now,
                modifiedAtMillis = now
            )
            entity.copy(id = dao.insert(entity))
        }
        result.onFailure {
            tempOut.delete()
            finalFile.delete()
        }
        fileKey?.fill(0)
        password.fill('\u0000')
        result
    }

    /** Decrypts a vault file into a temporary plaintext file for the media player to stream from. */
    suspend fun decryptToTempFile(entity: VaultFileEntity): Result<File> = withContext(Dispatchers.IO) {
        val tempDir = playbackDir.apply { mkdirs() }
        // Only a sanitised extension is reused: display names are user/provider supplied
        // and must never influence the path (e.g. contain '/'). Never ".adx": see VaultNames.
        val ext = VaultNames.playbackExtension(entity.mimeType, entity.displayName)
        val outFile = File(tempDir, if (ext.isEmpty()) UUID.randomUUID().toString() else "${UUID.randomUUID()}.$ext")
        runCatching {
            val fileKey = crypto.unwrapFileKey(Base64.decode(entity.wrappedKeyBase64, Base64.NO_WRAP))
            FileInputStream(entity.encryptedPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    crypto.decryptStream(input, output, fileKey)
                }
            }
            outFile
        }.onFailure { outFile.delete() }
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
            val blob = File(entity.encryptedPath)
            // Keep the database row if the blob can't be removed, so nothing is orphaned silently.
            if (blob.exists() && !blob.delete()) error("Could not delete the encrypted file")
            dao.delete(entity)
        }
    }

    suspend fun renameFile(entity: VaultFileEntity, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                dao.update(entity.copy(displayName = newName, modifiedAtMillis = System.currentTimeMillis()))
            }
        }

    /**
     * Irreversible reset: removes every encrypted blob and database row, then destroys
     * the Keystore master key so any leftover ciphertext can never be decrypted.
     */
    suspend fun wipeAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            clearPlaybackTempFiles()
            vaultDir.listFiles()?.forEach { it.delete() }
            dao.deleteAll()
            crypto.destroyMasterKey()
        }
    }

    /** Clears any leftover decrypted playback temp files (called on lock/background). */
    fun clearPlaybackTempFiles() {
        playbackDir.listFiles()?.forEach { it.delete() }
    }

    /** Startup housekeeping: plaintext left by a crash and half-written imports. */
    fun cleanupStaleFiles() {
        clearPlaybackTempFiles()
        vaultDir.listFiles { f -> f.extension == "tmp" }?.forEach { it.delete() }
    }

    private fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

}

/** Sink used to verify a key against a blob (full GCM check) without storing any plaintext. */
private object DiscardOutputStream : OutputStream() {
    override fun write(b: Int) {}
    override fun write(b: ByteArray, off: Int, len: Int) {}
}
