package com.ninjavault.app.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.data.VaultFileType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onOpenFile: (VaultFileEntity) -> Unit,
    onLock: () -> Unit
) {
    val files by viewModel.currentFiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<VaultFileEntity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri -> scope.launch { viewModel.importFile(uri) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Vault") },
                actions = {
                    IconButton(onClick = onLock) { Icon(Icons.Default.Lock, contentDescription = "Lock vault") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                importLauncher.launch(arrayOf("*/*"))
            }) { Icon(Icons.Default.Add, contentDescription = "Import files") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setSearchQuery(it) },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )

            if (selected.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${selected.size} selected")
                    TextButton(onClick = {
                        scope.launch {
                            files.filter { it.id in selected }.forEach { viewModel.deleteFile(it) }
                            selected = emptySet()
                        }
                    }) { Text("Delete") }
                }
            }

            LazyColumn {
                items(files, key = { it.id }) { file ->
                    VaultFileRow(
                        file = file,
                        isSelected = file.id in selected,
                        onClick = {
                            if (selected.isEmpty()) onOpenFile(file)
                            else selected = if (file.id in selected) selected - file.id else selected + file.id
                        },
                        onLongClick = { selected = selected + file.id },
                        onDeleteRequest = { pendingDelete = file }
                    )
                }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${file.displayName}\"?") },
            text = { Text("This permanently deletes the encrypted file. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.deleteFile(file) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultFileRow(
    file: VaultFileEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    ListItem(
        headlineContent = { Text(file.displayName) },
        supportingContent = { Text("${file.fileType.name} · ${formatSize(file.sizeBytes)}") },
        leadingContent = { Icon(iconFor(file.fileType), contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onDeleteRequest) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

private fun iconFor(type: VaultFileType) = when (type) {
    VaultFileType.IMAGE -> Icons.Default.Image
    VaultFileType.VIDEO -> Icons.Default.Movie
    VaultFileType.AUDIO -> Icons.Default.AudioFile
    VaultFileType.PDF -> Icons.Default.PictureAsPdf
    VaultFileType.ZIP -> Icons.Default.FolderZip
    VaultFileType.DOCUMENT -> Icons.Default.Description
    VaultFileType.OTHER -> Icons.Default.InsertDriveFile
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}
