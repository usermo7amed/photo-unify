package com.photounifier.ui

import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale for consistent layouts across Photo Unifier screens.
 *
 * Keep screen-specific exceptions deliberate rather than introducing new
 * one-off spacing values for common UI patterns.
 */
object PhotoUnifierSpacing {
    val xxs = 4.dp
    val xs  = 8.dp
    val sm  = 12.dp
    val md  = 16.dp
    val lg  = 20.dp
    val xl  = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/**
 * Shared icon size scale. Prefer these over one-off Modifier.size() calls.
 */
object PhotoUnifierIconSize {
    val xs   = 16.dp
    val sm   = 18.dp
    val md   = 20.dp
    val lg   = 24.dp
    val xl   = 32.dp
    val xxl  = 48.dp
    val hero = 60.dp
}

/**
 * Shared corner radius tokens for surfaces that don't use MaterialTheme.shapes.
 * Prefer MaterialTheme.shapes wherever possible; use these for custom Canvas
 * or RoundedCornerShape overrides that must match the theme visually.
 */
object PhotoUnifierRadius {
    val xs  = 8.dp
    val sm  = 10.dp
    val md  = 12.dp
    val lg  = 14.dp
    val xl  = 20.dp
    val xxl = 28.dp
}

/**
 * Elevation tokens — keep all shadow depths consistent across the app.
 */
object PhotoUnifierElevation {
    val none = 0.dp
    val low  = 1.dp
    val mid  = 2.dp
    val high = 4.dp
}

/**
 * Animation duration tokens for consistent motion timing.
 */
object PhotoUnifierDuration {
    const val fast   = 160
    const val normal = 240
    const val slow   = 360
    const val enter  = 280
    const val exit   = 200
}

/**
 * Component height tokens for interactive elements - optimized for a compact, modern feel.
 */
object PhotoUnifierHeight {
    val buttonPrimary   = 44.dp
    val buttonSecondary = 38.dp
    val buttonTertiary  = 34.dp
    val buttonSmall     = 30.dp
    val progressBar     = 6.dp
    val thumbnailBar    = 84.dp
    val stepIndicator   = 32.dp
    val stepIndicatorSm = 26.dp
}

/**
 * Glassmorphic design tokens for translucent blurred surfaces and buttons.
 */
object PhotoUnifierGlass {
    val blurRadius = 14.dp
    const val primaryAlpha = 0.82f
    const val secondaryAlpha = 0.65f
    const val surfaceAlpha = 0.60f
    const val borderAlpha = 0.25f
    const val borderLightAlpha = 0.40f
}

