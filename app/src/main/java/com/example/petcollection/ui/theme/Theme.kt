package com.example.petcollection.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF52715B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0ECD9), onPrimaryContainer = Color(0xFF233627),
    secondary = Color(0xFF856344), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E5D4), onSecondaryContainer = Color(0xFF4F3823),
    tertiary = Color(0xFF687951), tertiaryContainer = Color(0xFFE3EBD5), onTertiaryContainer = Color(0xFF2C3C1F),
    background = Color(0xFFFAF8F2), onBackground = Color(0xFF303D33),
    surface = Color(0xFFFAF8F2), onSurface = Color(0xFF303D33),
    surfaceVariant = Color(0xFFE7E8DE), onSurfaceVariant = Color(0xFF52594F),
    surfaceContainer = Color(0xFFF0F0E7), surfaceContainerLow = Color(0xFFF5F4ED),
    outline = Color(0xFF757D70), outlineVariant = Color(0xFFD4DACD)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0CFAF), onPrimary = Color(0xFF203824),
    primaryContainer = Color(0xFF354E39), onPrimaryContainer = Color(0xFFD6EBD0),
    secondary = Color(0xFFE4BF9C), onSecondary = Color(0xFF422C19),
    secondaryContainer = Color(0xFF59412C), onSecondaryContainer = Color(0xFFFFDEC0),
    tertiary = Color(0xFFBDCEA5), tertiaryContainer = Color(0xFF414E31), onTertiaryContainer = Color(0xFFDCEAC4),
    background = Color(0xFF171C17), onBackground = Color(0xFFE1E6DC),
    surface = Color(0xFF171C17), onSurface = Color(0xFFE1E6DC),
    surfaceVariant = Color(0xFF41483D), onSurfaceVariant = Color(0xFFC2CABC),
    surfaceContainer = Color(0xFF242A23), surfaceContainerLow = Color(0xFF1E241E),
    outline = Color(0xFF8D9787), outlineVariant = Color(0xFF414B3D)
)

@Composable
fun PetCollectionTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
