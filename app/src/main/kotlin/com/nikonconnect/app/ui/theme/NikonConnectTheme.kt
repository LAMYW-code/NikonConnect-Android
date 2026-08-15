package com.nikonconnect.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    tertiary = Color(0xFF0A8F5A),
    onTertiary = Color.White,
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF17191D),
    surface = Color.White,
    onSurface = Color(0xFF17191D),
    surfaceVariant = Color(0xFFEAE7E0),
    onSurfaceVariant = Color(0xFF565A63),
    outline = Color(0xFFD2CFC8),
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CB8FF),
    onPrimary = Color(0xFF08245C),
    tertiary = Color(0xFF69D5A4),
    onTertiary = Color(0xFF003822),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFF5F3EE),
    surface = Color(0xFF1A1C20),
    onSurface = Color(0xFFF5F3EE),
    surfaceVariant = Color(0xFF282B31),
    onSurfaceVariant = Color(0xFFC3C5CC),
    outline = Color(0xFF454951),
    error = Color(0xFFFF6B6B),
)

@Composable
fun NikonConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
