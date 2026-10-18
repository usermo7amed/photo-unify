package com.photounifier.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF087F78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF7EF),
    onPrimaryContainer = Color(0xFF073F3A),
    secondary = Color(0xFF526973),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EDF1),
    onSecondaryContainer = Color(0xFF1D333C),
    tertiary = Color(0xFF526DE8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E7FF),
    onTertiaryContainer = Color(0xFF1B2B68),
    background = Color(0xFFF4F7FB),
    surface = Color(0xFFFCFDFE),
    surfaceVariant = Color(0xFFE8EEF3),
    onSurface = Color(0xFF15232D),
    onSurfaceVariant = Color(0xFF526470),
    outline = Color(0xFFC5D2DB),
    outlineVariant = Color(0xFFDCE5EB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78E5D2),
    onPrimary = Color(0xFF06312D),
    primaryContainer = Color(0xFF124E49),
    onPrimaryContainer = Color(0xFFC8FFF4),
    secondary = Color(0xFFB8CAD2),
    onSecondary = Color(0xFF20323B),
    secondaryContainer = Color(0xFF263B45),
    onSecondaryContainer = Color(0xFFE1EEF4),
    tertiary = Color(0xFFB4C0FF),
    onTertiary = Color(0xFF202E6B),
    tertiaryContainer = Color(0xFF303E78),
    onTertiaryContainer = Color(0xFFE1E6FF),
    background = Color(0xFF0E141A),
    surface = Color(0xFF151D25),
    surfaceVariant = Color(0xFF202B35),
    onSurface = Color(0xFFE8F0F5),
    onSurfaceVariant = Color(0xFFB5C4CE),
    outline = Color(0xFF52636F),
    outlineVariant = Color(0xFF35444F)
)

@Composable
fun PhotoUnifierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
