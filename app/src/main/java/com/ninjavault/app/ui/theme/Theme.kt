package com.ninjavault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Color palettes for the real Compose vault UI. Stored IDs are independent of display labels. */
enum class VaultColorTheme(
    val label: String,
    val description: String,
    val isDark: Boolean,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color
) {
    SHADOW_NINJA("Shadow Ninja", "Black with ninja red", true,
        Color(0xFFFF3B4F), Color(0xFFFF6675), Color(0xFF101114), Color(0xFF191A20),
        Color(0xFF21070B), Color(0xFF21070B), Color(0xFFF5F5F5), Color(0xFFF5F5F5)),
    CYBER_PURPLE("Cyber Purple", "Charcoal with neon purple", true,
        Color(0xFFB56CFF), Color(0xFFE0B7FF), Color(0xFF151020), Color(0xFF21182F),
        Color(0xFF24083D), Color(0xFF24083D), Color(0xFFF7EEFF), Color(0xFFF7EEFF)),
    OCEAN("Ocean", "Midnight blue with cyan", true,
        Color(0xFF22D3EE), Color(0xFF7DEBFA), Color(0xFF0B1728), Color(0xFF12243A),
        Color(0xFF003640), Color(0xFF003640), Color(0xFFE5F6FF), Color(0xFFE5F6FF)),
    EMERALD("Emerald", "Deep green with emerald", true,
        Color(0xFF34D399), Color(0xFF8AF0C8), Color(0xFF0C1D18), Color(0xFF142B23),
        Color(0xFF063D2A), Color(0xFF063D2A), Color(0xFFE6FFF3), Color(0xFFE6FFF3)),
    GOLD_VAULT("Gold Vault", "Luxury black and gold", true,
        Color(0xFFE8BD58), Color(0xFFFFE4A0), Color(0xFF17150F), Color(0xFF252115),
        Color(0xFF382707), Color(0xFF382707), Color(0xFFFFF7DF), Color(0xFFFFF7DF)),
    LIGHT("Light", "Pale gray with slate", false,
        Color(0xFF475569), Color(0xFF64748B), Color(0xFFF3F5F9), Color(0xFFFFFFFF),
        Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF172033), Color(0xFF172033)),
    CHERRY("Cherry", "Burgundy with cherry pink", true,
        Color(0xFFFB7185), Color(0xFFFFA3B1), Color(0xFF240F18), Color(0xFF351522),
        Color(0xFF4A081A), Color(0xFF4A081A), Color(0xFFFFF0F4), Color(0xFFFFF0F4)),
    SUNSET("Sunset", "Warm brown with orange", true,
        Color(0xFFFB923C), Color(0xFFFFC078), Color(0xFF24150D), Color(0xFF352014),
        Color(0xFF4A2106), Color(0xFF4A2106), Color(0xFFFFF2E5), Color(0xFFFFF2E5));

    companion object {
        fun fromStored(value: String?): VaultColorTheme =
            values().firstOrNull { it.name == value } ?: SHADOW_NINJA
    }
}

@Composable
fun NinjaRunTheme(
    theme: VaultColorTheme = VaultColorTheme.SHADOW_NINJA,
    content: @Composable () -> Unit
) {
    val colors = if (theme.isDark) {
        darkColorScheme(
            primary = theme.primary, onPrimary = theme.onPrimary,
            secondary = theme.secondary, onSecondary = theme.onSecondary,
            background = theme.background, onBackground = theme.onBackground,
            surface = theme.surface, onSurface = theme.onSurface,
            primaryContainer = theme.primary.copy(alpha = 0.22f),
            secondaryContainer = theme.secondary.copy(alpha = 0.20f),
            error = NinjaDanger
        )
    } else {
        lightColorScheme(
            primary = theme.primary, onPrimary = theme.onPrimary,
            secondary = theme.secondary, onSecondary = theme.onSecondary,
            background = theme.background, onBackground = theme.onBackground,
            surface = theme.surface, onSurface = theme.onSurface,
            primaryContainer = theme.primary.copy(alpha = 0.14f),
            secondaryContainer = theme.secondary.copy(alpha = 0.16f),
            error = NinjaDanger
        )
    }
    MaterialTheme(colorScheme = colors, typography = NinjaTypography, content = content)
}
