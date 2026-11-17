package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ProxyGateDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = Color(0xFF9DFCE0),
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = Color(0xFFBDE9FF),
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFFDFA6),
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFFD9DF),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = ObsidianSurface,
    surfaceContainerLow = ObsidianBg,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1F2D40),
)

private val ProxyGateLightColorScheme = lightColorScheme(
    primary = Color(0xFF008F64),
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = Color(0xFF001E2E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF281800),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF3E0012),
    background = SlateBg,
    onBackground = TextPrimaryLight,
    surface = SlateSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SlateSurface,
    surfaceContainerLow = SlateBg,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = SlateOutline,
    outlineVariant = Color(0xFFD4DFEC),
)

val ProxyGateShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ProxyGateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ProxyGateDarkColorScheme else ProxyGateLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ProxyGateTypography,
        shapes = ProxyGateShapes,
        content = content,
    )
}
