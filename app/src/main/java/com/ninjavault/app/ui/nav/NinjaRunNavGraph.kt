package com.ninjavault.app.ui.nav

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ninjavault.app.crypto.BiometricHelper
import com.ninjavault.app.data.SecurePrefs
import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.ui.game.NinjaRunScreen
import com.ninjavault.app.ui.settings.SettingsScreen
import com.ninjavault.app.ui.setup.SetupWizardScreen
import com.ninjavault.app.ui.vault.MediaPlayerScreen
import com.ninjavault.app.ui.vault.VaultScreen
import com.ninjavault.app.ui.vault.VaultViewModel
import com.ninjavault.app.vault.ActivationSequenceManager
import com.ninjavault.app.vault.AppLockController
import kotlinx.coroutines.delay

private object Routes {
    const val SETUP = "setup"
    const val GAME = "game"
    const val PIN_CHECK = "pin_check"
    const val VAULT = "vault"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
}

/** Routes that are only reachable after unlocking; leaving the app/idling here must re-lock. */
private val UNLOCKED_ROUTES = setOf(Routes.PIN_CHECK, Routes.VAULT, Routes.PLAYER, Routes.SETTINGS)

@Composable
fun NinjaRunNavGraph(
    activity: FragmentActivity,
    prefs: SecurePrefs,
    appLock: AppLockController,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val vaultViewModel: VaultViewModel = viewModel(
        factory = viewModelFactory { initializer { VaultViewModel(activity.applicationContext) } }
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var selectedFileId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastInteractionMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val startDestination = remember { if (prefs.isSetupComplete) Routes.GAME else Routes.SETUP }

    fun biometricUsable(): Boolean =
        prefs.isBiometricEnabled && BiometricHelper.isAvailable(activity)

    /** Back to the game screen with everything above it (vault, player, settings) cleared. */
    fun lockVault() {
        vaultViewModel.clearPlaybackTempFiles()
        navController.navigate(Routes.GAME) {
            popUpTo(Routes.GAME) { inclusive = true }
            launchSingleTop = true
        }
    }

    /** Clears the whole back stack and starts the setup wizard. */
    fun restartSetup() {
        vaultViewModel.clearPlaybackTempFiles()
        navController.navigate(Routes.SETUP) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // Lock when the activity reports it went to the background / was away too long.
    LaunchedEffect(Unit) {
        appLock.lockEvents.collect {
            val route = navController.currentDestination?.route
            if (route != null && route in UNLOCKED_ROUTES) lockVault()
        }
    }

    // Lock after a period without touches while browsing the vault or settings.
    // (Playback is exempt so a long video doesn't lock mid-watch; it still locks on background.)
    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.VAULT || currentRoute == Routes.SETTINGS) {
            lastInteractionMillis = SystemClock.elapsedRealtime()
            while (true) {
                delay(1_000)
                val idleMillis = SystemClock.elapsedRealtime() - lastInteractionMillis
                if (idleMillis >= prefs.autoLockTimeoutSeconds * 1_000L) {
                    lockVault()
                    break
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionMillis = SystemClock.elapsedRealtime()
                    }
                }
            }
    ) {
        NavHost(navController = navController, startDestination = startDestination) {

            composable(Routes.SETUP) {
                SetupWizardScreen(
                    biometricAvailable = BiometricHelper.isAvailable(activity)
                ) { sequence, pin, biometricEnabled ->
                    prefs.saveActivationSequence(sequence)
                    if (pin != null) prefs.setPin(pin.toCharArray()) else prefs.clearPin()
                    prefs.isBiometricEnabled = biometricEnabled
                    // Fresh credentials start with a clean lockout slate.
                    prefs.failedAttempts = 0
                    prefs.lockedUntilMillis = 0L
                    prefs.isSetupComplete = true
                    navController.navigate(Routes.GAME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }

            composable(Routes.GAME) {
                // Lockout state is persisted through [prefs], so recreating the manager
                // (navigation, restart) does not reset failed attempts.
                val activationManager = remember {
                    ActivationSequenceManager(
                        configuredSequence = prefs.getActivationSequence(),
                        store = prefs
                    )
                }
                NinjaRunScreen(
                    activationManager = activationManager,
                    hapticsEnabled = prefs.hapticsEnabled,
                    onUnlocked = {
                        if (prefs.isPinEnabled() || biometricUsable()) {
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
                    activity = activity,
                    biometricUsable = biometricUsable(),
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
                    appLock = appLock,
                    onOpenFile = { file ->
                        selectedFileId = file.id
                        navController.navigate(Routes.PLAYER)
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onLock = { lockVault() }
                )
            }

            composable(Routes.PLAYER) {
                // Looked up by id (not from the filtered list) so search text can't blank the screen.
                val file by produceState<VaultFileEntity?>(initialValue = null, selectedFileId) {
                    val id: Long? = selectedFileId
                    value = if (id != null) vaultViewModel.getFile(id) else null
                }
                file?.let { MediaPlayerScreen(file = it, viewModel = vaultViewModel) }
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    prefs = prefs,
                    biometricAvailable = BiometricHelper.isAvailable(activity),
                    onBack = { navController.popBackStack() },
                    // Restarting setup from a clean back stack means finishing it lands on
                    // the game screen with the vault locked, never back inside the vault.
                    onChangeCredentials = { restartSetup() },
                    onDarkThemeChange = onDarkThemeChange,
                    onResetVault = {
                        vaultViewModel.resetVault { result ->
                            result
                                .onSuccess {
                                    prefs.wipeAll()
                                    restartSetup()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        activity,
                                        "Reset failed: ${it.message ?: "unknown error"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                )
            }
        }
    }
}
