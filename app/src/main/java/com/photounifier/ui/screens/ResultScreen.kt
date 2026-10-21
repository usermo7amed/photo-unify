package com.photounifier.ui.screens

import androidx.compose.ui.res.stringResource
import com.photounifier.R

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import com.photounifier.ui.PhotoUnifierGlass
import com.photounifier.ui.components.GlassmorphicButton
import com.photounifier.ui.components.GlassmorphicOutlinedButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photounifier.PhotoUiState
import com.photounifier.ui.PhotoUnifierDuration
import com.photounifier.ui.PhotoUnifierElevation
import com.photounifier.ui.PhotoUnifierHeight
import com.photounifier.ui.PhotoUnifierIconSize
import com.photounifier.ui.PhotoUnifierRadius
import com.photounifier.ui.PhotoUnifierSpacing
import kotlin.math.roundToInt

/**
 * شاشة النتيجة والتصدير بنمط CamScanner الاحترافي:
 * - بطاقة إنجاز احتفالية تبرز نجاح تحسين المستند مع إحصائيات سريعة.
 * - خيارات تصدير فورية بنقرة واحدة كـ PDF أو صور عالية الدقة بألوان مميزة.
 * - قائمة اختيار الصفحات للتصدير بدقة وأمان.
 * - أزرار سريعة لمسح مستند جديد أو الرجوع لضبط الفلاتر.
 */
@Composable
fun ResultScreen(
    state: PhotoUiState,
    onBackToAdjust: () -> Unit,
    onPickNewBatch: () -> Unit,
    onToggleExportImage: (String, Boolean) -> Unit,
    onSelectAllExportImages: (Boolean) -> Unit,
    onExportImages: () -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressMax = state.progressMax.coerceAtLeast(1)
    val progressFraction = (state.progress.toFloat() / progressMax).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(PhotoUnifierDuration.normal),
        label = "saveProgress"
    )

    val exportMax = state.exportProgressMax.coerceAtLeast(1)
    val exportFraction = (state.exportProgress.toFloat() / exportMax).coerceIn(0f, 1f)
    val animatedExportProgress by animateFloatAsState(
        targetValue = exportFraction,
        animationSpec = tween(PhotoUnifierDuration.normal),
        label = "exportProgress"
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
    ) {
        if (state.busy) {
            // واجهة المعالجة الحية أثناء التحسين
            ProcessingStateCard(
                progress = animatedProgress,
                current = state.progress,
                total = progressMax,
                status = state.status
            )
        } else {
            // واجهة النجاح والتصدير بنمط CamScanner
            SuccessExportCard(
                state = state,
                animatedExportProgress = animatedExportProgress,
                onExportPdf = onExportPdf,
                onExportImages = onExportImages,
                onToggleExportImage = onToggleExportImage,
                onSelectAllExportImages = onSelectAllExportImages,
                onBackToAdjust = onBackToAdjust,
                onPickNewBatch = onPickNewBatch
            )
        }
    }
}

/**
 * بطاقة عرض التقدم أثناء المعالجة
 */
@Composable
private fun ProcessingStateCard(
    progress: Float,
    current: Int,
    total: Int,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PhotoUnifierRadius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.mid),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PhotoUnifierSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.lg)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.5.dp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.status_processing_documents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.status_document_progress, current, total),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PhotoUnifierHeight.progressBar)
                        .clip(RoundedCornerShape(PhotoUnifierRadius.xs))
                )
            }
        }
    }
}

/**
 * بطاقة الإنجاز والتصدير بعد اكتمال التحسين
 */
