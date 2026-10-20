package com.photounifier.ui.components

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photounifier.SelectionThumbnail
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.photounifier.ui.PhotoUnifierDuration
import com.photounifier.ui.PhotoUnifierHeight
import com.photounifier.ui.PhotoUnifierElevation
import com.photounifier.ui.PhotoUnifierIconSize
import com.photounifier.ui.PhotoUnifierSpacing

@Composable
fun SelectionThumbnailsBar(
    selectedUris: List<Uri>,
    thumbnails: List<SelectionThumbnail>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onRemoveUri: ((Uri) -> Unit)? = null,
    onAddMore: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (selectedUris.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "الصفحات",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "الصورة ${currentIndex + 1} من ${selectedUris.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (thumbnails.isEmpty() && selectedUris.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(selectedUris.size.coerceAtMost(6)) {
                    ShimmerThumbnailPlaceholder()
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(
                    thumbnails,
                    key = { _, item -> item.uri.toString() }
                ) { _, thumbnail ->
                val selectedIndex = selectedUris.indexOf(thumbnail.uri)
                val isCurrent = currentIndex == selectedIndex

                val scale by animateFloatAsState(
                    targetValue = if (isCurrent) 1.08f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "thumbScale"
                )

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                width = if (isCurrent) 2.5.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .clickable(enabled = enabled && selectedIndex >= 0, role = Role.Button) {
                                if (selectedIndex >= 0) onSelectIndex(selectedIndex)
                            }
                    ) {
                        Image(
                            bitmap = thumbnail.bitmap.asImageBitmap(),
                            contentDescription = "صورة ${selectedIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium
                        )

                        // Index badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                        ) {
                            Text(
                                text = "${selectedIndex + 1}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        // Selected indicator
                        if (isCurrent) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(PhotoUnifierSpacing.xxs),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "الصفحة الحالية",
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }

                    // Remove badge button
                    if (onRemoveUri != null && enabled) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(1.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.76f)
                        ) {
                            IconButton(
                                onClick = { onRemoveUri(thumbnail.uri) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "إزالة الصورة ${selectedIndex + 1}",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // More count pill if thumbnails exceed memory limit
            if (selectedUris.size > thumbnails.size) {
                item {
                    val remaining = selectedUris.size - thumbnails.size
                    Surface(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+$remaining",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick add more button
            if (onAddMore != null) {
                item {
                    Surface(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                MaterialTheme.shapes.medium
                            )
                            .clickable(enabled = enabled, role = Role.Button) { onAddMore() },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = "إضافة صورة جديدة",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "إضافة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun PreviewNavigationControls(
    selectedIndex: Int,
    totalCount: Int,
    onIndexChange: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (totalCount <= 1) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onIndexChange(selectedIndex - 1) },
            enabled = enabled && selectedIndex > 0,
            modifier = Modifier
                .weight(1f)
                .height(PhotoUnifierHeight.buttonTertiary),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.xs)
                )
                Text(
                    text = "السابقة",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(0.85f)
                .height(PhotoUnifierHeight.buttonTertiary),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = PhotoUnifierElevation.low
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${selectedIndex + 1} / $totalCount",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        OutlinedButton(
            onClick = { onIndexChange(selectedIndex + 1) },
            enabled = enabled && selectedIndex < totalCount - 1,
            modifier = Modifier
                .weight(1f)
                .height(PhotoUnifierHeight.buttonTertiary),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "التالية",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.xs)
                )
            }
        }
    }
}
