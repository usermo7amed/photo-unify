package com.photounifier.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photounifier.BeforeAfterPreview
import com.photounifier.PhotoUiState
import com.photounifier.ui.components.PreviewNavigationControls
import com.photounifier.ui.components.SectionHeading
import com.photounifier.ui.components.StepHeading
import com.photounifier.ui.components.ValuePill
import kotlin.math.roundToInt

@Composable
fun AdjustPreviewScreen(
    state: PhotoUiState,
    openCvReady: Boolean,
    onPreviewSelect: (Int) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onContrastChange: (Int) -> Unit,
    onProcess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepHeading(
                number = 3,
                title = "معاينة وضبط الإضاءة والتباين",
                subtitle = "قارن الصورة الأصلية بالنسخة المحسّنة مباشرة، واضبط الإضاءة قبل الحفظ النهائي."
            )

            // Multiple image selector controls
            if (state.selected.size > 1) {
                PreviewNavigationControls(
                    selectedIndex = state.previewIndex,
                    totalCount = state.selected.size,
                    onIndexChange = onPreviewSelect,
                    enabled = !state.busy && !state.cropApplying
                )
            }

            // Interactive before & after comparison
            if (state.selected.isNotEmpty()) {
                SectionHeading(
                    title = "مقارنة حية تفاعلية",
                    subtitle = "اسحب الفاصل يميناً ويساراً مباشرة، أو المس مطولاً لرؤية الأصل."
                )

                if (state.sourcePreview != null) {
                    BeforeAfterPreview(
                        source = state.sourcePreview,
                        enhanced = state.resultPreview ?: state.enhancedPreview,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "جاري تجهيز معاينة الصورة ${state.previewIndex + 1}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Brightness Adjustment Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "السطوع",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    ValuePill(
                        when {
                            state.brightness > 0 -> "+${state.brightness}"
                            else -> "${state.brightness}"
                        }
                    )
                }
                Slider(
                    value = state.brightness.toFloat(),
                    onValueChange = { onBrightnessChange(it.roundToInt()) },
                    valueRange = -50f..50f,
                    enabled = !state.busy && state.sourcePreview != null
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("أغمق (-50)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("طبيعي (0)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("أفتح (+50)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Contrast Adjustment Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التباين",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    ValuePill(if (state.contrastPercent == 100) "100% طبيعي" else "${state.contrastPercent}%")
                }
                Slider(
                    value = state.contrastPercent.toFloat(),
                    onValueChange = { onContrastChange(it.roundToInt()) },
                    valueRange = 50f..200f,
                    enabled = !state.busy && state.sourcePreview != null
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("أقل (50%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("100% طبيعي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("أعلى (200%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Reset manual adjustments banner
            if (state.brightness != 0 || state.contrastPercent != 100) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "تعديلات مخصصة مفعّلة",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "سطوع ${if (state.brightness > 0) "+" else ""}${state.brightness} • تباين ${state.contrastPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onBrightnessChange(0)
                                onContrastChange(100)
                            },
                            enabled = !state.busy,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("إعادة ضبط")
                        }
                    }
                }
            }

            // Primary Process & Save Action Button
            Button(
                onClick = onProcess,
                enabled = !state.busy && openCvReady && state.selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (state.busy) "جاري التحسين والحفظ..." else "حسّن واحفظ المجموعة الآن 🚀",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!openCvReady) {
                Text(
                    text = "⚠️ مكتبة معالجة الصور (OpenCV) غير متوفرة حالياً على هذا الجهاز.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "📁 سيتم حفظ النسخ الجديدة في مجلد Pictures/PhotoUnifier دون المساس بالملفات الأصلية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
