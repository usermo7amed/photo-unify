package com.photounifier.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.photounifier.ui.PhotoUnifierSpacing
import com.photounifier.ui.components.CropResultPreview
import com.photounifier.ui.components.ValuePill

@Composable
fun CropReviewCard(
    state: PhotoUiState,
    openCvReady: Boolean,
    onOpenCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(PhotoUnifierSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)) {
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
                ValuePill(
                    if (state.cropEdits.isNotEmpty()) "تم تعديل ${state.cropEdits.size} صفحة"
                    else "جاهز للمراجعة"
                )
            }

            if (state.cropApplied && state.cropPreview != null) {
                CropResultPreview(state.cropPreview)
            }

            Button(
                onClick = onOpenCrop,
                enabled = !state.busy && !state.cropApplying && state.selected.isNotEmpty() && openCvReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (state.cropEdits.isNotEmpty()) "استكمال مراجعة الصفحات" else "فتح محرر زوايا الورقة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val hintText = when {
                state.cropApplying -> "جاري تجهيز القص وتصحيح المنظور بجودة فائقة..."
                state.selected.isEmpty() -> "اختر صورة أولاً لتتمكن من مراجعة حدودها."
                !openCvReady -> "مكتبة معالجة الصور (OpenCV) غير جاهزة حالياً."
                state.cropEdits.isNotEmpty() -> "تم حفظ تعديلات ${state.cropEdits.size} من ${state.selected.size} صفحة. اضغط «التالي» للمتابعة للتحسين."
                else -> "خطوة اختيارية: يمكنك الانتقال للخطوة التالية إذا كانت الصورة لا تحتاج إلى قص."
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = hintText,
                    modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                ) {
                    Text(
                        text = "ضبط زوايا المستند",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اسحب الزوايا لضبط حدود الورقة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                ValuePill("صفحة ${state.previewIndex + 1} / ${state.selected.size}")
            }

            if (state.cropSource != null && !state.cropSource.isRecycled) {
                CropCornerEditor(
                    bitmap = state.cropSource,
                    corners = state.cropCorners,
                    onMoveCorner = onMoveCropCorner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = if (compactLayout) 108.dp else 160.dp)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = if (compactLayout) 108.dp else 160.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "جاري تجهيز معاينة الصفحة...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            CropPreviewStrip(bitmap = state.cropPreview, compact = compactLayout)

            if (state.cropDirty) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "تعديلات غير محفوظة — احفظها أو ألغِ التعديل.",
                        modifier = Modifier.padding(
                            horizontal = PhotoUnifierSpacing.xs,
                            vertical = PhotoUnifierSpacing.xxs
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

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

@Composable
private fun CropPreviewStrip(
    bitmap: android.graphics.Bitmap?,
    compact: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
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
                        width = if (compact) 34.dp else 42.dp,
                        height = if (compact) 44.dp else 54.dp
                    )
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surface),
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
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (bitmap != null && !bitmap.isRecycled) {
                        "تتحدث المعاينة مع تعديل الزوايا."
                    } else {
                        "ستظهر النتيجة بعد تجهيز المعاينة."
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

@Composable
private fun DocumentPageThumbnailStrip(
    state: PhotoUiState,
    onSelectPage: (Int) -> Unit,
    onEnsureThumbnail: (Int) -> Unit,
    enabled: Boolean,
    compact: Boolean
) {
    val thumbnailWidth = if (compact) 58.dp else 72.dp
    val thumbnailHeight = if (compact) 64.dp else 82.dp
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("صفحات المجموعة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "${state.cropEdits.size} صفحة معدلة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(state.selected, key = { _, uri -> uri.toString() }) { index, uri ->
                LaunchedEffect(uri) { onEnsureThumbnail(index) }
                val thumbnail = state.selectionThumbnails.firstOrNull { it.uri == uri }
                val isCurrent = index == state.previewIndex
                val isEdited = state.cropEdits.containsKey(uri)
                Column(
                    modifier = Modifier.width(thumbnailWidth).clickable(enabled = enabled, role = Role.Button) { onSelectPage(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = thumbnailWidth, height = thumbnailHeight)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnail != null && !thumbnail.bitmap.isRecycled) {
                            Image(
                                bitmap = thumbnail.bitmap.asImageBitmap(),
                                contentDescription = "صفحة ${index + 1}",
                                modifier = Modifier.fillMaxSize().padding(2.dp).clip(MaterialTheme.shapes.extraSmall),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Medium
                            )
                        } else {
                            Text(
                                "صفحة\n${index + 1}",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isEdited) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(3.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    "✓",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        "صفحة ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

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
            .padding(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xxs)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                MaterialTheme.shapes.large
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PhotoUnifierSpacing.xs, vertical = PhotoUnifierSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
            ) {
                CropToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Undo,
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
                    icon = Icons.Filled.RotateRight,
                    label = "تدوير 90°",
                    enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                    onClick = onRotatePage
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCancelCrop,
                    enabled = !state.cropApplying && !state.busy,
                    modifier = Modifier.weight(0.8f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "إلغاء",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Button(
                    onClick = onApplyCrop,
                    enabled = state.cropPreview != null && state.cropDirty && !state.cropApplying && !state.busy,
                    modifier = Modifier.weight(1.15f).height(44.dp),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (state.cropApplying) "جارٍ الحفظ..." else "حفظ الصفحة",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                OutlinedButton(
                    onClick = onFinishCrop,
                    enabled = !state.cropApplying && !state.busy && !state.cropDirty,
                    modifier = Modifier.weight(1.15f).height(44.dp),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "إنهاء المراجعة",
                        style = MaterialTheme.typography.labelSmall,
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
