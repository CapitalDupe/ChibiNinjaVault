package com.ninjavault.app.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.ninjavault.app.crypto.BiometricHelper
import com.ninjavault.app.data.SecurePrefs

@Composable
fun PinCheckScreen(
    prefs: SecurePrefs,
    activity: FragmentActivity?,
    onVerified: () -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (prefs.isBiometricEnabled && activity != null && BiometricHelper.isAvailable(activity)) {
            BiometricHelper.authenticate(
                activity = activity,
                onSuccess = onVerified,
                onError = { /* fall back to PIN entry below */ }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Confirm it's you", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (prefs.isPinEnabled()) {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (prefs.verifyPin(pin.toCharArray())) onVerified()
                else error = "Incorrect PIN"
            }) { Text("Unlock") }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
