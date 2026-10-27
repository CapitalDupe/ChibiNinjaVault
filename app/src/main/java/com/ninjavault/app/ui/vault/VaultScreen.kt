package com.ninjavault.app.ui.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.data.VaultFileType
import com.ninjavault.app.vault.AppLockController
import com.ninjavault.app.vault.VaultNames
import com.ninjavault.app.vault.effectiveType
import com.ninjavault.app.vault.listName

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
    var deleteOriginal by rememberSaveable { mutableStateOf(false) }
    var encExportTarget by remember { mutableStateOf<VaultFileEntity?>(null) }
    var keyExportTarget by remember { mutableStateOf<VaultFileEntity?>(null) }     // asking for password
    var keyExportReady by remember { mutableStateOf<Pair<VaultFileEntity, CharArray>?>(null) } // choosing destination
    var showImportEncIntro by remember { mutableStateOf(false) }
    var importBlobUri by remember { mutableStateOf<Uri?>(null) }
    var importKeyUri by remember { mutableStateOf<Uri?>(null) }

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
        OpenWritableDocuments()
    ) { uris -> uris.forEach { uri -> viewModel.importFile(uri, deleteOriginal) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) viewModel.exportFile(target, uri)
    }

    val encExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = encExportTarget
        encExportTarget = null
        if (uri != null && target != null) viewModel.exportEncryptedCopy(target, uri)
    }

    val keyExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val ready = keyExportReady
        keyExportReady = null
        if (uri != null && ready != null) viewModel.exportKey(ready.first, uri, ready.second)
        else ready?.second?.fill('\u0000')
    }

    // Import is two pickers in a row (encrypted file, then its key file) followed by a password prompt.
    val importKeyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importKeyUri = uri else importBlobUri = null }

    val importBlobLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importBlobUri = uri
            appLock.suppressNextBackgroundLock()
            importKeyLauncher.launch(arrayOf("*/*"))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selected.isEmpty()) {
                TopAppBar(
                    title = { Text("Your Vault") },
                    actions = {
                        IconButton(onClick = { showImportEncIntro = true }) {
                            Icon(Icons.Default.VpnKey, contentDescription = "Import encrypted file and key")
                        }
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Delete original after import",
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = deleteOriginal, onCheckedChange = { deleteOriginal = it })
            }

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
                        onExportEncrypted = {
                            encExportTarget = file
                            appLock.suppressNextBackgroundLock()
                            encExportLauncher.launch(file.listName())
                        },
                        onExportKey = { keyExportTarget = file },
                        onDeleteRequest = { pendingDelete = file }
                    )
                }
            }
        }
    }

    keyExportTarget?.let { file ->
        PasswordDialog(
            title = "Export key",
            message = "The key file is locked with this password. Without the password it is useless, " +
                "and the password can't be recovered.",
            confirmPassword = true,
            confirmLabel = "Choose location",
            onDismiss = { keyExportTarget = null },
            onSubmit = { password ->
                keyExportTarget = null
                keyExportReady = file to password
                appLock.suppressNextBackgroundLock()
                keyExportLauncher.launch(VaultNames.keyFileName(file.displayName))
            }
        )
    }

    if (showImportEncIntro) {
        AlertDialog(
            onDismissRequest = { showImportEncIntro = false },
            title = { Text("Import encrypted file") },
            text = {
                Text(
                    "Choose the encrypted .adx file first, then its key file (.adxkey). " +
                        "You'll need the password that was set when the key was exported."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportEncIntro = false
                    importBlobUri = null
                    importKeyUri = null
                    appLock.suppressNextBackgroundLock()
                    importBlobLauncher.launch(arrayOf("*/*"))
                }) { Text("Choose files") }
            },
            dismissButton = { TextButton(onClick = { showImportEncIntro = false }) { Text("Cancel") } }
        )
    }

    if (importBlobUri != null && importKeyUri != null) {
        PasswordDialog(
            title = "Key password",
            message = "Enter the password for this key file.",
            confirmPassword = false,
            confirmLabel = "Import",
            onDismiss = { importBlobUri = null; importKeyUri = null },
            onSubmit = { password ->
                val blob = importBlobUri
                val key = importKeyUri
                importBlobUri = null
                importKeyUri = null
                if (blob != null && key != null) viewModel.importEncrypted(blob, key, password)
                else password.fill('\u0000')
            }
        )
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${file.listName()}\"?") },
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
                    label = { Text("Name (shown with .adx)") }
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
    onExportEncrypted: () -> Unit,
    onExportKey: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(file.listName()) },
        supportingContent = { Text("${file.effectiveType().name} · ${formatSize(file.sizeBytes)}") },
        leadingContent = { Icon(iconFor(file.effectiveType()), contentDescription = null) },
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
                        text = { Text("Export decrypted copy") },
                        onClick = { menuOpen = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export encrypted copy (.adx)") },
                        onClick = { menuOpen = false; onExportEncrypted() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export key\u2026") },
                        onClick = { menuOpen = false; onExportKey() }
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

/** Same picker, but asks for write access so the original can be deleted after a successful import. */
private class OpenWritableDocuments : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}
