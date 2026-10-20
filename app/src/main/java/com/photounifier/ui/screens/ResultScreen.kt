package com.photounifier.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photounifier.PhotoUiState
import kotlin.math.roundToInt

/**
 * شاشة النتيجة والتصدير بنمط CamScanner الاحترافي:
 * - بطاقة إنجاز احتفالية تبرز نجاح تحسين المستند.
 * - خيارات تصدير فورية بنقرة واحدة كـ PDF أو صور عالية الدقة.
 * - أزرار سريعة لمسح مستند جديد أو تعديل المجموعة.
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
        label = "saveProgress"
    )

    val exportMax = state.exportProgressMax.coerceAtLeast(1)
    val exportFraction = (state.exportProgress.toFloat() / exportMax).coerceIn(0f, 1f)
    val animatedExportProgress by animateFloatAsState(
        targetValue = exportFraction,
        label = "exportProgress"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                total = progressMax,
                animatedExportProgress = animatedExportProgress,
                exportMax = exportMax,
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(54.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "جاري تحسين وحفظ المستندات...",
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المستند $current من $total",
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
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
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
    total: Int,
    animatedExportProgress: Float,
    exportMax: Int,
    onExportPdf: () -> Unit,
    onExportImages: () -> Unit,
    onToggleExportImage: (String, Boolean) -> Unit,
    onSelectAllExportImages: (Boolean) -> Unit,
    onBackToAdjust: () -> Unit,
    onPickNewBatch: () -> Unit
) {
    val isSuccess = state.savedCount > 0
    val finalPreview = state.resultPreview ?: state.enhancedPreview ?: state.sourcePreview

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // شارة النجاح
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSuccess) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSuccess) "✓" else "⚠️",
                    fontSize = 32.sp,
                    color = if (isSuccess) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isSuccess) "تم تحسين المستندات بنجاح" else "تعذّر حفظ المستندات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isSuccess) "جاهزة للتصدير كملف PDF أو حفظ كصور عالية الدقة."
                    else state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // معاينة سريعة للمستند النهائي
            if (finalPreview != null && !finalPreview.isRecycled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .aspectRatio(1.25f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF14171C))
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = finalPreview.asImageBitmap(),
                        contentDescription = "المستند النهائي",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.70f)
                    ) {
                        Text(
                            text = "جودة كاملة ✓",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // اختَر العناصر المراد تصديرها. الاحتفاظ بالفاشلة محددة يجعل إعادة المحاولة واضحة وآمنة.
            if (state.processedImages.isNotEmpty()) {
                val allExportImagesSelected = state.processedImages.all {
                    it.id in state.selectedExportIds
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    text = "الصور الجاهزة للتصدير",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${state.processedImages.count { it.id in state.selectedExportIds }} من ${state.processedImages.size} صور محددة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { onSelectAllExportImages(!allExportImagesSelected) },
                                enabled = !state.exportBusy
                            ) {
                                Text(if (allExportImagesSelected) "إلغاء تحديد الكل" else "تحديد الكل")
                            }
                        }

                        state.processedImages.forEach { image ->
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
                                        contentDescription = "تضمين ${image.displayName} في التصدير"
                                    }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 6.dp),
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
                                            "محددة للتصدير" else "غير محددة",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "خيارات التصدير السريعة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // تصدير PDF
                    Button(
                        onClick = onExportPdf,
                        enabled = !state.exportBusy && isSuccess && state.selectedExportIds.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("مستند PDF", fontWeight = FontWeight.Bold)
                        }
                    }

                    // تصدير صور
                    OutlinedButton(
                        onClick = onExportImages,
                        enabled = !state.exportBusy && isSuccess && state.selectedExportIds.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("JPG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("حفظ كصور", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // حالة التصدير: يظل وصف النتيجة ظاهرًا، بينما يظهر الشريط فقط أثناء العمل.
                if (state.exportBusy || state.exportProgress > 0 ||
                    state.exportErrors.isNotEmpty() || state.exportStatus != "جاهز للتصدير"
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = state.exportStatus,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.exportBusy) {
                                LinearProgressIndicator(
                                    progress = animatedExportProgress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            } else {
                                Text(
                                    text = if (state.exportErrors.isEmpty())
                                        "اكتمل التصدير." else "انتهى التصدير مع إخفاقات؛ تقدر تعيد المحاولة.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.exportErrors.isEmpty())
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // اعرض تفاصيل كل إخفاق بدل الاكتفاء برسالة حالة عامة؛ تظل النتائج الأصلية متاحة لإعادة التصدير.
            if (state.exportErrors.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "تعذّر تصدير بعض العناصر",
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
                        Text(
                            text = "يمكنك المحاولة مرة أخرى؛ الصور المعالجة ما زالت محفوظة داخل التطبيق.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // مسارات الحفظ
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "الصور في Pictures/PhotoUnifier • ملفات PDF في Documents/PhotoUnifier",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // أزرار المتابعة وإنشاء مستند جديد
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickNewBatch,
                    enabled = !state.exportBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = "+ مسح / استيراد مستند جديد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onBackToAdjust,
                    enabled = !state.exportBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "الرجوع لتعديل الفلاتر",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