@Composable
private fun SuccessExportCard(
    state: PhotoUiState,
    animatedExportProgress: Float,
    onExportPdf: () -> Unit,
    onExportImages: () -> Unit,
    onToggleExportImage: (String, Boolean) -> Unit,
    onSelectAllExportImages: (Boolean) -> Unit,
    onBackToAdjust: () -> Unit,
    onPickNewBatch: () -> Unit
) {
    val isSuccess = state.savedCount > 0
    val finalPreview = state.resultPreview ?: state.enhancedPreview ?: state.sourcePreview

    // أنيميشن احتفالي للرمز الترحيبي عند ظهور الشاشة
    var hasAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasAnimated = true }
    val scaleAnim by animateFloatAsState(
        targetValue = if (hasAnimated) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "celebrateScale"
    )

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(PhotoUnifierRadius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.mid),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PhotoUnifierSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
        ) {
            // شارة النجاح الاحتفالية
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(scaleAnim)
                    .clip(CircleShape)
                    .background(
                        if (isSuccess) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSuccess) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Text(
                        text = if (isSuccess) stringResource(R.string.result_success_title) else stringResource(R.string.result_failure_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (isSuccess) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(PhotoUnifierIconSize.md)
                        )
                    }
                }
                Text(
                    text = if (isSuccess) stringResource(R.string.result_success_description)
                    else state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // شريط إحصائيات الإنجاز السريع
            if (isSuccess) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatSummaryChip(label = stringResource(R.string.result_pages), value = stringResource(R.string.selected_count, state.savedCount))
                    StatSummaryChip(label = stringResource(R.string.result_quality), value = stringResource(R.string.result_quality_original))
                    StatSummaryChip(label = stringResource(R.string.result_processing), value = stringResource(R.string.result_processing_local))
                }
            }

            // معاينة سريعة للمستند النهائي
            if (finalPreview != null && !finalPreview.isRecycled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .weight(1.15f)
                        .heightIn(min = 100.dp)
                        .clip(RoundedCornerShape(PhotoUnifierRadius.lg))
                        .background(Color(0xFF14171C))
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            RoundedCornerShape(PhotoUnifierRadius.lg)
                        )
                ) {
                    Image(
                        bitmap = finalPreview.asImageBitmap(),
                        contentDescription = stringResource(R.string.final_document),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = PhotoUnifierSpacing.xs),
                        shape = RoundedCornerShape(PhotoUnifierRadius.xs),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = stringResource(R.string.full_quality_preview),
                            modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.xs, vertical = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // التفاصيل الطويلة وقائمة التصدير فقط هي القابلة للتمرير؛ تبقى المعاينة ظاهرة.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.md)
            ) {
            // قائمة اختيار العناصر المراد تصديرها
            if (state.processedImages.isNotEmpty()) {
                val allExportImagesSelected = state.processedImages.all {
                    it.id in state.selectedExportIds
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PhotoUnifierRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PhotoUnifierSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.images_ready_export),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.selected_count_of_total, state.processedImages.count { it.id in state.selectedExportIds }, state.processedImages.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { onSelectAllExportImages(!allExportImagesSelected) },
                                enabled = !state.exportBusy
                            ) {
                                Text(
                                    text = if (allExportImagesSelected) stringResource(R.string.action_deselect) else stringResource(R.string.action_select_all),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        state.processedImages.forEach { image ->
                            val imageExportDescription = stringResource(R.string.include_image_export, image.displayName)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = image.id in state.selectedExportIds,
                                    onCheckedChange = { selected ->
                                        onToggleExportImage(image.id, selected)
                                    },
                                    enabled = !state.exportBusy,
                                    modifier = Modifier.semantics {
                                        contentDescription = imageExportDescription
                                    }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = image.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (image.id in state.selectedExportIds)
                                            stringResource(R.string.selected_for_export) else stringResource(R.string.not_selected),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // أزرار التصدير بنمط CamScanner (PDF + صور)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.export_options_share),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
                ) {
                    // تصدير PDF
                    GlassmorphicButton(
                        onClick = onExportPdf,
                        enabled = !state.exportBusy && isSuccess && state.selectedExportIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        height = PhotoUnifierHeight.buttonSecondary,
                        shape = RoundedCornerShape(PhotoUnifierRadius.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(PhotoUnifierIconSize.xs)
                        )
                        Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                        Text(stringResource(R.string.action_export_pdf), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }

                    // تصدير صور
                    GlassmorphicButton(
                        onClick = onExportImages,
                        enabled = !state.exportBusy && isSuccess && state.selectedExportIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        height = PhotoUnifierHeight.buttonSecondary,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = PhotoUnifierGlass.secondaryAlpha),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(PhotoUnifierRadius.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(PhotoUnifierIconSize.xs)
                        )
                        Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                        Text(stringResource(R.string.action_save_images), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }

                // حالة التصدير
                if (state.exportBusy || state.exportProgress > 0 ||
                    state.exportErrors.isNotEmpty() || state.exportStatus != stringResource(R.string.status_ready_export)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.padding(PhotoUnifierSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                        ) {
                            Text(
                                text = state.exportStatus,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.exportBusy) {
                                LinearProgressIndicator(
                                    progress = { animatedExportProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(PhotoUnifierHeight.progressBar)
                                        .clip(RoundedCornerShape(PhotoUnifierRadius.xs))
                                )
                            } else {
                                Text(
                                    text = if (state.exportErrors.isEmpty())
                                        stringResource(R.string.export_success) else stringResource(R.string.export_finished_with_notes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.exportErrors.isEmpty())
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // تفاصيل الإخفاقات إن وُجدت
            if (state.exportErrors.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PhotoUnifierRadius.md),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(PhotoUnifierSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                    ) {
                        Text(
                            text = stringResource(R.string.export_some_failed),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        state.exportErrors.forEachIndexed { index, message ->
                            Text(
                                text = "${index + 1}. $message",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // مسارات الحفظ المباشرة
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PhotoUnifierRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(PhotoUnifierSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.sm),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.export_locations),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // أزرار المتابعة والعمليات اللاحقة
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                GlassmorphicButton(
                    onClick = onPickNewBatch,
                    enabled = !state.exportBusy,
                    modifier = Modifier.fillMaxWidth(),
                    height = PhotoUnifierHeight.buttonSecondary,
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = PhotoUnifierGlass.primaryAlpha),
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(PhotoUnifierRadius.md)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs)
                    )
                    Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                    Text(
                        text = stringResource(R.string.action_new_document),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                GlassmorphicOutlinedButton(
                    onClick = onBackToAdjust,
                    enabled = !state.exportBusy,
                    modifier = Modifier.fillMaxWidth(),
                    height = PhotoUnifierHeight.buttonTertiary,
                    shape = RoundedCornerShape(PhotoUnifierRadius.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs)
                    )
                    Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                    Text(
                        text = stringResource(R.string.action_return_filters),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            }
        }
    }
}

/**
 * شارة ملخص الإحصائيات (عدد الصفحات، الدقة، المعالجة)
 */
@Composable
private fun StatSummaryChip(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(PhotoUnifierRadius.sm),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
