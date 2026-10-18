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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photounifier.PhotoUiState
import com.photounifier.ui.components.StepHeading
import kotlin.math.roundToInt

@Composable
fun ResultScreen(
    state: PhotoUiState,
    onBackToAdjust: () -> Unit,
    onPickNewBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressMax = state.progressMax.coerceAtLeast(1)
    val progressFraction = (state.progress.toFloat() / progressMax).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        label = "saveProgress"
    )

    val statusTitle = when {
        state.busy -> "جاري تحسين وحفظ الصور على الجهاز..."
        state.savedCount > 0 && state.errors.isNotEmpty() -> "اكتملت المعالجة مع بعض الملاحظات"
        state.savedCount > 0 -> "تم حفظ جميع النسخ المحسّنة بنجاح 🎉"
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
                title = "حالة المعالجة والنتائج",
                subtitle = "تابع تقدم معالجة المجموعة، وتفاصيل الحفظ في الذاكرة."
            )

            // Status Banner
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

            // Progress Bar and Counter
            if (state.busy || state.progress > 0 || state.savedCount > 0 || state.errors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "تم حفظ ${state.savedCount} من أصل $progressMax صورة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(animatedProgress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (state.busy || state.progress > 0) {
                        LinearProgressIndicator(
                            progress = animatedProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                    }
                }
            }

            // Errors List Card if any
            if (state.errors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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

            // Post-processing action buttons
            if (!state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToAdjust,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("الرجوع للمعاينة")
                    }

                    Button(
                        onClick = onPickNewBatch,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("مجموعة جديدة", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "📁 تم حفظ الملفات في المجلد العام Pictures/PhotoUnifier. الصور الأصلية بقيت كما هي دون أي تعديل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
