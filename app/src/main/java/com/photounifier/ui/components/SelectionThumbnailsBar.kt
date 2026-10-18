package com.photounifier.ui.components

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photounifier.SelectionThumbnail

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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "الصور المختارة (${selectedUris.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "الصورة ${currentIndex + 1} من ${selectedUris.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(
                thumbnails,
                key = { _, item -> item.uri.toString() }
            ) { _, thumbnail ->
                val selectedIndex = selectedUris.indexOf(thumbnail.uri)
                val isCurrent = currentIndex == selectedIndex

                val scale by animateFloatAsState(
                    targetValue = if (isCurrent) 1.05f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
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
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isCurrent) 2.5.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = enabled && selectedIndex >= 0) {
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
                                    .padding(4.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    text = "✓",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Remove badge button
                    if (onRemoveUri != null && enabled && selectedUris.size > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(2.dp)
                                .size(20.dp)
                                .clickable { onRemoveUri(thumbnail.uri) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.70f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
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
                            .clip(RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
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
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = enabled) { onAddMore() },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "+",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onIndexChange(selectedIndex - 1) },
            enabled = enabled && selectedIndex > 0,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("الصورة السابقة")
        }

        Surface(
            modifier = Modifier
                .weight(0.9f)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${selectedIndex + 1} / $totalCount",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedButton(
            onClick = { onIndexChange(selectedIndex + 1) },
            enabled = enabled && selectedIndex < totalCount - 1,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("الصورة التالية")
        }
    }
}
