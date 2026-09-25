package com.amisayem.kothabolbo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.amisayem.kothabolbo.domain.model.AccentTheme
import com.amisayem.kothabolbo.domain.model.AppearanceMode

val DeepNavy = Color(0xFF0A0E1A)
val NavySurface = Color(0xFF111827)
val NavyElevated = Color(0xFF182235)
val BrandCyan = Color(0xFF00D4FF)
val BrandPurple = Color(0xFF7C3AED)
val Danger = Color(0xFFFF4D6D)
val Success = Color(0xFF4ADE80)
val Warning = Color(0xFFFFB020)

private fun accent(theme: AccentTheme): Color = when (theme) {
    AccentTheme.CYAN -> BrandCyan
    AccentTheme.PURPLE -> BrandPurple
    AccentTheme.OCEAN -> Color(0xFF3B82F6)
    AccentTheme.ROSE -> Color(0xFFF43F8C)
}

private fun dark(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF001018),
    primaryContainer = accent.copy(alpha = .22f),
    onPrimaryContainer = Color.White,
    secondary = BrandPurple,
    onSecondary = Color.White,
    tertiary = BrandCyan,
    background = DeepNavy,
    onBackground = Color(0xFFF5F7FB),
    surface = NavySurface,
    onSurface = Color(0xFFF5F7FB),
    surfaceVariant = NavyElevated,
    onSurfaceVariant = Color(0xFFB8C2D8),
    outline = Color(0xFF34415A),
    error = Danger,
    onError = Color.White
)

private fun light(accent: Color): ColorScheme = lightColorScheme(
    primary = if (accent == BrandCyan) Color(0xFF007C96) else accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = .18f),
    onPrimaryContainer = Color(0xFF07111E),
    secondary = BrandPurple,
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF101522),
    surface = Color.White,
    onSurface = Color(0xFF101522),
    surfaceVariant = Color(0xFFE9EDF6),
    onSurfaceVariant = Color(0xFF536079),
    outline = Color(0xFFBAC3D2),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun KothaBolboTheme(
    appearance: AppearanceMode = AppearanceMode.DARK,
    accentTheme: AccentTheme = AccentTheme.CYAN,
    content: @Composable () -> Unit
) {
    val darkMode = when (appearance) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (darkMode) dark(accent(accentTheme)) else light(accent(accentTheme)),
        typography = Typography(),
        content = content
    )
}
