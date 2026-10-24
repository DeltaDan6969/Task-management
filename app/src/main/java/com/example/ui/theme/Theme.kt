package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RetailOrangeLight,
    onPrimary = Color.Black,
    primaryContainer = RetailOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = RetailHeaderBlackLight,
    onSecondary = Color.White,
    background = Color(0xFF121417),
    surface = Color(0xFF1B1E24),
    surfaceVariant = Color(0xFF242932),
    onBackground = Color(0xFFE2E4E9),
    onSurface = Color(0xFFE2E4E9),
    outline = Color(0xFF3E4552),
    error = RetailRedMissed
)

private val LightColorScheme = lightColorScheme(
    primary = RetailOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCCBC),
    onPrimaryContainer = RetailOrangeDark,
    secondary = RetailHeaderBlack,
    onSecondary = Color.White,
    background = RetailBackground,
    surface = RetailSurface,
    surfaceVariant = RetailSurfaceSubtle,
    onBackground = RetailTextPrimary,
    onSurface = RetailTextPrimary,
    outline = RetailBorder,
    error = RetailRedMissed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
