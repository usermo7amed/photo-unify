package com.photounifier.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photounifier.PhotoUiState
import com.photounifier.ui.components.SelectionThumbnailsBar
import com.photounifier.ui.PhotoUnifierSpacing
import com.photounifier.ui.components.ValuePill

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
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PhotoUnifierSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.lg)
        ) {
            // أيقونة الماسح الاحترافية مع تدرج زمردي أنيق
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ماسح وموحِّد المستندات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "اكتشاف تلقائي للحدود • تصحيح المنظور • إزالة الظلال والتحسين",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // ميزات سريعة مدمجة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickFeatureBadge(Icons.Filled.Bolt, "كشف ذكي")
                Spacer(modifier = Modifier.width(6.dp))
                QuickFeatureBadge(Icons.Filled.Crop, "قص المنظور")
                Spacer(modifier = Modifier.width(6.dp))
                QuickFeatureBadge(Icons.Filled.AutoAwesome, "تحسين فوري")
            }

            // خيارات الإدخال الرئيسية بنمط CamScanner (كاميرا + معرض)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
            ) {
                Button(
                    onClick = onCaptureDocument,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = "مسح ضوئي بالكاميرا",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onPickImages,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = "استيراد صور من المعرض",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // إشعار الخصوصية
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.sm, vertical = PhotoUnifierSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "المعالجة بالكامل محلياً على جهازك؛ أمان تام لمستنداتك.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * واجهة دفعة المستندات عند اختيار صفحة أو أكثر
 */
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
    // شريط إحصائيات المستند السريع
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
        ) {
            Text(
                text = "المستندات الجاهزة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ValuePill(if (state.selected.size == 1) "صفحة واحدة" else "${state.selected.size} صفحات")
        }

        OutlinedButton(
            onClick = onClearSelection,
            enabled = enabled,
            modifier = Modifier.height(38.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "مسح الكل",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    // بطاقة المعاينة المركزية للصفحة النشطة (Hero Document Card)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PhotoUnifierSpacing.md),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val preview = state.sourcePreview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
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

                // شارة رقم الصفحة في الزاوية
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PhotoUnifierSpacing.xs),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = "صفحة ${state.previewIndex + 1} من ${state.selected.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // شريط الصفحات المصغرة لاختيار أو حذف أي صفحة
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

    // أزرار التحكم بالدفعة
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
    ) {
        // الزر البارز لبدء القص والتحسين
        Button(
            onClick = onStartReview,
            enabled = enabled && state.selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                Text(
                    text = "بدء القص والتحسين ➔",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // أزرار إضافة صفحات أخرى (كاميرا أو معرض)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
        ) {
            OutlinedButton(
                onClick = onCaptureDocument,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "+ تصوير صفحة",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = { onPickImages(true) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "+ إضافة من المعرض",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QuickFeatureBadge(icon: ImageVector, label: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.xs, vertical = PhotoUnifierSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
