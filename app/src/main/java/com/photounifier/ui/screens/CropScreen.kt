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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.photounifier.ui.components.PhotoUnifierButton
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
        elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.mid)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compactLayout = maxHeight < 620.dp || maxWidth < 360.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    if (compactLayout) PhotoUnifierSpacing.sm else PhotoUnifierSpacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (compactLayout) PhotoUnifierSpacing.xxs else PhotoUnifierSpacing.sm
                )
            ) {
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
                    Surface(
                        shape = RoundedCornerShape(PhotoUnifierRadius.md),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(if (compactLayout) 28.dp else 40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Crop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(
                                    if (compactLayout) PhotoUnifierIconSize.sm else PhotoUnifierIconSize.md
                                )
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.crop_title),
                            style = if (compactLayout) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (!compactLayout) {
                            Text(
                                text = stringResource(R.string.crop_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                ValuePill(
                    if (state.cropEdits.isNotEmpty()) stringResource(R.string.crop_page_modified_count, state.cropEdits.size)
                    else stringResource(R.string.crop_ready_review)
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
                        contentDescription = stringResource(R.string.crop_page_preview_description, state.previewIndex + 1),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.crop_preview_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.cropApplied && state.cropPreview != null) {
                CropResultPreview(state.cropPreview, compact = compactLayout)
            }

            PhotoUnifierButton(
                onClick = onOpenCrop,
                enabled = !state.busy && !state.cropApplying && state.selected.isNotEmpty() && openCvReady,
                modifier = Modifier.fillMaxWidth(),
                height = if (compactLayout) PhotoUnifierHeight.buttonSecondary else PhotoUnifierHeight.buttonPrimary,
                shape = RoundedCornerShape(PhotoUnifierRadius.md)
            ) {
                Icon(
                    imageVector = Icons.Filled.Crop,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.sm)
                )
                Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                Text(
                    text = if (state.cropEdits.isNotEmpty()) stringResource(R.string.crop_continue_review, state.cropEdits.size, state.selected.size)
                    else stringResource(R.string.action_open_crop_editor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            val hintText = when {
                state.cropApplying -> stringResource(R.string.crop_processing)
                state.selected.isEmpty() -> stringResource(R.string.crop_select_first)
                !openCvReady -> stringResource(R.string.crop_opencv_unavailable)
                state.cropEdits.isNotEmpty() -> stringResource(R.string.crop_edits_saved_summary, state.cropEdits.size, state.selected.size)
                else -> stringResource(R.string.crop_optional_hint)
            }

            Surface(
                shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (compactLayout) PhotoUnifierSpacing.xs else PhotoUnifierSpacing.sm,
                        vertical = if (compactLayout) PhotoUnifierSpacing.xxs else PhotoUnifierSpacing.xs
                    ),
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
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compactLayout) 2 else Int.MAX_VALUE,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
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
        val availableHeight = maxHeight
        val compactLayout = availableHeight < 620.dp
        // Keep a useful minimum editor area without forcing overflow on very short screens.
        val cropPreviewMinHeight = if (compactLayout) {
            (availableHeight * 0.18f).coerceIn(48.dp, 96.dp)
        } else {
            170.dp
        }
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
                    ValuePill(stringResource(R.string.page_counter, state.previewIndex + 1, state.selected.size))
                }
            }

            // محرر الزوايا هو العنصر الرئيسي، ويتمدد ليشغل المساحة المتاحة.
            if (state.cropSource != null && !state.cropSource.isRecycled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = cropPreviewMinHeight),
                    shape = RoundedCornerShape(PhotoUnifierRadius.lg),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                        .heightIn(min = cropPreviewMinHeight),
                    shape = RoundedCornerShape(PhotoUnifierRadius.lg),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.crop_page_preview_loading),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max = if (compactLayout) {
                                (availableHeight * 0.84f).coerceAtLeast(240.dp)
                            } else {
                                680.dp
                            }
                        ),
                    shape = RoundedCornerShape(PhotoUnifierRadius.lg),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
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
                                    contentDescription = stringResource(R.string.action_close_preview)
                                )
                            }
                        }

                        val preview = state.cropPreview
                        if (preview != null && !preview.isRecycled) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = stringResource(R.string.crop_preview_title),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        max = if (compactLayout) {
                                            (availableHeight * 0.45f).coerceAtLeast(140.dp)
                                        } else {
                                            560.dp
                                        }
                                    ),
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
    val thumbnailWidth = if (compact) 54.dp else 74.dp
    val thumbnailHeight = if (compact) 58.dp else 84.dp

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
                text = stringResource(R.string.document_pages),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(PhotoUnifierRadius.xs),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = stringResource(R.string.edited_pages_count, state.cropEdits.size, state.selected.size),
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
                                contentDescription = stringResource(R.string.page_number, index + 1),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(PhotoUnifierRadius.xs)),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Medium
                            )
                        } else {
                            Text(
                                stringResource(R.string.page_number, index + 1),
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
                                    contentDescription = stringResource(R.string.page_edited, index + 1),
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.page_number, index + 1),
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                label = stringResource(R.string.action_undo),
                enabled = state.cropUndoStack.isNotEmpty() && !state.cropApplying && !state.busy,
                onClick = onUndoCrop
            )
            CropToolIconButton(
                icon = Icons.Filled.RestartAlt,
                label = stringResource(R.string.action_reset),
                enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                onClick = onResetCrop
            )
            CropToolIconButton(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                label = stringResource(R.string.action_rotate_90),
                enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                onClick = onRotatePage
            )
            CropToolIconButton(
                icon = Icons.Filled.Close,
                label = stringResource(R.string.action_cancel_crop),
                enabled = !state.cropApplying && !state.busy,
                onClick = onCancelCrop
            )

            PhotoUnifierButton(
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
                        state.cropApplying -> stringResource(R.string.crop_saving)
                        state.cropDirty -> stringResource(R.string.action_crop)
                        else -> stringResource(R.string.action_finish)
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
