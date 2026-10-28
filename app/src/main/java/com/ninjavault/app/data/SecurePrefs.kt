package com.ninjavault.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ninjavault.app.crypto.PasswordHasher
import com.ninjavault.app.vault.LockoutStore
import com.ninjavault.app.vault.SwipeDirection
import java.security.KeyStore

/**
 * Stores app configuration — the activation sequence, hashed PIN (never plaintext),
 * lockout counters and general settings — in EncryptedSharedPreferences, which
 * itself is backed by a Keystore-protected key.
 *
 * Note: nothing here (or the PIN/pattern/biometric gate) is used to derive the
 * vault's file-encryption keys; those are wrapped solely by a separate Keystore
 * key (see CryptoManager). The pattern/PIN/biometrics are an access gate in the
 * app, not a cryptographic secret.
 */
class SecurePrefs(context: Context) : LockoutStore {

    private val prefs: SharedPreferences = createPrefs(context)

    var isSetupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) = prefs.edit().putBoolean("setup_complete", value).apply()

    fun saveActivationSequence(sequence: List<SwipeDirection>) {
        prefs.edit().putString("activation_sequence", sequence.joinToString(",") { it.name }).apply()
    }

    fun getActivationSequence(): List<SwipeDirection> {
        val raw = prefs.getString("activation_sequence", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { name -> SwipeDirection.values().firstOrNull { it.name == name } }
    }

    // --- Activation-sequence lockout (persisted so a restart doesn't reset it) ---

    override var failedAttempts: Int
        get() = prefs.getInt("lockout_failed_attempts", 0)
        set(value) = prefs.edit().putInt("lockout_failed_attempts", value).apply()

    override var lockedUntilMillis: Long
        get() = prefs.getLong("lockout_locked_until", 0L)
        set(value) = prefs.edit().putLong("lockout_locked_until", value).apply()

    // --- PIN ---

    fun isPinEnabled(): Boolean = prefs.contains("pin_hash")

    fun setPin(pin: CharArray) {
        val result = PasswordHasher.hash(pin)
        prefs.edit()
            .putString("pin_hash", result.hashBase64)
            .putString("pin_salt", result.saltBase64)
            .apply()
        clearPinFailures()
    }

    fun verifyPin(pin: CharArray): Boolean {
        val hash = prefs.getString("pin_hash", null) ?: return false
        val salt = prefs.getString("pin_salt", null) ?: return false
        return PasswordHasher.verify(pin, hash, salt)
    }

    fun clearPin() {
        prefs.edit().remove("pin_hash").remove("pin_salt").apply()
        clearPinFailures()
    }

    /** Milliseconds until PIN entry is allowed again (0 when not locked). */
    fun pinLockRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (prefs.getLong("pin_locked_until", 0L) - nowMillis).coerceAtLeast(0L)

    fun recordPinFailure(nowMillis: Long = System.currentTimeMillis()) {
        val failures = prefs.getInt("pin_failed_attempts", 0) + 1
        val editor = prefs.edit().putInt("pin_failed_attempts", failures)
        if (failures >= PIN_MAX_ATTEMPTS) {
            val exponent = (failures - PIN_MAX_ATTEMPTS).coerceAtMost(20)
            val total = PIN_LOCKOUT_MILLIS + (PIN_LOCKOUT_MILLIS shl exponent)
            editor.putLong("pin_locked_until", nowMillis + total.coerceAtMost(MAX_LOCKOUT_MILLIS))
        }
        editor.apply()
    }

    fun clearPinFailures() {
        prefs.edit().remove("pin_failed_attempts").remove("pin_locked_until").apply()
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

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics_enabled", true)
        set(value) = prefs.edit().putBoolean("haptics_enabled", value).apply()

    // Kept for compatibility with existing installs; new selections use vaultColorTheme.
    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    var vaultColorTheme: String
        get() = prefs.getString("vault_color_theme", "SHADOW_NINJA") ?: "SHADOW_NINJA"
        set(value) = prefs.edit().putString("vault_color_theme", value).apply()

    /**
     * Full local reset: clears all preferences. The vault's own Keystore key and
     * files are destroyed separately by VaultRepository.wipeAll().
     */
    fun wipeAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "ninja_run_secure_prefs"
        const val PIN_MAX_ATTEMPTS = 5
        const val PIN_LOCKOUT_MILLIS = 30_000L
        const val MAX_LOCKOUT_MILLIS = 60 * 60 * 1000L

        fun build(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /**
         * EncryptedSharedPreferences can throw if its Keystore key was invalidated or the
         * file no longer matches it (a known failure after some device/OS events). Rather
         * than crash on every launch, start with fresh settings; the vault itself uses an
         * independent key, so existing files remain readable after the pattern is set up again.
         */
        fun createPrefs(context: Context): SharedPreferences = try {
            build(context)
        } catch (e: Exception) {
            context.deleteSharedPreferences(PREFS_NAME)
            try {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            } catch (_: Exception) {
            }
            build(context)
        }
    }
}
