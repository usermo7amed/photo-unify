package com.photounifier.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.photounifier.ui.PhotoUnifierGlass
import com.photounifier.ui.components.GlassmorphicButton
import com.photounifier.ui.components.GlassmorphicOutlinedButton
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photounifier.BeforeAfterPreview
import com.photounifier.PhotoUiState
import com.photounifier.ui.components.PreviewNavigationControls
import com.photounifier.ui.components.ValuePill
import com.photounifier.ui.PhotoUnifierDuration
import com.photounifier.ui.PhotoUnifierElevation
import com.photounifier.ui.PhotoUnifierHeight
import com.photounifier.ui.PhotoUnifierIconSize
import com.photounifier.ui.PhotoUnifierRadius
import com.photounifier.ui.PhotoUnifierSpacing
import kotlin.math.roundToInt

/**
 * نموذج الفلتر الجاهز بنمط CamScanner مع بيانات الدقة والعلامة الذكية
 */
private data class FilterPreset(
    val id: String,
    val badge: String,
    val title: String,
    val subtitle: String,
    val brightness: Int,
    val contrast: Int,
    val isAi: Boolean = false
)

private val ScannerFilterPresets = listOf(
    FilterPreset(
        id = "magic",
        badge = "AI",
        title = "تفتيح ذكي",
        subtitle = "تنقية الورق وإبراز الخط",
        brightness = 18,
        contrast = 135,
        isAi = true
    ),
    FilterPreset(
        id = "sharp",
        badge = "DOC",
        title = "مستند ناصع",
        subtitle = "إزالة تامة للظلال والاصفرار",
        brightness = 24,
        contrast = 155
    ),
    FilterPreset(
        id = "bold",
        badge = "B/W",
        title = "نص بارز",
        subtitle = "للكتابة الباهتة والرصاص",
        brightness = -5,
        contrast = 160
    ),
    FilterPreset(
        id = "soft",
        badge = "LGT",
        title = "إضاءة ناعمة",
        subtitle = "تفتيح خفيف متوازن وطبيعي",
        brightness = 10,
        contrast = 115
    ),
    FilterPreset(
        id = "color",
        badge = "CLR",
        title = "ألوان حيوية",
        subtitle = "إبراز الخرائط والرسومات الملونة",
        brightness = 8,
        contrast = 125
    ),
    FilterPreset(
        id = "original",
        badge = "RAW",
        title = "محايد",
        subtitle = "بدون ضبط إضافي فوق التحسين",
        brightness = 0,
        contrast = 100
    )
)

