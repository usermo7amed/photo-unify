package com.photounifier.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.photounifier.ui.PhotoUnifierGlass
import com.photounifier.ui.components.GlassmorphicButton
import com.photounifier.ui.components.GlassmorphicOutlinedButton
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photounifier.CropCornerEditor
import com.photounifier.PhotoUiState
import com.photounifier.ui.PhotoUnifierDuration
import com.photounifier.ui.PhotoUnifierElevation
import com.photounifier.ui.PhotoUnifierHeight
import com.photounifier.ui.PhotoUnifierIconSize
import com.photounifier.ui.PhotoUnifierRadius
import com.photounifier.ui.PhotoUnifierSpacing
import com.photounifier.ui.components.CropResultPreview
import com.photounifier.ui.components.ValuePill

/**
 * بطاقة مراجعة القص في شاشة العمليات الرئيسية
 */
@Composable
fun CropReviewCard(
    state: PhotoUiState,
    openCvReady: Boolean,
    onOpenCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(PhotoUnifierRadius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.mid),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(PhotoUnifierSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Surface(
                        shape = RoundedCornerShape(PhotoUnifierRadius.md),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Crop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(PhotoUnifierIconSize.md)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "قص وتصحيح المنظور",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اكتشاف أوتوماتيكي لحدود الورقة وتصحيح الزوايا",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ValuePill(
                    if (state.cropEdits.isNotEmpty()) "تم تعديل ${state.cropEdits.size} صفحة"
                    else "جاهز للمراجعة"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(PhotoUnifierRadius.lg))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val preview = state.sourcePreview
                if (preview != null && !preview.isRecycled) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "معاينة الصفحة ${state.previewIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    Text(
                        text = "ستظهر معاينة المستند هنا",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.cropApplied && state.cropPreview != null) {
                CropResultPreview(state.cropPreview)
            }

            GlassmorphicButton(
                onClick = onOpenCrop,
                enabled = !state.busy && !state.cropApplying && state.selected.isNotEmpty() && openCvReady,
                modifier = Modifier.fillMaxWidth(),
                height = PhotoUnifierHeight.buttonPrimary,
                shape = RoundedCornerShape(PhotoUnifierRadius.md)
            ) {
                Icon(
                    imageVector = Icons.Filled.Crop,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.sm)
                )
                Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                Text(
                    text = if (state.cropEdits.isNotEmpty()) "استكمال مراجعة الصفحات (${state.cropEdits.size}/${state.selected.size})"
                    else "فتح محرر زوايا الورقة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            val hintText = when {
                state.cropApplying -> "جاري تجهيز القص وتصحيح المنظور بجودة فائقة..."
                state.selected.isEmpty() -> "اختر صورة أولاً لتتمكن من مراجعة حدودها."
                !openCvReady -> "مكتبة معالجة الصور (OpenCV) غير جاهزة حالياً."
                state.cropEdits.isNotEmpty() -> "تم حفظ تعديلات ${state.cropEdits.size} من ${state.selected.size} صفحة. اضغط «التالي» للمتابعة للتحسين."
                else -> "خطوة اختيارية: يمكنك الانتقال للخطوة التالية مباشرة إذا كانت الصور لا تحتاج إلى قص."
            }

            Surface(
                shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * شاشة ورشة عمل القص التفاعلية
 */
@Composable
fun CropWorkspaceView(
    state: PhotoUiState,
    onMoveCropCorner: (Int, Float, Float) -> Unit,
    onSelectPage: (Int) -> Unit,
    onEnsurePageThumbnail: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactLayout = maxHeight < 500.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(
                if (compactLayout) PhotoUnifierSpacing.xxs else PhotoUnifierSpacing.xs
            )
        ) {
            // شريط العنوان وعداد الصفحات
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(PhotoUnifierIconSize.md)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "ضبط زوايا المستند",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اسحب الزوايا لتعديل حدود الورقة وتصحيح المنظور.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                ValuePill("صفحة ${state.previewIndex + 1} / ${state.selected.size}")
            }

            // منطقة محرر الزوايا التفاعلي
            if (state.cropSource != null && !state.cropSource.isRecycled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = if (compactLayout) 110.dp else 170.dp),
                    shape = RoundedCornerShape(PhotoUnifierRadius.lg),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shadowElevation = PhotoUnifierElevation.low
                ) {
                    CropCornerEditor(
                        bitmap = state.cropSource,
                        corners = state.cropCorners,
                        onMoveCorner = onMoveCropCorner,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = if (compactLayout) 110.dp else 170.dp),
                    shape = RoundedCornerShape(PhotoUnifierRadius.lg),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "جاري تجهيز معاينة الصفحة...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // شريط معاينة ناتج القص السريع
            CropPreviewStrip(bitmap = state.cropPreview, compact = compactLayout)

            // تنبيه التعديلات غير المحفوظة
            AnimatedVisibility(
                visible = state.cropDirty,
                enter = fadeIn(animationSpec = tween(PhotoUnifierDuration.fast)),
                exit = fadeOut(animationSpec = tween(PhotoUnifierDuration.fast))
            ) {
                Surface(
                    shape = RoundedCornerShape(PhotoUnifierRadius.xs),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.xs, vertical = PhotoUnifierSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(PhotoUnifierIconSize.xs)
                        )
                        Text(
                            text = "تعديلات غير محفوظة — احفظ الصفحة أو تراجع عن التعديل.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // شريط صفحات المجموعة
            DocumentPageThumbnailStrip(
                state = state,
                onSelectPage = onSelectPage,
                onEnsureThumbnail = onEnsurePageThumbnail,
                enabled = !state.cropApplying && !state.busy && !state.cropDirty,
                compact = compactLayout
            )
        }
    }
}

/**
 * شريط معاينة ناتج القص المصغر
 */
@Composable
private fun CropPreviewStrip(
    bitmap: android.graphics.Bitmap?,
    compact: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PhotoUnifierRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) PhotoUnifierSpacing.xxs else PhotoUnifierSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(
                        width = if (compact) 36.dp else 44.dp,
                        height = if (compact) 46.dp else 56.dp
                    )
                    .clip(RoundedCornerShape(PhotoUnifierRadius.xs))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(PhotoUnifierRadius.xs)),
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
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.sm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "معاينة ناتج القص الحية",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (bitmap != null && !bitmap.isRecycled) {
                        "تتحدث المعاينة مباشرة مع سحب وضبط زوايا الورقة."
                    } else {
                        "ستظهر النتيجة فور تحريك أو اختيار الزوايا."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    softWrap = !compact
                )
            }
        }
    }
}

