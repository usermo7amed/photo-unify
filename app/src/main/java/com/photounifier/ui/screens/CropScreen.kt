package com.photounifier.ui.screens

import androidx.compose.ui.res.stringResource
import com.photounifier.R

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
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
                            text = stringResource(R.string.crop_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.crop_subtitle),
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
        val compactLayout = maxHeight < 620.dp
        var showCropResultPreview by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(
                if (compactLayout) PhotoUnifierSpacing.xxs else PhotoUnifierSpacing.xs
            )
        ) {
            // عنوان مختصر؛ المساحة الأساسية للصورة.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(PhotoUnifierIconSize.sm)
                    )
                    Text(
                        text = stringResource(R.string.crop_editor_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                ) {
                    if (state.cropPreview != null && !state.cropPreview.isRecycled) {
                        TextButton(
                            onClick = { showCropResultPreview = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_preview),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    ValuePill("صفحة ${state.previewIndex + 1} / ${state.selected.size}")
                }
            }

            // محرر الزوايا هو العنصر الرئيسي، ويتمدد ليشغل المساحة المتاحة.
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

            // اختيار الصفحات مفيد فقط عند وجود أكثر من صفحة.
            if (state.selected.size > 1) {
                DocumentPageThumbnailStrip(
                    state = state,
                    onSelectPage = onSelectPage,
                    onEnsureThumbnail = onEnsurePageThumbnail,
                    enabled = !state.cropApplying && !state.busy && !state.cropDirty,
                    compact = compactLayout
                )
            }
        }

        // معاينة الناتج متاحة عند الطلب بدل بطاقة دائمة تستهلك مساحة المحرر.
        if (showCropResultPreview) {
            Dialog(onDismissRequest = { showCropResultPreview = false }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PhotoUnifierRadius.lg),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PhotoUnifierSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.crop_preview_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showCropResultPreview = false }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "إغلاق المعاينة"
                                )
                            }
                        }

                        val preview = state.cropPreview
                        if (preview != null && !preview.isRecycled) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "ناتج القص وتصحيح المنظور",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 560.dp),
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.Medium
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.crop_preview_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { showCropResultPreview = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }
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
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(PhotoUnifierRadius.lg),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = PhotoUnifierElevation.mid
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PhotoUnifierSpacing.xxs, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CropToolIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "تراجع",
                enabled = state.cropUndoStack.isNotEmpty() && !state.cropApplying && !state.busy,
                onClick = onUndoCrop
            )
            CropToolIconButton(
                icon = Icons.Filled.RestartAlt,
                label = "إعادة ضبط",
                enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                onClick = onResetCrop
            )
            CropToolIconButton(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                label = "تدوير 90 درجة",
                enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                onClick = onRotatePage
            )
            CropToolIconButton(
                icon = Icons.Filled.Close,
                label = "إلغاء وإغلاق محرر القص",
                enabled = !state.cropApplying && !state.busy,
                onClick = onCancelCrop
            )

            GlassmorphicButton(
                onClick = if (state.cropDirty) onApplyCrop else onFinishCrop,
                enabled = !state.cropApplying && !state.busy &&
                    (!state.cropDirty || state.cropPreview != null),
                modifier = Modifier.weight(1f),
                height = PhotoUnifierHeight.buttonSecondary,
                shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                contentPadding = PaddingValues(horizontal = PhotoUnifierSpacing.xs, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = if (state.cropDirty) Icons.Filled.Check else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.xs)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        state.cropApplying -> "جارٍ الحفظ..."
                        state.cropDirty -> stringResource(R.string.action_crop)
                        else -> "إنهاء"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CropToolIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(PhotoUnifierIconSize.sm)
        )
    }
}
