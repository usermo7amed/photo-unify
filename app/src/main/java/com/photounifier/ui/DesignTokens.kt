package com.photounifier.ui

import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale for consistent layouts across Photo Unifier screens.
 * Keep screen-specific exceptions deliberate rather than introducing one-off values.
 */
object PhotoUnifierSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
}

/** Shared icon size scale. Prefer these over one-off Modifier.size() calls. */
object PhotoUnifierIconSize {
    val xs = 16.dp
    val sm = 18.dp
    val md = 20.dp
    val hero = 60.dp
}

/** Shared corner radii inspired by the softer, more expressive Material 3 shape language. */
object PhotoUnifierRadius {
    val xs = 10.dp
    val sm = 14.dp
    val md = 18.dp
    val lg = 24.dp
    val xl = 28.dp
}

/** Elevation tokens — use restraint so shape and color carry the visual hierarchy. */
object PhotoUnifierElevation {
    val none = 0.dp
    val low = 1.dp
    val mid = 3.dp
    val high = 6.dp
}

/** Motion timings for subtle, spring-friendly expressive feedback. */
object PhotoUnifierDuration {
    const val fast = 180
    const val normal = 280
    const val expressive = 420
}

/** Comfortable touch targets for buttons, selectors and workflow progress. */
object PhotoUnifierHeight {
    val buttonPrimary = 52.dp
    val buttonSecondary = 48.dp
    val buttonTertiary = 44.dp
    val buttonSmall = 40.dp
    val menuItem = 48.dp
    val navigationBar = 80.dp
    val progressBar = 8.dp
    val stepIndicator = 38.dp
    val stepIndicatorSm = 30.dp
}

/** Retained for image overlays; interactive buttons no longer depend on blur. */
object PhotoUnifierGlass {
    val blurRadius = 14.dp
    const val primaryAlpha = 1.0f
    const val secondaryAlpha = 0.88f
    const val surfaceAlpha = 0.92f
    const val borderAlpha = 0.72f
}
