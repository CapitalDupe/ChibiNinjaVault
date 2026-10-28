package com.ninjavault.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ninjavault.app.data.SecurePrefs
import com.ninjavault.app.ui.theme.VaultColorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: SecurePrefs,
    biometricAvailable: Boolean,
    onBack: () -> Unit,
    onChangeCredentials: () -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onResetVault: () -> Unit
) {
    var biometric by remember { mutableStateOf(prefs.isBiometricEnabled && biometricAvailable) }
    var lockOnBackground by remember { mutableStateOf(prefs.lockOnBackground) }
    var haptics by remember { mutableStateOf(prefs.hapticsEnabled) }
    var selectedTheme by remember { mutableStateOf(VaultColorTheme.fromStored(prefs.vaultColorTheme)) }
    var autoLock by remember { mutableStateOf(prefs.autoLockTimeoutSeconds.toFloat()) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection("Unlock") {
                Button(onClick = onChangeCredentials) { Text("Change pattern, PIN or biometrics") }
            }

            SettingsSection("Biometrics") {
                SettingsToggle(
                    label = if (biometricAvailable) "Enable biometric unlock"
                    else "Biometric unlock (not available on this device)",
                    checked = biometric,
                    enabled = biometricAvailable
                ) {
                    biometric = it
                    prefs.isBiometricEnabled = it
                }
            }

            SettingsSection("Auto-lock") {
                Text("Lock after ${autoLock.toInt()} seconds of inactivity")
                Slider(
                    value = autoLock,
                    onValueChange = { autoLock = it },
                    onValueChangeFinished = { prefs.autoLockTimeoutSeconds = autoLock.toInt() },
                    valueRange = 10f..300f
                )
                SettingsToggle("Lock when app goes to background", lockOnBackground) {
                    lockOnBackground = it
                    prefs.lockOnBackground = it
                }
            }

            SettingsSection("Appearance") {
                Text(
                    "Choose a colour theme for your vault. Your selection is saved on this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                VaultColorTheme.values().forEach { theme ->
                    val selected = selectedTheme == theme
                    Surface(
                        onClick = {
                            selectedTheme = theme
                            prefs.vaultColorTheme = theme.name
                            onDarkThemeChange(theme.isDark)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(theme.background, theme.primary, theme.secondary).forEach { swatch ->
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(theme.label, style = MaterialTheme.typography.titleMedium)
                                Text(theme.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = selected, onClick = {
                                selectedTheme = theme
                                prefs.vaultColorTheme = theme.name
                                onDarkThemeChange(theme.isDark)
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingsToggle("Haptics", haptics) {
                    haptics = it
                    prefs.hapticsEnabled = it
                }
            }

            SettingsSection("Danger zone") {
                Text(
                    "Resetting the vault deletes every encrypted file and the encryption key. " +
                        "This cannot be undone and there is no recovery backdoor.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset vault") }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset vault?") },
            text = { Text("This permanently deletes all encrypted files and the encryption key. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showResetConfirm = false; onResetVault() }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Column(content = content)
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
