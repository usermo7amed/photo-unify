package com.photounifier.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
            StepHeading(
                number = 2,
                title = "راجع حدود الصورة (قص المستند)",
                subtitle = "إذا كانت الصورة ورقة أو مستند، اضبط الزوايا الأربع لتصحيح منظورها تلقائياً."
            )

            if (state.cropApplied && state.cropPreview != null) {
                CropResultPreview(state.cropPreview)
            }

            Button(
                onClick = onOpenCrop,
                enabled = !state.busy && !state.cropApplying && state.selected.isNotEmpty() && openCvReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (state.cropApplied) "إعادة مراجعة زوايا الورقة" else "اكتشاف الورقة وضبط الزوايا الأربع",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            val hintText = when {
                state.cropApplying -> "جاري تجهيز القص وتصحيح المنظور بجودة فائقة..."
                state.selected.isEmpty() -> "اختر صورة أولاً لتتمكن من مراجعة حدودها."
                !openCvReady -> "مكتبة معالجة الصور (OpenCV) غير جاهزة حالياً."
                state.cropApplied -> "تم تطبيق القص على الصورة الأولى بنجاح! يمكنك المتابعة إلى خطوة الضبط."
                else -> "هذه الخطوة اختيارية؛ إذا كانت الصورة طبيعية أو بورتريه وليست مستنداً، يمكنك تخطيها بالضغط على «التالي»."
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = hintText,
                    modifier = Modifier.padding(12.dp),
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "محرر زوايا المستند",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اسحب أي زاوية لتطابق حواف الورقة مع العدسة المكبرة الحية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ValuePill("الصورة الأولى")
        }

        CropCornerEditor(
            bitmap = state.cropSource,
            corners = state.cropCorners,
            onMoveCorner = onMoveCropCorner,
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        )

        CropResultPreview(state.cropPreview)
    }
}

@Composable
fun CropWorkspaceBottomBar(
    state: PhotoUiState,
    onUndoCrop: () -> Unit,
    onResetCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onApplyCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUndoCrop,
                    enabled = state.cropUndoStack.isNotEmpty() && !state.cropApplying && !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("تراجع") }

                OutlinedButton(
                    onClick = onResetCrop,
                    enabled = !state.cropApplying && !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("إعادة ضبط") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelCrop,
                    enabled = !state.cropApplying && !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("إلغاء") }

                Button(
                    onClick = onApplyCrop,
                    enabled = state.cropPreview != null && !state.cropApplying && !state.busy,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (state.cropApplying) "جاري التطبيق..." else "تطبيق القص ➔",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