/**
 * شريط الصور المصغرة لصفحات المستند مع شارة التعديل المنجز
 */
@Composable
private fun DocumentPageThumbnailStrip(
    state: PhotoUiState,
    onSelectPage: (Int) -> Unit,
    onEnsureThumbnail: (Int) -> Unit,
    enabled: Boolean,
    compact: Boolean
) {
    val thumbnailWidth = if (compact) 60.dp else 74.dp
    val thumbnailHeight = if (compact) 66.dp else 84.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "صفحات المستند",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(PhotoUnifierRadius.xs),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "${state.cropEdits.size} من ${state.selected.size} معدلة",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(state.selected, key = { _, uri -> uri.toString() }) { index, uri ->
                LaunchedEffect(uri) { onEnsureThumbnail(index) }
                val thumbnail = state.selectionThumbnails.firstOrNull { it.uri == uri }
                val isCurrent = index == state.previewIndex
                val isEdited = state.cropEdits.containsKey(uri)

                Column(
                    modifier = Modifier
                        .width(thumbnailWidth)
                        .clickable(enabled = enabled, role = Role.Button) { onSelectPage(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = thumbnailWidth, height = thumbnailHeight)
                            .clip(RoundedCornerShape(PhotoUnifierRadius.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(PhotoUnifierRadius.md)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnail != null && !thumbnail.bitmap.isRecycled) {
                            Image(
                                bitmap = thumbnail.bitmap.asImageBitmap(),
                                contentDescription = "صفحة ${index + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(PhotoUnifierRadius.xs)),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Medium
                            )
                        } else {
                            Text(
                                "صفحة ${index + 1}",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isEdited) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "تم تعديل الصفحة ${index + 1}",
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = "صفحة ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * شريط الأدوات السفلي لورشة القص مع أزرار التدوير والتراجع وحفظ الصفحة
 */
@Composable
fun CropWorkspaceBottomBar(
    state: PhotoUiState,
    onRotatePage: () -> Unit,
    onUndoCrop: () -> Unit,
    onResetCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onApplyCrop: () -> Unit,
    onFinishCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xxs),
        shape = RoundedCornerShape(PhotoUnifierRadius.xl),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = PhotoUnifierElevation.high
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PhotoUnifierSpacing.xs, vertical = PhotoUnifierSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
        ) {
            // شريط الأدوات السريعة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
            ) {
                CropToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "تراجع",
                    enabled = state.cropUndoStack.isNotEmpty() && !state.cropApplying && !state.busy,
                    onClick = onUndoCrop
                )
                CropToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.RestartAlt,
                    label = "إعادة ضبط",
                    enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                    onClick = onResetCrop
                )
                CropToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.RotateRight,
                    label = "تدوير 90°",
                    enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                    onClick = onRotatePage
                )
            }

            // شريط أزرار التنفيذ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassmorphicOutlinedButton(
                    onClick = onCancelCrop,
                    enabled = !state.cropApplying && !state.busy,
                    modifier = Modifier.weight(0.8f),
                    height = PhotoUnifierHeight.buttonSecondary,
                    shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs)
                    )
                    Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xxs))
                    Text(
                        text = "إلغاء",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                GlassmorphicButton(
                    onClick = onApplyCrop,
                    enabled = state.cropPreview != null && state.cropDirty && !state.cropApplying && !state.busy,
                    modifier = Modifier.weight(1.2f),
                    height = PhotoUnifierHeight.buttonSecondary,
                    shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs)
                    )
                    Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xxs))
                    Text(
                        text = if (state.cropApplying) "جارٍ الحفظ..." else "حفظ الصفحة",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                GlassmorphicButton(
                    onClick = onFinishCrop,
                    enabled = !state.cropApplying && !state.busy && !state.cropDirty,
                    modifier = Modifier.weight(1.2f),
                    height = PhotoUnifierHeight.buttonSecondary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = PhotoUnifierGlass.secondaryAlpha),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs)
                    )
                    Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xxs))
                    Text(
                        text = "إنهاء المراجعة",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CropToolButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    GlassmorphicButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PhotoUnifierGlass.surfaceAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = PhotoUnifierGlass.borderAlpha),
        shape = RoundedCornerShape(PhotoUnifierRadius.sm),
        height = PhotoUnifierHeight.buttonTertiary,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(PhotoUnifierIconSize.xs)
        )
        Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
    }
}
