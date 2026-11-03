package com.senzaproject.createapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, DARK, LIGHT }

private val DarkColors = darkColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0B1220),
    secondaryContainer = Color(0xFF232C38),
    onSecondaryContainer = TextLight,
    tertiary = GreenAccent,
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFDCFCE7),
    background = DarkBackground,
    onBackground = TextLight,
    surface = DarkSurface,
    onSurface = TextLight,
    surfaceVariant = Color(0xFF232C38),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF273241),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF212A35),
    surfaceContainerHighest = Color(0xFF232C38),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    tertiary = Color(0xFF16A34A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF14532D),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

@Composable
fun CreateAppTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
