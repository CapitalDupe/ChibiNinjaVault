package com.ninjavault.app.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ninjavault.app.vault.SwipeDirection

private val ALL_DIRECTIONS = listOf(SwipeDirection.LEFT, SwipeDirection.RIGHT, SwipeDirection.UP, SwipeDirection.DOWN)

/**
 * First-launch wizard. The user builds their own unlock sequence (3-8 swipes),
 * confirms it twice to avoid accidental misconfiguration, and can optionally
 * add a PIN and/or biometric layer on top. Everything here is explained plainly
 * to the user — this is their own configured access pattern to their own data.
 */
@Composable
fun SetupWizardScreen(
    biometricAvailable: Boolean,
    onComplete: (sequence: List<SwipeDirection>, pin: String?, biometricEnabled: Boolean) -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var sequence by remember { mutableStateOf(listOf<SwipeDirection>()) }
    var confirmSequence by remember { mutableStateOf(listOf<SwipeDirection>()) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var wantsPin by remember { mutableStateOf(false) }
    var wantsBiometric by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Ninja Run", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Set up the swipe pattern that unlocks your personal vault. " +
                "This is your own combination — pick something memorable but not obvious.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> BuildSequenceStep(
                title = "Step 1: Choose your unlock pattern (3-8 swipes)",
                current = sequence,
                onChange = { sequence = it },
                onNext = {
                    if (sequence.size < 3) errorText = "Choose at least 3 swipes"
                    else { errorText = null; step = 1 }
                }
            )
            1 -> BuildSequenceStep(
                title = "Step 2: Confirm your pattern by repeating it",
                current = confirmSequence,
                onChange = { confirmSequence = it },
                onNext = {
                    if (confirmSequence != sequence) {
                        errorText = "Doesn't match. Try again."
                        confirmSequence = emptyList()
                    } else {
                        errorText = null
                        step = 2
                    }
                }
            )
            2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Step 3: Optional extra protection", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wantsPin, onCheckedChange = { wantsPin = it })
                    Text("Add a PIN as well")
                }
                if (wantsPin) {
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("Enter PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = confirmPin, onValueChange = { confirmPin = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(
                        checked = wantsBiometric && biometricAvailable,
                        onCheckedChange = { wantsBiometric = it },
                        enabled = biometricAvailable
                    )
                    Text(
                        if (biometricAvailable) "Enable biometric unlock"
                        else "Biometric unlock isn't available on this device"
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    if (wantsPin && (pin.length < 4 || pin != confirmPin)) {
                        errorText = "PIN must be at least 4 digits and match"
                        return@Button
                    }
                    errorText = null
                    onComplete(sequence, if (wantsPin) pin else null, wantsBiometric && biometricAvailable)
                }) {
                    Text("Finish setup")
                }
            }
        }

        errorText?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BuildSequenceStep(
    title: String,
    current: List<SwipeDirection>,
    onChange: (List<SwipeDirection>) -> Unit,
    onNext: () -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    Text("Tap the arrows in the order you want (this simulates a swipe during setup):")
    Spacer(Modifier.height(8.dp))
    Row {
        ALL_DIRECTIONS.forEach { dir ->
            OutlinedButton(
                onClick = { if (current.size < 8) onChange(current + dir) },
                modifier = Modifier.padding(4.dp)
            ) { Text(dir.arrow()) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Pattern so far (${current.size}/8):")
    LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
        items(current) { dir ->
            Text(dir.arrow(), modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.headlineMedium)
        }
    }
    Row {
        TextButton(onClick = { onChange(emptyList()) }) { Text("Clear") }
        TextButton(onClick = { onChange(current.dropLast(1)) }) { Text("Undo") }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onNext) { Text("Next") }
}

private fun SwipeDirection.arrow(): String = when (this) {
    SwipeDirection.LEFT -> "←"
    SwipeDirection.RIGHT -> "→"
    SwipeDirection.UP -> "↑"
    SwipeDirection.DOWN -> "↓"
}
