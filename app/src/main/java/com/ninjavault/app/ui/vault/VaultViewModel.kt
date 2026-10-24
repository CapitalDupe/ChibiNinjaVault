package com.ninjavault.app.ui.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ninjavault.app.data.VaultDatabase
import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VaultViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val repository = VaultRepository(
        context = appContext,
        dao = VaultDatabase.getInstance(appContext).vaultDao()
    )

    private val searchQuery = MutableStateFlow("")
    private val currentFolder = MutableStateFlow("/")

    // Imports run one at a time: keeps memory bounded and avoids concurrent key setup.
    private val importMutex = Mutex()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** One-off user-visible results (errors and confirmations) for a snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentFiles = searchQuery.flatMapLatest { q ->
        if (q.isBlank()) currentFolder.flatMapLatest { repository.observeFolder(it) }
        else repository.search(q)
    }

    init {
        // Remove plaintext/partial files a crash or kill may have left behind.
        viewModelScope.launch(Dispatchers.IO) { repository.cleanupStaleFiles() }
    }

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setFolder(path: String) { currentFolder.value = path }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            importMutex.withLock {
                val (displayName, mimeType) = withContext(Dispatchers.IO) { queryMetadata(uri) }
                repository.importFile(sourceUri = uri, displayName = displayName, mimeType = mimeType)
                    .onFailure { report("Couldn't import \"$displayName\": ${it.message ?: "unknown error"}") }
            }
        }
    }

    private fun queryMetadata(uri: Uri): Pair<String, String> {
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var displayName = uri.lastPathSegment ?: "imported_file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIndex) ?: displayName
            }
        }
        return displayName to mimeType
    }

    fun deleteFile(file: VaultFileEntity) {
        viewModelScope.launch {
            repository.deleteFile(file)
                .onFailure { report("Couldn't delete \"${file.displayName}\": ${it.message ?: "unknown error"}") }
        }
    }

    fun deleteFiles(files: List<VaultFileEntity>) {
        viewModelScope.launch {
            files.forEach { file ->
                repository.deleteFile(file)
                    .onFailure { report("Couldn't delete \"${file.displayName}\": ${it.message ?: "unknown error"}") }
            }
        }
    }

    fun renameFile(file: VaultFileEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            report("Name can't be empty")
            return
        }
        viewModelScope.launch {
            repository.renameFile(file, trimmed)
                .onFailure { report("Couldn't rename: ${it.message ?: "unknown error"}") }
        }
    }

    fun exportFile(file: VaultFileEntity, destination: Uri) {
        viewModelScope.launch {
            repository.exportFile(file, destination)
                .onSuccess { report("Exported \"${file.displayName}\"") }
                .onFailure { report("Couldn't export \"${file.displayName}\": ${it.message ?: "unknown error"}") }
        }
    }

    /** Destroys every file, database row and the vault's Keystore key. [onDone] runs on the main thread. */
    fun resetVault(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(repository.wipeAll()) }
    }

    suspend fun getFile(id: Long): VaultFileEntity? = repository.getFile(id)

    suspend fun prepareForPlayback(file: VaultFileEntity) = repository.decryptToTempFile(file)

    fun clearPlaybackTempFiles() = repository.clearPlaybackTempFiles()

    private fun report(message: String) {
        _messages.tryEmit(message)
    }
}
