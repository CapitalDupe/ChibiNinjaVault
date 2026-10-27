package com.ninjavault.app.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ninjavault.app.crypto.KeyExport

/** Asks for a password; when [confirmPassword] is true it must be entered twice and be long enough. */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    confirmPassword: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = confirmPassword && password.isNotEmpty() && password.length < KeyExport.MIN_PASSWORD_LENGTH
    val mismatch = confirmPassword && repeat.isNotEmpty() && repeat != password
    val valid = if (confirmPassword) {
        password.length >= KeyExport.MIN_PASSWORD_LENGTH && password == repeat
    } else {
        password.isNotEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = tooShort
                )
                if (confirmPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text("Repeat password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = mismatch
                    )
                }
                if (tooShort) {
                    Text(
                        "Use at least ${KeyExport.MIN_PASSWORD_LENGTH} characters.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (mismatch) {
                    Text(
                        "Passwords don't match.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSubmit(password.toCharArray()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
