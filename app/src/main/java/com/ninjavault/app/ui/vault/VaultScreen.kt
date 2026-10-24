package com.ninjavault.app.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.ninjavault.app.vault.AppLockController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    appLock: AppLockController,
    onOpenFile: (VaultFileEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit
) {
    val files by viewModel.currentFiles.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<VaultFileEntity?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VaultFileEntity?>(null) }
    var exportTarget by remember { mutableStateOf<VaultFileEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Drop selections for files that no longer exist (deleted, or filtered out by search).
    LaunchedEffect(files) {
        val present = files.map { it.id }.toSet()
        if (selected.any { it !in present }) selected = selected.filter { it in present }.toSet()
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> uris.forEach { uri -> viewModel.importFile(uri) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) viewModel.exportFile(target, uri)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selected.isEmpty()) {
                TopAppBar(
                    title = { Text("Your Vault") },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock vault")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { pendingBulkDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // The system picker backgrounds the app; don't treat that as leaving the vault.
                appLock.suppressNextBackgroundLock()
                importLauncher.launch(arrayOf("*/*"))
            }) { Icon(Icons.Default.Add, contentDescription = "Import files") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setSearchQuery(it) },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )

            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Nothing here yet. Tap + to import files."
                        else "No files match your search."
                    )
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
                        onRename = { renameTarget = file },
                        onExport = {
                            exportTarget = file
                            appLock.suppressNextBackgroundLock()
                            exportLauncher.launch(file.displayName)
                        },
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
                    viewModel.deleteFile(file)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    if (pendingBulkDelete) {
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            title = { Text("Delete ${selected.size} files?") },
            text = { Text("This permanently deletes the selected encrypted files. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFiles(files.filter { it.id in selected })
                    selected = emptySet()
                    pendingBulkDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingBulkDelete = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { file ->
        var newName by remember(file.id) { mutableStateOf(file.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        viewModel.renameFile(file, newName)
                        renameTarget = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultFileRow(
    file: VaultFileEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(file.displayName) },
        supportingContent = { Text("${file.fileType.name} · ${formatSize(file.sizeBytes)}") },
        leadingContent = { Icon(iconFor(file.fileType), contentDescription = null) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "File actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export a copy") },
                        onClick = { menuOpen = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDeleteRequest() }
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else ListItemDefaults.containerColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
