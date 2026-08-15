package com.ninjavault.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ninjavault.app.data.SecurePrefs

@Composable
fun SettingsScreen(
    prefs: SecurePrefs,
    onChangeSequence: () -> Unit,
    onChangePin: () -> Unit,
    onResetVault: () -> Unit
) {
    var biometric by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var lockOnBackground by remember { mutableStateOf(prefs.lockOnBackground) }
    var soundEffects by remember { mutableStateOf(prefs.soundEffectsEnabled) }
    var haptics by remember { mutableStateOf(prefs.hapticsEnabled) }
    var darkTheme by remember { mutableStateOf(prefs.darkTheme) }
    var autoLock by remember { mutableStateOf(prefs.autoLockTimeoutSeconds.toFloat()) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            SettingsSection("Unlock pattern") {
                Button(onClick = onChangeSequence) { Text("Change unlock swipe pattern") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onChangePin) { Text("Change PIN") }
            }

            SettingsSection("Biometrics") {
                SettingsToggle("Enable biometric unlock", biometric) {
                    biometric = it; prefs.isBiometricEnabled = it
                }
            }

            SettingsSection("Auto-lock") {
                Text("Lock after ${autoLock.toInt()} seconds of inactivity")
                Slider(
                    value = autoLock,
                    onValueChange = { autoLock = it; prefs.autoLockTimeoutSeconds = it.toInt() },
                    valueRange = 10f..300f
                )
                SettingsToggle("Lock when app goes to background", lockOnBackground) {
                    lockOnBackground = it; prefs.lockOnBackground = it
                }
            }

            SettingsSection("Feedback") {
                SettingsToggle("Sound effects", soundEffects) { soundEffects = it; prefs.soundEffectsEnabled = it }
                SettingsToggle("Haptics", haptics) { haptics = it; prefs.hapticsEnabled = it }
                SettingsToggle("Dark theme", darkTheme) { darkTheme = it; prefs.darkTheme = it }
            }

            SettingsSection("Danger zone") {
                Text(
                    "Resetting the vault deletes the encryption key and all encrypted files. " +
                        "This cannot be undone and there is no recovery backdoor.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showResetConfirm = true }, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )) { Text("Reset vault") }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset vault?") },
            text = { Text("This permanently deletes all encrypted files and cannot be undone.") },
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
    Divider(modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
