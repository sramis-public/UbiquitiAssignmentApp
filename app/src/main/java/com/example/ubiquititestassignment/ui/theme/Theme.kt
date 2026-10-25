package com.example.ubiquititestassignment.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CatalogLightColorScheme = lightColorScheme(
    primary = CatalogBlue,
    onPrimary = CatalogSurface,
    primaryContainer = CatalogBackground,
    onPrimaryContainer = CatalogBlue,
    secondary = CatalogSecondaryText,
    onSecondary = CatalogSurface,
    secondaryContainer = CatalogBackground,
    onSecondaryContainer = CatalogText,
    tertiary = CatalogBlue,
    onTertiary = CatalogSurface,
    tertiaryContainer = CatalogBackground,
    onTertiaryContainer = CatalogBlue,
    background = CatalogBackground,
    onBackground = CatalogText,
    surface = CatalogSurface,
    onSurface = CatalogText,
    surfaceVariant = CatalogBackground,
    onSurfaceVariant = CatalogSecondaryText,
    surfaceTint = CatalogBlue,
    inverseSurface = CatalogText,
    inverseOnSurface = CatalogSurface,
    inversePrimary = CatalogSurface,
    outline = CatalogMuted,
    outlineVariant = CatalogBackground,
    surfaceBright = CatalogSurface,
    surfaceDim = CatalogBackground,
    surfaceContainerLowest = CatalogSurface,
    surfaceContainerLow = CatalogBackground,
    surfaceContainer = CatalogBackground,
    surfaceContainerHigh = CatalogBackground,
    surfaceContainerHighest = CatalogBackground,
)

private val CatalogDarkColorScheme = darkColorScheme(
    primary = CatalogDarkBlue,
    onPrimary = CatalogDarkOnBlue,
    primaryContainer = CatalogDarkBlueContainer,
    onPrimaryContainer = CatalogDarkText,
    secondary = CatalogDarkSecondaryText,
    onSecondary = CatalogDarkSurface,
    secondaryContainer = CatalogDarkSecondaryContainer,
    onSecondaryContainer = CatalogDarkText,
    tertiary = CatalogDarkBlue,
    onTertiary = CatalogDarkOnBlue,
    tertiaryContainer = CatalogDarkBlueContainer,
    onTertiaryContainer = CatalogDarkText,
    background = CatalogDarkBackground,
    onBackground = CatalogDarkText,
    surface = CatalogDarkSurface,
    onSurface = CatalogDarkText,
    surfaceVariant = CatalogDarkSecondaryContainer,
    onSurfaceVariant = CatalogDarkSecondaryText,
    surfaceTint = CatalogDarkBlue,
    inverseSurface = CatalogBackground,
    inverseOnSurface = CatalogText,
    inversePrimary = CatalogBlue,
    outline = CatalogMuted,
    outlineVariant = CatalogDarkOutlineVariant,
    surfaceBright = CatalogDarkSurfaceHighest,
    surfaceDim = CatalogDarkBackground,
    surfaceContainerLowest = CatalogDarkSurfaceLowest,
    surfaceContainerLow = CatalogDarkSurfaceLow,
    surfaceContainer = CatalogDarkSurface,
    surfaceContainerHigh = CatalogDarkSurfaceHigh,
    surfaceContainerHighest = CatalogDarkSurfaceHighest,
)

/** Follows system appearance with fixed palettes, independent of wallpaper colors. */
@Composable
fun UbiquitiTestAssignmentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CatalogDarkColorScheme else CatalogLightColorScheme,
        typography = Typography,
        content = content,
    )
}
