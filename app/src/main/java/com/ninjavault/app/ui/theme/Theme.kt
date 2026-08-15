package com.ninjavault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NinjaPurple,
    secondary = NinjaCoin,
    background = NinjaIndigoBg,
    surface = NinjaIndigoBg,
    error = NinjaDanger
)

private val LightColors = lightColorScheme(
    primary = NinjaPurpleDark,
    secondary = NinjaCoin,
    background = NinjaIndigoBgLight,
    surface = NinjaIndigoBgLight,
    error = NinjaDanger
)

@Composable
fun NinjaRunTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = NinjaTypography, content = content)
}
