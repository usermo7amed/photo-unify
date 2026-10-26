package com.photounifier.ui.screens

import androidx.compose.ui.res.stringResource
import com.photounifier.R

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.photounifier.ui.PhotoUnifierSpacing
import com.photounifier.ui.components.SelectionThumbnailsBar

/**
 * شاشة البداية المبسطة بنمط تطبيقات المسح الاحترافية (مثل CamScanner).
 * تدعم المسح بالكاميرا والاستيراد من المعرض وإدارة دفعة المستندات بنقاء ودون تشتيت.
 */
@Composable
fun HomeScreen(
    state: PhotoUiState,
    onCaptureDocument: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onPreviewSelect: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactLayout = maxHeight < 620.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.md)
        ) {
            if (state.selected.isEmpty()) {
                // حالة خلو مختصرة تترك الإجراءات للشريط السفلي.
                EmptyScanDashboard()
            } else {
                // نمط دفعة المستندات - مستندات محددة جاهزة للمراجعة والقص
                ActiveBatchDashboard(
                    state = state,
                    onClearSelection = onClearSelection,
                    onRemoveImage = onRemoveImage,
                    onPreviewSelect = onPreviewSelect,
                    enabled = enabled,
                    compact = compactLayout
                )
            }
        }
    }
}

/**
 * Minimal empty state. Capture and gallery actions already live in the bottom bar,
 * so keep this area focused on the next step instead of duplicating controls.
 */
@Composable
private fun EmptyScanDashboard() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PhotoUnifierSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            modifier = Modifier.padding(top = PhotoUnifierSpacing.xxs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActiveBatchDashboard(
    state: PhotoUiState,
    onClearSelection: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onPreviewSelect: (Int) -> Unit,
    enabled: Boolean,
    compact: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
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
                    text = stringResource(R.string.selected_photos),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = onClearSelection,
                enabled = enabled
            ) {
                Text(stringResource(R.string.action_clear_all), style = MaterialTheme.typography.labelLarge)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = PhotoUnifierElevation.low)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PhotoUnifierSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                val preview = state.sourcePreview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (preview != null && !preview.isRecycled) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.crop_page_preview_description, state.previewIndex + 1),
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
                                text = stringResource(R.string.home_preview_loading),
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
                    enabled = enabled,
                    compact = compact
                )
            }
        }



    }
}
