package com.photounifier.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photounifier.PhotoUiState
import com.photounifier.ui.components.StepHeading
import kotlin.math.roundToInt

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
    val processMax = state.progressMax.coerceAtLeast(1)
    val processFraction = (state.progress.toFloat() / processMax).coerceIn(0f, 1f)
    val animatedProcessProgress by animateFloatAsState(
        targetValue = processFraction,
        label = "processingProgress"
    )
    val exportMax = state.exportProgressMax.coerceAtLeast(1)
    val exportFraction = (state.exportProgress.toFloat() / exportMax).coerceIn(0f, 1f)
    val animatedExportProgress by animateFloatAsState(
        targetValue = exportFraction,
        label = "exportProgress"
    )

    val statusTitle = when {
        state.busy -> "جاري معالجة الصور وتجهيزها للتصدير..."
        state.savedCount > 0 && state.errors.isNotEmpty() -> "انتهت المعالجة مع بعض الأخطاء"
        state.savedCount > 0 -> "المعالجة اكتملت — اختار صيغة التصدير"
        state.errors.isNotEmpty() -> "تعذّرت معالجة بعض الصور"
        else -> "جاهز لبدء المعالجة"
    }
    val statusContainer = when {
        state.busy -> MaterialTheme.colorScheme.secondaryContainer
        state.errors.isNotEmpty() -> MaterialTheme.colorScheme.errorContainer
        state.savedCount > 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusContent = when {
        state.busy -> MaterialTheme.colorScheme.onSecondaryContainer
        state.errors.isNotEmpty() -> MaterialTheme.colorScheme.onErrorContainer
        state.savedCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val selectedCount = state.processedImages.count { it.id in state.selectedExportIds }
    val allSelected = state.processedImages.isNotEmpty() &&
        selectedCount == state.processedImages.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StepHeading(
                number = 4,
                title = "الحفظ والتصدير",
                subtitle = "اختار الصور المطلوبة، وبعدها صدّرها كصور منفصلة أو كملف PDF."
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = statusContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusContent
                    )
                    Text(
                        text = state.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusContent
                    )
                }
            }

            if (state.busy || state.progress > 0 || state.savedCount > 0 || state.errors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "تم تجهيز ${state.savedCount} من أصل $processMax صورة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(animatedProcessProgress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.busy || state.progress > 0) {
                        LinearProgressIndicator(
                            progress = animatedProcessProgress,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "صور لم تكتمل معالجتها:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        state.errors.forEach { item ->
                            Text(
                                text = "• $item",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Text(
                text = "اختار الصور المراد تصديرها",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (state.processedImages.isEmpty()) {
                Text(
                    text = if (state.busy) {
                        "هتظهر الصور هنا أول ما تخلص معالجتها."
                    } else {
                        "مفيش صور جاهزة للتصدير حتى الآن. راجع حالة المعالجة والأخطاء بالأعلى."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المحدد: $selectedCount من ${state.processedImages.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { onSelectAllExportImages(!allSelected) },
                        enabled = !state.busy && !state.exportBusy
                    ) {
                        Text(if (allSelected) "إلغاء تحديد الكل" else "تحديد الكل")
                    }
                }

                state.processedImages.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = item.id in state.selectedExportIds,
                                onCheckedChange = { checked -> onToggleExportImage(item.id, checked) },
                                enabled = !state.busy && !state.exportBusy
                            )
                            Column(
                                modifier = Modifier.weight(1f).padding(vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "الصورة ${index + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportImages,
                        enabled = selectedCount > 0 && !state.busy && !state.exportBusy,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("تصدير كصور")
                    }
                    Button(
                        onClick = onExportPdf,
                        enabled = selectedCount > 0 && !state.busy && !state.exportBusy,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("تصدير PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.exportBusy || state.exportProgress > 0 || state.exportedCount > 0 || state.exportErrors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.exportErrors.isNotEmpty())
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (state.exportBusy) "جاري التصدير..." else "حالة التصدير",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.exportStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.exportBusy || state.exportProgress > 0) {
                            Text(
                                text = "التقدم: ${state.exportProgress.coerceAtMost(exportMax)} من $exportMax",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = animatedExportProgress,
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                        }
                        if (state.exportErrors.isNotEmpty()) {
                            Text(
                                text = "أخطاء التصدير:",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            state.exportErrors.forEach { message ->
                                Text(
                                    text = "• $message",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (!state.busy && !state.exportBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToAdjust,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("الرجوع للمعاينة")
                    }
                    Button(
                        onClick = onPickNewBatch,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("مجموعة جديدة", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "الصور الأصلية بتفضل كما هي. الصور المصدّرة بتتحفظ في Pictures/PhotoUnifier، وملفات PDF في Documents/PhotoUnifier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
