package com.ninjavault.app.ui.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ninjavault.app.data.VaultDatabase
import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.vault.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class VaultViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val repository = VaultRepository(
        context = appContext,
        dao = VaultDatabase.getInstance(appContext).vaultDao()
    )

    private val searchQuery = MutableStateFlow("")
    private val currentFolder = MutableStateFlow("/")

    val currentFiles = searchQuery.flatMapLatest { q ->
        if (q.isBlank()) currentFolder.flatMapLatest { repository.observeFolder(it) }
        else repository.search(q)
    }

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setFolder(path: String) { currentFolder.value = path }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val resolver = appContext.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var displayName = uri.lastPathSegment ?: "imported_file"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
            }
            repository.importFile(sourceUri = uri, displayName = displayName, mimeType = mimeType)
        }
    }

    fun deleteFile(file: VaultFileEntity) {
        viewModelScope.launch { repository.deleteFile(file) }
    }

    fun renameFile(file: VaultFileEntity, newName: String) {
        viewModelScope.launch { repository.renameFile(file, newName) }
    }

    fun exportFile(file: VaultFileEntity, destination: Uri) {
        viewModelScope.launch { repository.exportFile(file, destination) }
    }

    suspend fun prepareForPlayback(file: VaultFileEntity) = repository.decryptToTempFile(file)

    fun clearPlaybackTempFiles() = repository.clearPlaybackTempFiles()
}
