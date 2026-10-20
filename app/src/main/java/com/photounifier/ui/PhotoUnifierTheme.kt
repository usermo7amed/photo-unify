package com.photounifier.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 color palette for Photo Unifier with expressive shape and type hierarchy.
 *
 * Primary: Deep, refined Teal (representing image clarity and processing precision)
 * Secondary: Warm Amber (for success highlights, export cues, and badges)
 * Tertiary: Electric Azure / Cyan (for interactive accent accents)
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF72F8E7),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF944B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF301400),
    tertiary = Color(0xFF006782),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCE9FF),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF7FBF9),
    onBackground = Color(0xFF181C1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF51DBCB),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF72F8E7),
    secondary = Color(0xFFFFB77C),
    onSecondary = Color(0xFF4F2500),
    secondaryContainer = Color(0xFF713700),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFF65D3FF),
    onTertiary = Color(0xFF003544),
    tertiaryContainer = Color(0xFF004D62),
    onTertiaryContainer = Color(0xFFBCE9FF),
    background = Color(0xFF0F1514),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF141B19),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF222B29),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946)
)

private val PhotoUnifierShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val DefaultTypography = Typography()
private val PhotoUnifierTypography = DefaultTypography.copy(
    displaySmall = DefaultTypography.displaySmall.copy(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold
    ),
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
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = DefaultTypography.titleSmall.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = DefaultTypography.bodyLarge.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = DefaultTypography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = DefaultTypography.bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal
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

/**
 * Main application theme wrapper for Photo Unifier.
 *
 * @param darkTheme Whether to use dark theme colors. Defaults to system preference.
 * @param dynamicColor When true on Android 12+ (API 31+), applies system wallpaper-based colors.
 *                     Defaults to true to embrace Material You aesthetics.
 */
@Composable
fun PhotoUnifierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PhotoUnifierTypography,
        shapes = PhotoUnifierShapes,
        content = content
    )
}
