package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HorrorDarkColorScheme = darkColorScheme(
    primary = BloodCrimson,
    onPrimary = ParchmentAsh,
    primaryContainer = BloodCrimsonMuted,
    onPrimaryContainer = ParchmentAsh,
    secondary = TarnishedBrass,
    onSecondary = ObsidianVoid,
    tertiary = SpectralTeal,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = ParchmentAsh,
    surface = ShadowCharcoal,
    onSurface = ParchmentAsh,
    surfaceVariant = CryptSurface,
    onSurfaceVariant = MistGrey,
    outline = BorderSmoke,
    error = BloodCrimson,
    onError = ParchmentAsh
)

@Composable
fun SlendrinaGoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HorrorDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
