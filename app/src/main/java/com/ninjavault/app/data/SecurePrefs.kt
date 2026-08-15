package com.ninjavault.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ninjavault.app.crypto.PasswordHasher
import com.ninjavault.app.vault.SwipeDirection

/**
 * Stores app configuration — the activation sequence, hashed PIN (never plaintext),
 * and general settings — in EncryptedSharedPreferences, which itself is backed by
 * a Keystore-protected key. Nothing here is a "backdoor": there is no way to
 * derive the vault's file-encryption keys from this data without the user's
 * real PIN/passphrase.
 */
class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ninja_run_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var isSetupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) = prefs.edit().putBoolean("setup_complete", value).apply()

    fun saveActivationSequence(sequence: List<SwipeDirection>) {
        prefs.edit().putString("activation_sequence", sequence.joinToString(",") { it.name }).apply()
    }

    fun getActivationSequence(): List<SwipeDirection> {
        val raw = prefs.getString("activation_sequence", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { SwipeDirection.valueOf(it) }
    }

    fun isPinEnabled(): Boolean = prefs.contains("pin_hash")

    fun setPin(pin: CharArray) {
        val result = PasswordHasher.hash(pin)
        prefs.edit()
            .putString("pin_hash", result.hashBase64)
            .putString("pin_salt", result.saltBase64)
            .apply()
    }

    fun verifyPin(pin: CharArray): Boolean {
        val hash = prefs.getString("pin_hash", null) ?: return false
        val salt = prefs.getString("pin_salt", null) ?: return false
        return PasswordHasher.verify(pin, hash, salt)
    }

    fun clearPin() {
        prefs.edit().remove("pin_hash").remove("pin_salt").apply()
    }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false)
        set(value) = prefs.edit().putBoolean("biometric_enabled", value).apply()

    var autoLockTimeoutSeconds: Int
        get() = prefs.getInt("auto_lock_timeout_seconds", 60)
        set(value) = prefs.edit().putInt("auto_lock_timeout_seconds", value).apply()

    var lockOnBackground: Boolean
        get() = prefs.getBoolean("lock_on_background", true)
        set(value) = prefs.edit().putBoolean("lock_on_background", value).apply()

    var soundEffectsEnabled: Boolean
        get() = prefs.getBoolean("sound_effects_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_effects_enabled", value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics_enabled", true)
        set(value) = prefs.edit().putBoolean("haptics_enabled", value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    /** Full local reset: clears all preferences. Does not touch the Keystore master key. */
    fun wipeAll() {
        prefs.edit().clear().apply()
    }
}
