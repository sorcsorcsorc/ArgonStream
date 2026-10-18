package com.argon.stream.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorPalette = darkColorScheme(
    primary = ArgonCyan,
    onPrimary = ArgonObsidian,
    primaryContainer = ArgonCyanContainer,
    onPrimaryContainer = ArgonOnCyanContainer,
    secondary = ArgonAmber,
    onSecondary = ArgonObsidian,
    secondaryContainer = ArgonAmberContainer,
    onSecondaryContainer = ArgonOnAmberContainer,
    tertiary = ArgonEmerald,
    onTertiary = ArgonObsidian,
    tertiaryContainer = ArgonEmeraldContainer,
    onTertiaryContainer = ArgonOnEmeraldContainer,
    background = ArgonObsidian,
    onBackground = ArgonTextPrimary,
    surface = ArgonSurface,
    onSurface = ArgonTextPrimary,
    surfaceVariant = ArgonSurfaceVariant,
    onSurfaceVariant = ArgonTextSecondary,
    surfaceDim = ArgonObsidian,
    surfaceBright = ArgonSurfaceContainerHighest,
    surfaceContainerLowest = ArgonSurfaceContainerLowest,
    surfaceContainerLow = ArgonSurfaceContainerLow,
    surfaceContainer = ArgonSurfaceContainer,
    surfaceContainerHigh = ArgonSurfaceContainerHigh,
    surfaceContainerHighest = ArgonSurfaceContainerHighest,
    outline = ArgonOutline,
    outlineVariant = ArgonOutlineVariant,
    error = ArgonCrimson,
    onError = ArgonObsidian,
    errorContainer = ArgonCrimsonContainer,
    onErrorContainer = ArgonOnCrimsonContainer
)

private val LightColorPalette = lightColorScheme(
    primary = ArgonLightPrimary,
    primaryContainer = ArgonLightPrimaryContainer,
    onPrimaryContainer = ArgonLightOnPrimaryContainer,
    secondary = ArgonLightSecondary,
    tertiary = ArgonLightTertiary,
    background = ArgonLightBackground,
    onBackground = ArgonLightText,
    surface = ArgonLightSurface,
    onSurface = ArgonLightText,
    onSurfaceVariant = ArgonLightTextSecondary,
    surfaceContainerLow = ArgonLightSurfaceContainerLow,
    surfaceContainer = ArgonLightSurfaceContainer,
    surfaceContainerHigh = ArgonLightSurfaceContainerHigh,
    surfaceContainerHighest = ArgonLightSurfaceContainerHighest
)

@Composable
fun ArgonTheme(
    darkTheme: Boolean = true, // Dark theme by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorPalette
        else -> LightColorPalette
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArgonTypography,
        shapes = ArgonShapes,
        content = content
    )
}
