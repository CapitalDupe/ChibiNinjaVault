package com.ninjavault.app.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.ninjavault.app.crypto.BiometricHelper
import com.ninjavault.app.data.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Second factor after the swipe pattern: PIN and/or biometrics.
 *
 * The caller only routes here when at least one factor is actually usable, and a
 * biometric-only setup always gets a retry button, so the user can't be left on a
 * screen with no way forward. PIN attempts are rate-limited with a persisted
 * backoff (see [SecurePrefs.recordPinFailure]).
 */
@Composable
fun PinCheckScreen(
    prefs: SecurePrefs,
    activity: FragmentActivity,
    biometricUsable: Boolean,
    onVerified: () -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    val pinEnabled = prefs.isPinEnabled()
    val lockRemainingMillis = prefs.pinLockRemainingMillis(now)

    fun promptBiometric() {
        BiometricHelper.authenticate(
            activity = activity,
            negativeText = if (pinEnabled) "Use PIN" else "Cancel",
            onSuccess = {
                prefs.clearPinFailures()
                onVerified()
            },
            onError = { /* stay on this screen: PIN entry or retry button below */ }
        )
    }

    LaunchedEffect(Unit) {
        if (biometricUsable) promptBiometric()
    }
    // Ticks so the lockout countdown updates and the button re-enables.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Confirm it's you", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (pinEnabled) {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                enabled = lockRemainingMillis == 0L && !checking
            )
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = lockRemainingMillis == 0L && !checking && pin.length >= 4,
                onClick = {
                    checking = true
                    val attempt = pin.toCharArray()
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { prefs.verifyPin(attempt) }
                        attempt.fill('0')
                        if (ok) {
                            prefs.clearPinFailures()
                            onVerified()
                        } else {
                            prefs.recordPinFailure()
                            pin = ""
                            error = "Incorrect PIN"
                            now = System.currentTimeMillis()
                        }
                        checking = false
                    }
                }
            ) { Text("Unlock") }
        }

        if (biometricUsable) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { promptBiometric() }) { Text("Use biometrics") }
        }

        if (lockRemainingMillis > 0L) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Too many attempts. Try again in ${(lockRemainingMillis + 999) / 1000}s",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
