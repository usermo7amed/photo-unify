package com.photounifier.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.photounifier.ui.components.GlassmorphicButton
import com.photounifier.ui.components.GlassmorphicOutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photounifier.PhotoUiState
import com.photounifier.ui.PhotoUnifierElevation
import com.photounifier.ui.PhotoUnifierHeight
import com.photounifier.ui.PhotoUnifierIconSize
import com.photounifier.ui.PhotoUnifierSpacing
import com.photounifier.ui.components.SelectionThumbnailsBar

/**
 * شاشة البداية المبسطة بنمط تطبيقات المسح الاحترافية (مثل CamScanner).
 * تدعم المسح بالكاميرا والاستيراد من المعرض وإدارة دفعة المستندات بنقاء ودون تشتيت.
 */
@Composable
fun HomeScreen(
    state: PhotoUiState,
    onPickImages: (Boolean) -> Unit,
    onCaptureDocument: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onPreviewSelect: (Int) -> Unit,
    onStartReview: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.md)
    ) {
        if (state.selected.isEmpty()) {
            // حالة الخلو - شاشة نظيفة وسريعة للمسح أو الاستيراد بلمسة واحدة
            EmptyScanDashboard(
                onCaptureDocument = onCaptureDocument,
                onPickImages = { onPickImages(false) },
                enabled = enabled
            )
        } else {
            // نمط دفعة المستندات - مستندات محددة جاهزة للمراجعة والقص
            ActiveBatchDashboard(
                state = state,
                onCaptureDocument = onCaptureDocument,
                onPickImages = onPickImages,
                onClearSelection = onClearSelection,
                onRemoveImage = onRemoveImage,
                onPreviewSelect = onPreviewSelect,
                onStartReview = onStartReview,
                enabled = enabled
            )
        }
    }
}

/**
 * واجهة البدء السريع عند عدم وجود مستندات مختارة
 */
@Composable
private fun EmptyScanDashboard(
    onCaptureDocument: () -> Unit,
    onPickImages: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.low)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PhotoUnifierSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.md)
        ) {
            Surface(
                modifier = Modifier.size(PhotoUnifierIconSize.hero),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
            ) {
                Text(
                    text = "ابدأ بمسح مستند",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "صوّر الورقة بالكاميرا أو اختر صورًا من المعرض.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                GlassmorphicButton(
                    onClick = onCaptureDocument,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    height = PhotoUnifierHeight.buttonPrimary
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.sm)
                    )
                    Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                    Text(
                        text = "مسح بالكاميرا",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                GlassmorphicOutlinedButton(
                    onClick = onPickImages,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    height = PhotoUnifierHeight.buttonSecondary
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs)
                    )
                    Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                    Text(
                        text = "استيراد من المعرض",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveBatchDashboard(
    state: PhotoUiState,
    onCaptureDocument: () -> Unit,
    onPickImages: (Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onPreviewSelect: (Int) -> Unit,
    onStartReview: () -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.md)
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
                    text = "الصور المحددة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.selected.size == 1) "صورة واحدة" else "${state.selected.size} صور",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = onClearSelection,
                enabled = enabled
            ) {
                Text("مسح الكل", style = MaterialTheme.typography.labelLarge)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.low)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PhotoUnifierSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
            ) {
                val preview = state.sourcePreview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.15f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (preview != null && !preview.isRecycled) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "معاينة الصفحة ${state.previewIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.Medium
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "جاري تحميل معاينة الصفحة...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SelectionThumbnailsBar(
                    selectedUris = state.selected,
                    thumbnails = state.selectionThumbnails,
                    currentIndex = state.previewIndex,
                    onSelectIndex = onPreviewSelect,
                    onRemoveUri = onRemoveImage,
                    onAddMore = { onPickImages(true) },
                    enabled = enabled
                )
            }
        }

        GlassmorphicButton(
            onClick = onStartReview,
            enabled = enabled && state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            height = PhotoUnifierHeight.buttonPrimary
        ) {
            Icon(
                imageVector = Icons.Filled.Crop,
                contentDescription = null,
                modifier = Modifier.size(PhotoUnifierIconSize.sm)
            )
            Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
            Text(
                text = "مراجعة القص والتحسين",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
        ) {
            GlassmorphicOutlinedButton(
                onClick = onCaptureDocument,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                height = PhotoUnifierHeight.buttonTertiary
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.xs)
                )
                Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xxs))
                Text("تصوير صفحة", style = MaterialTheme.typography.labelSmall)
            }

            GlassmorphicOutlinedButton(
                onClick = { onPickImages(true) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                height = PhotoUnifierHeight.buttonTertiary
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.xs)
                )
                Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xxs))
                Text("من المعرض", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
