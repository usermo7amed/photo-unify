package com.photounifier.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D7E73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F7EE),
    onPrimaryContainer = Color(0xFF043834),
    secondary = Color(0xFF436069),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E7EC),
    onSecondaryContainer = Color(0xFF132A32),
    tertiary = Color(0xFF4E63C2),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEE3FF),
    onTertiaryContainer = Color(0xFF15225E),
    background = Color(0xFFF7F9FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EEF2),
    onSurface = Color(0xFF161F26),
    onSurfaceVariant = Color(0xFF4C5D6A),
    outline = Color(0xFFCAD6DF),
    outlineVariant = Color(0xFFE0E8EE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5BE3D0),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF08544D),
    onPrimaryContainer = Color(0xFFB4FCEF),
    secondary = Color(0xFFABC4CD),
    onSecondary = Color(0xFF16323B),
    secondaryContainer = Color(0xFF264049),
    onSecondaryContainer = Color(0xFFD3E7F0),
    tertiary = Color(0xFFB8C4FF),
    onTertiary = Color(0xFF1F2E6F),
    tertiaryContainer = Color(0xFF334386),
    onTertiaryContainer = Color(0xFFDDE3FF),
    background = Color(0xFF0E1318),
    surface = Color(0xFF141B22),
    surfaceVariant = Color(0xFF1E2730),
    onSurface = Color(0xFFE8EEF4),
    onSurfaceVariant = Color(0xFFAAB8C4),
    outline = Color(0xFF485663),
    outlineVariant = Color(0xFF2B3642)
)

private val PhotoUnifierShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DefaultTypography = Typography()
private val PhotoUnifierTypography = DefaultTypography.copy(
    headlineSmall = DefaultTypography.headlineSmall.copy(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = DefaultTypography.titleLarge.copy(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = DefaultTypography.titleMedium.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = DefaultTypography.titleSmall.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = DefaultTypography.bodyLarge.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = DefaultTypography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = DefaultTypography.bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = DefaultTypography.labelLarge.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = DefaultTypography.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = DefaultTypography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun PhotoUnifierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = PhotoUnifierTypography,
        shapes = PhotoUnifierShapes,
        content = content
    )
}
