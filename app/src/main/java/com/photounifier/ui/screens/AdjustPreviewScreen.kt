package com.photounifier.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photounifier.BeforeAfterPreview
import com.photounifier.PhotoUiState
import com.photounifier.ui.components.PreviewNavigationControls
import com.photounifier.ui.components.ValuePill
import kotlin.math.roundToInt

/**
 * نموذج الفلتر الجاهز بنمط CamScanner
 */
private data class FilterPreset(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val brightness: Int,
    val contrast: Int
)

private val ScannerFilterPresets = listOf(
    FilterPreset(
        id = "magic",
        icon = "🪄",
        title = "تفتيح ذكي",
        subtitle = "تنقية الورق وإبراز الخط",
        brightness = 18,
        contrast = 135
    ),
    FilterPreset(
        id = "sharp",
        icon = "📄",
        title = "مستند ناصع",
        subtitle = "إزالة كاملة للظلال والاصفرار",
        brightness = 24,
        contrast = 155
    ),
    FilterPreset(
        id = "original",
        icon = "🖼️",
        title = "أصلي",
        subtitle = "ألوان وإضاءة المستند الطبيعية",
        brightness = 0,
        contrast = 100
    ),
    FilterPreset(
        id = "soft",
        icon = "💡",
        title = "إضاءة ناعمة",
        subtitle = "تفتيح خفيف متوازن",
        brightness = 10,
        contrast = 115
    ),
    FilterPreset(
        id = "bold",
        icon = "🌑",
        title = "نص بارز",
        subtitle = "للكتابة الباهتة والرصاص",
        brightness = -5,
        contrast = 160
    )
)

/**
 * شاشة المعاينة وضبط الفلاتر بنمط CamScanner الاحترافي:
 * - معاينة تفاعلية بملء الشاشة مع شريط مقارنة قبل/بعد بالسحب.
 * - شريط فلاتر سفلي أيقوني سريع بلمسة واحدة.
 * - لوحة ضبط يدوي دقيق قابلة للطي والفتح دون ازدحام.
 * - زر حفظ وتصدير واضح ومريح للإبهام.
 */
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
    var showFineTuneSliders by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // شريط رأس الشاشة والتنقل بين الصفحات
        AdjustHeader(
            selectedCount = state.selected.size,
            currentIndex = state.previewIndex,
            onIndexChange = onPreviewSelect,
            isCustomAdjusted = state.brightness != 0 || state.contrastPercent != 100,
            onResetAdjustments = {
                onBrightnessChange(0)
                onContrastChange(100)
            },
            enabled = !state.busy && !state.cropApplying
        )

        // بطاقة المعاينة المركزية التفاعلية
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // تلميح تفاعلي
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اسحب الفاصل للمقارنة الحية (قبل / بعد)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "لمس مطول للأصل 👆",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // محرك المقارنة التفاعلي
                if (state.sourcePreview != null && !state.sourcePreview.isRecycled) {
                    BeforeAfterPreview(
                        source = state.sourcePreview,
                        enhanced = state.resultPreview ?: state.enhancedPreview,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "جاري تحميل معاينة المستند...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // شريط فلاتر CamScanner السريعة (Filter Presets Strip)
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
                    text = "فلاتر المستند السريعة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = { showFineTuneSliders = !showFineTuneSliders },
                    enabled = !state.busy && state.sourcePreview != null
                ) {
                    Text(
                        text = if (showFineTuneSliders) "إخفاء الضبط اليدوي ▴" else "🎚️ ضبط يدوي دقيق ▾",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ScannerFilterPresets, key = { it.id }) { preset ->
                    val isSelected = state.brightness == preset.brightness &&
                            state.contrastPercent == preset.contrast

                    FilterPresetCard(
                        preset = preset,
                        isSelected = isSelected,
                        onClick = {
                            onBrightnessChange(preset.brightness)
                            onContrastChange(preset.contrast)
                        },
                        enabled = !state.busy && state.sourcePreview != null
                    )
                }
            }
        }

        // لوحة الضبط اليدوي الدقيق (تظهر وتختفي بسلاسة)
        AnimatedVisibility(
            visible = showFineTuneSliders,
            enter = expandVertically(animationSpec = tween(220)),
            exit = shrinkVertically(animationSpec = tween(220))
        ) {
            FineTuneSlidersCard(
                brightness = state.brightness,
                contrastPercent = state.contrastPercent,
                onBrightnessChange = onBrightnessChange,
                onContrastChange = onContrastChange,
                enabled = !state.busy && state.sourcePreview != null
            )
        }

        // زر الحفظ والمعالجة النهائي البارز
        Button(
            onClick = onProcess,
            enabled = !state.busy && openCvReady && state.selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (state.busy) "جاري المعالجة والحفظ..." else "حفظ المستندات الآن (${state.selected.size}) 🚀",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ملاحظة حفظ هادئة
        Text(
            text = "📁 تُحفظ النسخ الناتجة بجودتها الكاملة في Pictures/PhotoUnifier دون تعديل الأصل.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * شريط رأس الشاشة مع خيارات التنقل بين الصفحات وإعادة الضبط
 */
@Composable
private fun AdjustHeader(
    selectedCount: Int,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    isCustomAdjusted: Boolean,
    onResetAdjustments: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "الفلاتر والتحسين",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "اختر الفلتر المناسب لنوع المستند وجودته",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selectedCount > 1) {
            PreviewNavigationControls(
                selectedIndex = currentIndex,
                totalCount = selectedCount,
                onIndexChange = onIndexChange,
                enabled = enabled
            )
        } else if (isCustomAdjusted) {
            OutlinedButton(
                onClick = onResetAdjustments,
                enabled = enabled,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إعادة ضبط", fontSize = 12.sp)
            }
        }
    }
}

/**
 * بطاقة الفلتر السريع بنمط CamScanner
 */
@Composable
private fun FilterPresetCard(
    preset: FilterPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier
            .width(108.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isSelected) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.icon,
                    fontSize = 17.sp
                )
            }

            Text(
                text = preset.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * بطاقة الضبط اليدوي الدقيق (السطوع والتباين)
 */
@Composable
private fun FineTuneSlidersCard(
    brightness: Int,
    contrastPercent: Int,
    onBrightnessChange: (Int) -> Unit,
    onContrastChange: (Int) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // شريط ضبط السطوع
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "السطوع",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    ValuePill(if (brightness > 0) "+$brightness" else "$brightness")
                }
                Slider(
                    value = brightness.toFloat(),
                    onValueChange = { onBrightnessChange(it.roundToInt()) },
                    valueRange = -50f..50f,
                    enabled = enabled
                )
            }

            // شريط ضبط التباين
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التباين",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    ValuePill("$contrastPercent%")
                }
                Slider(
                    value = contrastPercent.toFloat(),
                    onValueChange = { onContrastChange(it.roundToInt()) },
                    valueRange = 50f..200f,
                    enabled = enabled
                )
            }
        }
    }
}
