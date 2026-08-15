package com.ninjavault.app.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ninjavault.app.data.SecurePrefs
import com.ninjavault.app.ui.game.NinjaRunScreen
import com.ninjavault.app.ui.setup.SetupWizardScreen
import com.ninjavault.app.ui.settings.SettingsScreen
import com.ninjavault.app.ui.vault.MediaPlayerScreen
import com.ninjavault.app.ui.vault.VaultScreen
import com.ninjavault.app.ui.vault.VaultViewModel
import com.ninjavault.app.vault.ActivationSequenceManager
import com.ninjavault.app.vault.SwipeDirection
import androidx.fragment.app.FragmentActivity

private object Routes {
    const val SETUP = "setup"
    const val GAME = "game"
    const val PIN_CHECK = "pin_check"
    const val VAULT = "vault"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
}

@Composable
fun NinjaRunNavGraph(activity: ComponentActivity) {
    val prefs = remember { SecurePrefs(activity) }
    val vaultViewModel = remember { VaultViewModel(activity) }
    val navController = rememberNavController()

    var selectedFileId by remember { mutableStateOf<Long?>(null) }
    val files by vaultViewModel.currentFiles.collectAsState(initial = emptyList())
    val selectedFile = files.firstOrNull { it.id == selectedFileId }

    val startDestination = if (prefs.isSetupComplete) Routes.GAME else Routes.SETUP

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.SETUP) {
            SetupWizardScreen { sequence, pin, biometricEnabled ->
                prefs.saveActivationSequence(sequence)
                pin?.let { prefs.setPin(it.toCharArray()) }
                prefs.isBiometricEnabled = biometricEnabled
                prefs.isSetupComplete = true
                navController.navigate(Routes.GAME) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                }
            }
        }

        composable(Routes.GAME) {
            val activationManager = remember {
                ActivationSequenceManager(configuredSequence = prefs.getActivationSequence())
            }
            NinjaRunScreen(
                activationManager = activationManager,
                onUnlocked = {
                    if (prefs.isPinEnabled() || prefs.isBiometricEnabled) {
                        navController.navigate(Routes.PIN_CHECK)
                    } else {
                        navController.navigate(Routes.VAULT)
                    }
                }
            )
        }

        composable(Routes.PIN_CHECK) {
            PinCheckScreen(
                prefs = prefs,
                activity = activity as? FragmentActivity,
                onVerified = {
                    navController.navigate(Routes.VAULT) {
                        popUpTo(Routes.GAME)
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.VAULT) {
            VaultScreen(
                viewModel = vaultViewModel,
                onOpenFile = { file ->
                    selectedFileId = file.id
                    navController.navigate(Routes.PLAYER)
                },
                onLock = {
                    vaultViewModel.clearPlaybackTempFiles()
                    navController.navigate(Routes.GAME) {
                        popUpTo(Routes.VAULT) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PLAYER) {
            selectedFile?.let { MediaPlayerScreen(file = it, viewModel = vaultViewModel) }
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                prefs = prefs,
                onChangeSequence = { navController.navigate(Routes.SETUP) },
                onChangePin = { navController.navigate(Routes.SETUP) },
                onResetVault = {
                    prefs.wipeAll()
                    navController.navigate(Routes.SETUP) {
                        popUpTo(0)
                    }
                }
            )
        }
    }
}
