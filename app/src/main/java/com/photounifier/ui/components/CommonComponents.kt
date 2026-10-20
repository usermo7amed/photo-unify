package com.photounifier.ui.components

import com.photounifier.ui.PhotoUnifierDuration
import com.photounifier.ui.PhotoUnifierElevation
import com.photounifier.ui.PhotoUnifierHeight
import com.photounifier.ui.PhotoUnifierIconSize
import com.photounifier.ui.PhotoUnifierSpacing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photounifier.ui.PhotoUnifierGlass
import com.photounifier.ui.PhotoUnifierRadius
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope

/**
 * Brush متحرك لإنشاء تأثير Shimmer loading على عناصر التحميل والهياكل العظمية
 */
@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    showShimmer: Boolean = true
): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )

        val transition = rememberInfiniteTransition(label = "shimmerTransition")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslate"
        )
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation - 300f, translateAnimation - 300f),
            end = Offset(translateAnimation, translateAnimation)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

/**
 * Modifier لإضافة وميض هيكلي (Shimmer Loading Skeleton)
 */
fun Modifier.shimmerLoading(
    isLoading: Boolean = true,
    shape: Shape = RoundedCornerShape(PhotoUnifierRadius.md)
): Modifier = composed {
    if (!isLoading) return@composed this
    val brush = shimmerBrush()
    this
        .clip(shape)
        .background(brush)
}

/**
 * عنصر هيكل عظمي متحرك للصور المصغرة أثناء التحميل
 */
@Composable
fun ShimmerThumbnailPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .shimmerLoading(isLoading = true, shape = RoundedCornerShape(PhotoUnifierRadius.md))
    )
}

/**
 * شريط تقدم مسار العمل متعدد الخطوات مع خط رابط متحرك وعمق بصري وتوافقية إمكانية الوصول
 */
@Composable
fun StepProgress(
    currentStep: Int,
    titles: List<String>,
    selectedCount: Int,
    onStepClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PhotoUnifierRadius.xl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.low)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PhotoUnifierSpacing.md, vertical = PhotoUnifierSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "مسار العمل",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الخطوة ${currentStep + 1} من ${titles.size} • ${titles.getOrElse(currentStep) { "" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ValuePill(if (selectedCount == 0) "اختر الصور" else "${selectedCount} صورة")
            }

            // Stepper indicators مع خط رابط متحرك
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                titles.indices.forEach { index ->
                    val isCompleted = index < currentStep
                    val isCurrent = index == currentStep
                    val canNavigate = isCompleted && onStepClick != null
                    val stepTitle = titles.getOrElse(index) { "" }

                    val stepSize by animateDpAsState(
                        targetValue = if (isCurrent) PhotoUnifierHeight.stepIndicator else PhotoUnifierHeight.stepIndicatorSm,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "stepIndicatorSize"
                    )

                    val stepContainer by animateColorAsState(
                        targetValue = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isCompleted -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = tween(durationMillis = PhotoUnifierDuration.normal),
                        label = "stepContainerColor"
                    )

                    val stepTextColor by animateColorAsState(
                        targetValue = when {
                            isCurrent -> MaterialTheme.colorScheme.onPrimary
                            isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = PhotoUnifierDuration.normal),
                        label = "stepTextColor"
                    )

                    Surface(
                        modifier = Modifier
                            .size(stepSize)
                            .semantics {
                                role = Role.Button
                                contentDescription = if (isCompleted) {
                                    "خطوة مكتملة ${index + 1}: $stepTitle"
                                } else if (isCurrent) {
                                    "الخطوة الحالية ${index + 1}: $stepTitle"
                                } else {
                                    "خطوة قادمة ${index + 1}: $stepTitle"
                                }
                            }
                            .then(
                                if (canNavigate) {
                                    Modifier.clickable { onStepClick?.invoke(index) }
                                } else Modifier
                            ),
                        shape = CircleShape,
                        color = stepContainer,
                        shadowElevation = if (isCurrent) PhotoUnifierElevation.high else PhotoUnifierElevation.none
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isCompleted) "✓" else "${index + 1}",
                                color = stepTextColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (index < titles.lastIndex) {
                        val isLineActive = index < currentStep
                        val animatedLineProgress by animateFloatAsState(
                            targetValue = if (isLineActive) 1f else 0f,
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            label = "stepLineProgress"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .padding(horizontal = 3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedLineProgress)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Snackbar مخصص بتصميم Material You (Material 3) متناسق مع ألوان وثيم التطبيق
 */
@Composable
fun PhotoUnifierSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(PhotoUnifierSpacing.sm)
            .fillMaxWidth(),
        shape = RoundedCornerShape(PhotoUnifierRadius.lg),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = PhotoUnifierElevation.high,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.md, vertical = PhotoUnifierSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier.size(PhotoUnifierIconSize.sm)
            )
            Text(
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.weight(1f)
            )
            snackbarData.visuals.actionLabel?.let { action ->
                TextButton(
                    onClick = { snackbarData.performAction() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    Text(
                        text = action,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun ValuePill(
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = value,
            modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xxs),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StepHeading(
    number: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
    ) {
        Surface(
            modifier = Modifier.size(PhotoUnifierHeight.stepIndicator),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = PhotoUnifierElevation.low
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionHeading(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CropResultPreview(
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 78.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "معاينة ناتج القص",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    Text(
                        "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
            ) {
                Text(
                    text = "معاينة ناتج القص",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (bitmap != null && !bitmap.isRecycled)
                        "تم تصحيح المنظور؛ راجع استقامة المستند قبل المتابعة."
                    else
                        "ستظهر هنا نتيجة ضبط زوايا الورقة وتصحيح المنظور.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * زر زجاجي مدمج وشبه شفاف مع تأثير Blur ناعم وإطار بلوري رفيع
 */
@Composable
fun GlassmorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = PhotoUnifierGlass.primaryAlpha),
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    borderColor: Color = Color.White.copy(alpha = PhotoUnifierGlass.borderAlpha),
    shape: Shape = RoundedCornerShape(PhotoUnifierRadius.md),
    height: Dp = PhotoUnifierHeight.buttonPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = PhotoUnifierSpacing.md, vertical = PhotoUnifierSpacing.xxs),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(height)
            .clip(shape),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (enabled) borderColor else borderColor.copy(alpha = 0.12f)
        ),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // طبقة خلفية شبه شفافة مع تمويه Blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(PhotoUnifierGlass.blurRadius)
                    .background(
                        if (enabled) containerColor
                        else containerColor.copy(alpha = 0.25f)
                    )
            )
            // محتوى الزر الواضح
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * زر محدد زجاجي مدمج وشبه شفاف مع تمويه
 */
@Composable
fun GlassmorphicOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PhotoUnifierGlass.surfaceAlpha),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = PhotoUnifierGlass.borderAlpha),
    shape: Shape = RoundedCornerShape(PhotoUnifierRadius.md),
    height: Dp = PhotoUnifierHeight.buttonSecondary,
    contentPadding: PaddingValues = PaddingValues(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xxs),
    content: @Composable RowScope.() -> Unit
) {
    GlassmorphicButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor,
        shape = shape,
        height = height,
        contentPadding = contentPadding,
        content = content
    )
}