/**
 * شاشة المعاينة وضبط الفلاتر بنمط CamScanner الاحترافي:
 * - معاينة تفاعلية بملء الشاشة مع شريط مقارنة قبل/بعد بالسحب وإيماءات التكبير.
 * - شريط فلاتر سفلي أيقوني سريع بلمسة واحدة مع شارات ذكية.
 * - لوحة ضبط يدوي دقيق مع خيارات سريعة وأزرار تصفير فورية.
 * - زر حفظ وتصدير واضح ومريح للإبهام مع شارة الإجمالي.
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

    // التحقق من وجود ضبط يدوي خارج الفلاتر
    val activePreset = ScannerFilterPresets.firstOrNull {
        it.brightness == state.brightness && it.contrast == state.contrastPercent
    }
    val isCustomAdjusted = activePreset == null && (state.brightness != 0 || state.contrastPercent != 100)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
    ) {
        // شريط رأس الشاشة والتنقل بين الصفحات
        AdjustHeader(
            selectedCount = state.selected.size,
            currentIndex = state.previewIndex,
            onIndexChange = onPreviewSelect,
            activePresetTitle = activePreset?.title,
            isCustomAdjusted = isCustomAdjusted || (activePreset != null && activePreset.id != "original"),
            onResetAdjustments = {
                onBrightnessChange(0)
                onContrastChange(100)
            },
            enabled = !state.busy && !state.cropApplying
        )

        // بطاقة المعاينة المركزية التفاعلية
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
                    .padding(PhotoUnifierSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
            ) {
                // شريط التوجيه والأدوات التفاعلية للمعاينة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = null,
                            modifier = Modifier.size(PhotoUnifierIconSize.sm),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "اسحب الفاصل للمقارنة الحية",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(PhotoUnifierRadius.xs),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "اضغط مطولاً لمعاينة الأصل",
                            modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.xs, vertical = PhotoUnifierSpacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            .height(260.dp)
                            .clip(RoundedCornerShape(PhotoUnifierRadius.lg))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.sm)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "جاري تحضير المعاينة عالية الدقة...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
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
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
                ) {
                    Text(
                        text = "فلاتر المستند الذكية",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (activePreset != null && activePreset.id != "original") {
                        Surface(
                            shape = RoundedCornerShape(PhotoUnifierRadius.xs),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = activePreset.title,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                GlassmorphicButton(
                    onClick = { showFineTuneSliders = !showFineTuneSliders },
                    enabled = !state.busy && state.sourcePreview != null,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = PhotoUnifierGlass.secondaryAlpha),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(PhotoUnifierRadius.sm),
                    height = PhotoUnifierHeight.buttonTertiary,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = PhotoUnifierSpacing.sm,
                        vertical = PhotoUnifierSpacing.xxs
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showFineTuneSliders) "إخفاء الضبط" else "ضبط يدوي دقيق",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs),
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

        // لوحة الضبط اليدوي الدقيق (تظهر وتختفي بسلاسة كلوحة منبثقة)
        AnimatedVisibility(
            visible = showFineTuneSliders,
            enter = expandVertically(animationSpec = tween(PhotoUnifierDuration.normal)) + fadeIn(animationSpec = tween(PhotoUnifierDuration.normal)),
            exit = shrinkVertically(animationSpec = tween(PhotoUnifierDuration.normal)) + fadeOut(animationSpec = tween(PhotoUnifierDuration.fast))
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
        GlassmorphicButton(
            onClick = onProcess,
            enabled = !state.busy && openCvReady && state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            height = PhotoUnifierHeight.buttonPrimary,
            shape = RoundedCornerShape(PhotoUnifierRadius.md)
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                Text(
                    text = "جاري المعالجة والحفظ...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(PhotoUnifierIconSize.sm)
                )
                Spacer(modifier = Modifier.width(PhotoUnifierSpacing.xs))
                Text(
                    text = "حفظ وتصدير المستندات (${state.selected.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ملاحظة حفظ هادئة وموثوقة
        Text(
            text = "تُحفظ النسخ المحسنة بدقة كاملة في Pictures/PhotoUnifier دون تعديل الملفات الأصلية.",
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
    activePresetTitle: String?,
    isCustomAdjusted: Boolean,
    onResetAdjustments: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "الفلاتر والتحسين",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (activePresetTitle != null) {
                    Surface(
                        shape = RoundedCornerShape(PhotoUnifierRadius.xs),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = activePresetTitle,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(
                text = "اختر الفلتر المناسب لنوع المستند وجودته",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCount > 1) {
                PreviewNavigationControls(
                    selectedIndex = currentIndex,
                    totalCount = selectedCount,
                    onIndexChange = onIndexChange,
                    enabled = enabled
                )
            }
            if (isCustomAdjusted) {
                GlassmorphicOutlinedButton(
                    onClick = onResetAdjustments,
                    enabled = enabled,
                    height = PhotoUnifierHeight.buttonSmall,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = PhotoUnifierSpacing.xs),
                    shape = RoundedCornerShape(PhotoUnifierRadius.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(PhotoUnifierIconSize.xs),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "إعادة ضبط",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * بطاقة الفلتر السريع بنمط CamScanner مع شارات الذكاء الاصطناعي والتنسيق العصري
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
            .width(128.dp)
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                contentDescription = "${preset.title}. ${preset.subtitle}"
                selected = isSelected
            },
        shape = RoundedCornerShape(PhotoUnifierRadius.lg),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shadowElevation = if (isSelected) PhotoUnifierElevation.mid else PhotoUnifierElevation.low
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // شارة AI في الركن العلوي إذا كان الفلتر ذكيًا
            if (preset.isAi) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = PhotoUnifierRadius.xs),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "AI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = PhotoUnifierSpacing.xs, vertical = PhotoUnifierSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(PhotoUnifierIconSize.sm),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = preset.badge,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * بطاقة الضبط اليدوي الدقيق (السطوع والتباين) مع أزرار تصفير وقيم سريعة
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
        shape = RoundedCornerShape(PhotoUnifierRadius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PhotoUnifierSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.md)
        ) {
            // شريط ضبط السطوع
            Column(verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brightness6,
                            contentDescription = null,
                            modifier = Modifier.size(PhotoUnifierIconSize.sm),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "السطوع",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (brightness != 0) {
                            TextButton(
                                onClick = { onBrightnessChange(0) },
                                enabled = enabled,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("تصفير", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    ValuePill(if (brightness > 0) "+$brightness" else "$brightness")
                }

                Slider(
                    value = brightness.toFloat(),
                    onValueChange = { onBrightnessChange(it.roundToInt()) },
                    valueRange = -50f..50f,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "ضبط السطوع: $brightness"
                    }
                )

                // خيارات سريعة للسطوع
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(-20 to "-20", 0 to "0 (محايد)", 15 to "+15", 30 to "+30").forEach { (valStep, label) ->
                        val isCurr = brightness == valStep
                        Surface(
                            modifier = Modifier
                                .heightIn(min = PhotoUnifierHeight.buttonSmall)
                                .clickable(enabled = enabled, role = Role.RadioButton) { onBrightnessChange(valStep) }
                                .semantics { selected = isCurr },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isCurr) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            border = BorderStroke(
                                1.dp,
                                if (isCurr) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isCurr) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isCurr) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // شريط ضبط التباين
            Column(verticalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PhotoUnifierSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contrast,
                            contentDescription = null,
                            modifier = Modifier.size(PhotoUnifierIconSize.sm),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "التباين",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (contrastPercent != 100) {
                            TextButton(
                                onClick = { onContrastChange(100) },
                                enabled = enabled,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("تصفير", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    ValuePill("$contrastPercent%")
                }

                Slider(
                    value = contrastPercent.toFloat(),
                    onValueChange = { onContrastChange(it.roundToInt()) },
                    valueRange = 50f..200f,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "ضبط التباين: $contrastPercent%"
                    }
                )

                // خيارات سريعة للتباين
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(80 to "80%", 100 to "100%", 135 to "135%", 160 to "160%").forEach { (valStep, label) ->
                        val isCurr = contrastPercent == valStep
                        Surface(
                            modifier = Modifier
                                .heightIn(min = PhotoUnifierHeight.buttonSmall)
                                .clickable(enabled = enabled, role = Role.RadioButton) { onContrastChange(valStep) }
                                .semantics { selected = isCurr },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isCurr) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            border = BorderStroke(
                                1.dp,
                                if (isCurr) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isCurr) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isCurr) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
