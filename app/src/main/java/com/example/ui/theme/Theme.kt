package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val GlassWaveColorScheme = darkColorScheme(
    primary = LiquidCyan,
    onPrimary = ObsidianDeep,
    secondary = LuminousEmerald,
    onSecondary = ObsidianDeep,
    tertiary = ElectricSapphire,
    error = RubyCrimson,
    onError = GlassWhite,
    background = ObsidianDeep,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianMid,
    onSurfaceVariant = TextSecondary
)

@Composable
fun GlassWaveTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = GlassWaveColorScheme,
            typography = Typography,
            content = content
        )
    }
}
