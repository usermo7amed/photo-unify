package com.photounifier.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photounifier.CropCornerEditor
import com.photounifier.PhotoUiState
import com.photounifier.ui.components.CropResultPreview
import com.photounifier.ui.components.StepHeading
import com.photounifier.ui.components.ValuePill

@Composable
fun CropReviewCard(
    state: PhotoUiState,
    openCvReady: Boolean,
    onOpenCrop: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "قص وتصحيح المنظور",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اكتشاف أوتوماتيكي لحدود الورقة وتصحيح الزوايا",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ValuePill(
                    if (state.cropEdits.isNotEmpty()) "تم تعديل ${state.cropEdits.size} صفحة"
                    else "جاهز للمراجعة"
                )
            }

            if (state.cropApplied && state.cropPreview != null) {
                CropResultPreview(state.cropPreview)
            }

            Button(
                onClick = onOpenCrop,
                enabled = !state.busy && !state.cropApplying && state.selected.isNotEmpty() && openCvReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (state.cropEdits.isNotEmpty()) "استكمال مراجعة الصفحات 📐" else "فتح محرر زوايا الورقة 📐",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            val hintText = when {
                state.cropApplying -> "جاري تجهيز القص وتصحيح المنظور بجودة فائقة..."
                state.selected.isEmpty() -> "اختر صورة أولاً لتتمكن من مراجعة حدودها."
                !openCvReady -> "مكتبة معالجة الصور (OpenCV) غير جاهزة حالياً."
                state.cropEdits.isNotEmpty() -> "تم حفظ تعديلات ${state.cropEdits.size} من ${state.selected.size} صفحة. اضغط «التالي» للمتابعة للتحسين."
                else -> "💡 خطوة اختيارية: يمكنك تخطيها بالضغط على «التالي» إذا كانت الصورة لا تحتاج إلى قص."
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = hintText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CropWorkspaceView(
    state: PhotoUiState,
    onMoveCropCorner: (Int, Float, Float) -> Unit,
    onSelectPage: (Int) -> Unit,
    onRotatePage: () -> Unit,
    onEnsurePageThumbnail: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("محرر المستند", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "اسحب الزوايا لتحديد الورقة، أو دوّر الصفحة قبل الحفظ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ValuePill("صفحة ${state.previewIndex + 1} / ${state.selected.size}")
            OutlinedButton(
                onClick = onRotatePage,
                enabled = !state.cropApplying && !state.busy && state.cropSource != null,
                shape = RoundedCornerShape(12.dp)
            ) { Text("تدوير 90°") }
        }

        if (state.cropSource != null && !state.cropSource.isRecycled) {
            CropCornerEditor(
                bitmap = state.cropSource,
                corners = state.cropCorners,
                onMoveCorner = onMoveCropCorner,
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 180.dp)
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 180.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("جاري تجهيز معاينة الصفحة...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        CropResultPreview(state.cropPreview)
        if (state.cropDirty) {
            Text(
                "فيه تعديلات لسه ما اتحفظتش. احفظ الصفحة قبل الانتقال لصفحة تانية أو إنهاء المراجعة.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        DocumentPageThumbnailStrip(
            state = state,
            onSelectPage = onSelectPage,
            onEnsureThumbnail = onEnsurePageThumbnail,
            enabled = !state.cropApplying && !state.busy && !state.cropDirty
        )
    }
}

@Composable
private fun DocumentPageThumbnailStrip(
    state: PhotoUiState,
    onSelectPage: (Int) -> Unit,
    onEnsureThumbnail: (Int) -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("صفحات المجموعة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "${state.cropEdits.size} صفحة معدلة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(state.selected, key = { _, uri -> uri.toString() }) { index, uri ->
                LaunchedEffect(uri) { onEnsureThumbnail(index) }
                val thumbnail = state.selectionThumbnails.firstOrNull { it.uri == uri }
                val isCurrent = index == state.previewIndex
                val isEdited = state.cropEdits.containsKey(uri)
                Column(
                    modifier = Modifier.width(72.dp).clickable(enabled = enabled) { onSelectPage(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 82.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnail != null && !thumbnail.bitmap.isRecycled) {
                            Image(
                                bitmap = thumbnail.bitmap.asImageBitmap(),
                                contentDescription = "صفحة ${index + 1}",
                                modifier = Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Medium
                            )
                        } else {
                            Text(
                                "صفحة\n${index + 1}",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isEdited) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(3.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    "✓",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        "صفحة ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun CropWorkspaceBottomBar(
    state: PhotoUiState,
    onUndoCrop: () -> Unit,
    onResetCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onApplyCrop: () -> Unit,
    onFinishCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUndoCrop,
                    enabled = state.cropUndoStack.isNotEmpty() && !state.cropApplying && !state.busy,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("تراجع") }

                OutlinedButton(
                    onClick = onResetCrop,
                    enabled = !state.cropApplying && !state.busy,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("إعادة ضبط الزوايا") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCancelCrop,
                    enabled = !state.cropApplying && !state.busy && !state.cropDirty,
                    modifier = Modifier.weight(0.9f).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("إغلاق") }

                Button(
                    onClick = onApplyCrop,
                    enabled = state.cropPreview != null && !state.cropApplying && !state.busy,
                    modifier = Modifier.weight(1.2f).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (state.cropApplying) "جاري الحفظ..." else if (state.cropDirty) "حفظ التعديلات" else "حفظ الصفحة",
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onFinishCrop,
                    enabled = !state.cropApplying && !state.busy && !state.cropDirty,
                    modifier = Modifier.weight(1.1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("إنهاء المراجعة") }
            }
        }
    }
}
