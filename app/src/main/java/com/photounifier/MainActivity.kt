package com.photounifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import com.photounifier.ui.PhotoUnifierTheme
import org.opencv.android.OpenCVLoader
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()
    private var openCvReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCvReady = OpenCVLoader.initLocal()
        setContent {
            PhotoUnifierTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    val state by viewModel.state.observeAsState(PhotoUiState())
                    var appendToSelection by remember { mutableStateOf(false) }
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia()
                    ) { uris ->
                        if (uris.isNotEmpty()) {
                            val chosen = if (appendToSelection) {
                                (state.selected + uris).distinct()
                            } else {
                                uris.distinct()
                            }
                            viewModel.selectImages(chosen)
                        }
                    }
                    PhotoUnifierScreen(
                        state = state,
                        openCvReady = openCvReady,
                        onPickImages = { append ->
                            appendToSelection = append
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearSelection = { viewModel.selectImages(emptyList()) },
                        onPreviewSelect = viewModel::showPreview,
                        onProcess = { viewModel.process(openCvReady) },
                        onBrightnessChange = viewModel::setBrightness,
                        onContrastChange = viewModel::setContrast,
                        onOpenCrop = { viewModel.openCropEditor(openCvReady) },
                        onMoveCropCorner = viewModel::moveCropCorner,
                        onUndoCrop = viewModel::undoCrop,
                        onResetCrop = viewModel::resetCrop,
                        onCancelCrop = viewModel::cancelCrop,
                        onApplyCrop = viewModel::applyCrop
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoUnifierScreen(
    state: PhotoUiState,
    openCvReady: Boolean,
    onPickImages: (Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onPreviewSelect: (Int) -> Unit,
    onProcess: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onContrastChange: (Int) -> Unit,
    onOpenCrop: () -> Unit,
    onMoveCropCorner: (Int, Float, Float) -> Unit,
    onUndoCrop: () -> Unit,
    onResetCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onApplyCrop: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    val stepTitles = listOf("اختيار الصور", "قص الصورة", "الإضاءة", "المعاينة والحفظ", "النتيجة")
    val isCropWorkspace = currentStep == 1 && state.cropOpen

    // Keep the home screen visible after selection so users can review or add more images.
    LaunchedEffect(state.selected.isEmpty()) {
        if (state.selected.isEmpty() && currentStep > 0 && !state.busy) currentStep = 0
    }
    LaunchedEffect(currentStep, state.previewIndex) {
        // Manual crop is intentionally scoped to the first selected image.
        if (currentStep == 1 && state.previewIndex != 0 && !state.busy) onPreviewSelect(0)
    }
    LaunchedEffect(state.cropApplied) {
        if (state.cropApplied && currentStep == 1) currentStep = 2
    }
    LaunchedEffect(state.busy, state.status, state.resultPreview, state.enhancedPreview) {
        if (state.busy) {
            currentStep = 4
        } else if (currentStep == 4 && (state.resultPreview != null || state.enhancedPreview != null)) {
            currentStep = 4
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
        TopAppBar(
            title = {
                Column {
                    Text("مُوحِّد الصور", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "اختار • حسّن • احفظ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        )
        },
        bottomBar = {
            if (isCropWorkspace) {
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                            RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onUndoCrop,
                                enabled = state.cropUndoStack.isNotEmpty() && !state.cropApplying && !state.busy,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("تراجع") }
                            OutlinedButton(
                                onClick = onResetCrop,
                                enabled = !state.cropApplying && !state.busy,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("إعادة ضبط") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancelCrop,
                                enabled = !state.cropApplying && !state.busy,
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) { Text("إلغاء") }
                            Button(
                                onClick = onApplyCrop,
                                enabled = state.cropPreview != null && !state.cropApplying && !state.busy,
                                modifier = Modifier.weight(1.25f).height(50.dp)
                            ) { Text(if (state.cropApplying) "جاري التطبيق..." else "تطبيق القص") }
                        }
                    }
                }
            } else if (currentStep in 1..2) Surface(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                        RoundedCornerShape(22.dp)
                    ),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { currentStep = (currentStep - 1).coerceAtLeast(0) },
                        enabled = currentStep > 0 && !state.busy && !state.cropApplying,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("السابق") }
                    Button(
                        onClick = { currentStep = (currentStep + 1).coerceAtMost(stepTitles.lastIndex) },
                        enabled = currentStep < stepTitles.lastIndex &&
                            (currentStep != 0 || state.selected.isNotEmpty()) &&
                            !(currentStep == 1 && state.cropOpen) && !state.busy && !state.cropApplying,
                        modifier = Modifier.weight(1.4f).height(48.dp)
                    ) {
                        Text(
                            when (currentStep) {
                                0 -> if (state.selected.isEmpty()) "ابدأ المراجعة" else "مراجعة ${state.selected.size} صور"
                                1 -> "التالي: الإضاءة"
                                2 -> "معاينة الحفظ"
                                3 -> "عرض حالة المعالجة"
                                else -> "اكتملت الخطوات"
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .then(
                    if (isCropWorkspace) {
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    } else {
                        Modifier.verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(if (isCropWorkspace) 8.dp else 12.dp)
        ) {
            if (isCropWorkspace) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "اضبط زوايا الورقة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "اسحب أي ركن، والمعاينة هتتحدّث مباشرة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ValuePill("الصورة الأولى")
                }
                CropCornerEditor(
                    state.cropSource,
                    state.cropCorners,
                    onMoveCropCorner,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                CropResultPreview(state.cropPreview)
            }
            if (!isCropWorkspace && currentStep != 0) StepProgress(currentStep = currentStep, titles = stepTitles, selectedCount = state.selected.size)
            if (!isCropWorkspace && currentStep == 0) {
                HomeSelectionCard(
                    state = state,
                    onPickImages = onPickImages,
                    onClearSelection = onClearSelection,
                    onPreviewSelect = onPreviewSelect,
                    onPreviewSwipe = { direction ->
                        if (state.selected.isNotEmpty() && !state.busy) {
                            onPreviewSelect((state.previewIndex + direction).coerceIn(0, state.selected.lastIndex))
                        }
                    },
                    onStartReview = { currentStep = 1 },
                    enabled = !state.busy
                )
            }

            if (!isCropWorkspace && currentStep == 1) Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepHeading(2, "راجع حدود الصورة", "لو الصورة مستند أو ورقة، ظبّط الزوايا قبل التحسين.")
                    OutlinedButton(
                        onClick = onOpenCrop,
                        enabled = !state.busy && !state.cropApplying && state.selected.isNotEmpty() && openCvReady,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (state.cropApplied) "مراجعة القص مرة تانية" else "اكتشاف الورقة وضبط الزوايا")
                    }
                    Text(
                        when {
                            state.cropApplying -> "جاري تجهيز القص بجودة عالية..."
                            state.selected.isEmpty() -> "اختار صورة الأول علشان تقدر تراجع حدودها."
                            !openCvReady -> "ميزة القص محتاجة مكتبة معالجة الصور، وهي مش جاهزة حاليًا."
                            state.cropApplied -> "تم تطبيق القص على أول صورة. تقدر تكمّل للتحسين."
                            else -> "الخطوة دي اختيارية؛ لو الصورة مش مستند، تقدر تتخطاها."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isCropWorkspace && currentStep == 2) Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StepHeading(
                        3,
                        "اضبط الإضاءة والتباين",
                        "عدّل الصورة براحتك وشوف الفرق قبل ما تبدأ المعالجة والحفظ."
                    )
                    if (state.selected.size > 1) {
                        PreviewSelectionControls(
                            state = state,
                            onPreviewSelect = onPreviewSelect,
                            enabled = !state.busy && !state.cropApplying
                        )
                    }

                    if (state.selected.isNotEmpty()) {
                        SectionHeading(
                            "معاينة مباشرة",
                            "قارن الأصل بالنتيجة المعدّلة أثناء ضبط القيم."
                        )
                        if (state.sourcePreview != null) {
                            BeforeAfterPreview(
                                source = state.sourcePreview,
                                enhanced = state.resultPreview ?: state.enhancedPreview,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(190.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "جاري تجهيز معاينة الصورة ${state.previewIndex + 1}...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            "المعاينة بتتحدّث مع تغييرات السطوع والتباين. استخدم المقارنة للتأكد إن التفاصيل والألوان لسه طبيعية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "اختار صورة الأول علشان تظهر المعاينة وتقدر تظبط الإضاءة والتباين.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "السطوع",
                                modifier = Modifier.weight(1f),
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
                            Text("أغمق", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("طبيعي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("أفتح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "التباين",
                                modifier = Modifier.weight(1f),
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
                            Text("أقل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("100% طبيعي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("أعلى", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    if (state.brightness == 0 && state.contrastPercent == 100) "الوضع الطبيعي"
                                    else "تعديلات يدوية مفعّلة",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "سطوع ${if (state.brightness > 0) "+" else ""}${state.brightness} · تباين ${state.contrastPercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onBrightnessChange(0)
                                    onContrastChange(100)
                                },
                                enabled = !state.busy && (state.brightness != 0 || state.contrastPercent != 100)
                            ) {
                                Text("إعادة ضبط")
                            }
                        }
                    }

                    Text(
                        "التحسين الذكي الحالي هيفضل شغال زي ما هو، ومفيش تحويل تلقائي للصور الملونة لأبيض وأسود. الأصل مش بيتغيّر؛ الحفظ بيعمل نسخة جديدة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isCropWorkspace && currentStep == 3) Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepHeading(
                        4,
                        "راجع النتيجة واحفظها",
                        "اتأكد من شكل الصورة، وبعدها احفظ نسخة محسّنة من غير ما تغيّر الأصل."
                    )
                    if (state.selected.size > 1) {
                        PreviewSelectionControls(
                            state = state,
                            onPreviewSelect = onPreviewSelect,
                            enabled = !state.busy && !state.cropApplying
                        )
                    }
                    if (state.selected.isNotEmpty()) {
                        BeforeAfterPreview(
                            source = state.sourcePreview,
                            enhanced = state.resultPreview ?: state.enhancedPreview,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (state.cropApplied)
                                "المعاينة تبدأ من الصورة بعد القص، ثم تعرض تأثير السطوع والتباين. اسحب الفاصل وافحص التفاصيل بالتكبير قبل الحفظ."
                            else if (state.resultPreview == null && state.enhancedPreview == null)
                                "حرّك شريط المقارنة لمراجعة الأصل. النتيجة تتحدث مع تغييرات السطوع والتباين، ويمكنك التكبير والسحب لفحص التفاصيل."
                            else "اسحب شريط المقارنة يمينًا ويسارًا. استخدم إصبعيك للتكبير والتحريك أو أزرار التكبير أسفل الصورة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "بعد اختيار الصور، هتظهر معاينة المصدر والنتيجة هنا.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!openCvReady) {
                        Text(
                            "مكتبة OpenCV مش جاهزة؛ مش هتقدر تبدأ المعالجة حاليًا.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = 2 },
                            enabled = !state.busy && !state.cropApplying,
                            modifier = Modifier.weight(0.8f).height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("السابق")
                        }
                        Button(
                            onClick = { onProcess() },
                            enabled = !state.busy && openCvReady && state.selected.isNotEmpty(),
                            modifier = Modifier.weight(1.3f).height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (state.busy) "جاري الحفظ..." else "حسّن واحفظ")
                        }
                    }
                }
            }

            if (!isCropWorkspace && currentStep == 4) {
                val progressMax = state.progressMax.coerceAtLeast(1)
                val progressFraction = (state.progress.toFloat() / progressMax).coerceIn(0f, 1f)
                val statusTitle = when {
                    state.busy -> "جاري تحسين الصور وحفظ النسخ..."
                    state.savedCount > 0 && state.errors.isNotEmpty() -> "المعالجة خلصت، وفيه صور ما نجحتش"
                    state.savedCount > 0 -> "اتحفظت النسخ المحسّنة بنجاح"
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StepHeading(
                            5,
                            "حالة المعالجة والحفظ",
                            "تابع الصور اللي اتعالجت، وأي أخطاء، ومكان النسخ الجديدة."
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = statusContainer
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    statusTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusContent
                                )
                                Text(
                                    state.status,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = statusContent
                                )
                            }
                        }

                        if (state.busy || state.progress > 0 || state.savedCount > 0 || state.errors.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "تم حفظ ${state.savedCount} من ${progressMax} صورة",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${(progressFraction * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (state.busy || state.progress > 0) {
                                LinearProgressIndicator(
                                    progress = progressFraction,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (state.errors.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "صور لم تكتمل معالجتها",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    state.errors.forEach { item ->
                                        Text(
                                            "• $item",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (!state.busy && state.savedCount == 0 && state.errors.isEmpty()) {
                            OutlinedButton(
                                onClick = { currentStep = 3 },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("الرجوع لمعاينة الصور وبدء الحفظ")
                            }
                        }

                        Text(
                            "النسخ الجديدة بتتحفظ في Pictures/PhotoUnifier. الصور الأصلية مش بتتعدّل أو تتمسح.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun CropResultPreview(bitmap: android.graphics.Bitmap?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(width = 62.dp, height = 76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF16191D)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "معاينة ناتج القص",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    Text(
                        "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "معاينة ناتج القص",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (bitmap != null && !bitmap.isRecycled)
                        "راجع استقامة الورقة قبل تطبيق القص."
                    else
                        "هنا هتشوف شكل الورقة بعد تصحيح المنظور.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewSelectionControls(
    state: PhotoUiState,
    onPreviewSelect: (Int) -> Unit,
    enabled: Boolean
) {
    if (state.selected.size <= 1) return
    val currentIndex = state.previewIndex.coerceIn(0, state.selected.lastIndex)
    Row(
        modifier = Modifier.fillMaxWidth().pointerInput(state.selected, currentIndex, enabled) {
            var dragDistance = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    if (enabled) {
                        dragDistance += dragAmount
                        change.consume()
                    }
                },
                onDragEnd = {
                    if (enabled && abs(dragDistance) >= 44.dp.toPx()) {
                        val direction = if (dragDistance < 0f) 1 else -1
                        val nextIndex = currentIndex + direction
                        if (nextIndex in state.selected.indices) onPreviewSelect(nextIndex)
                    }
                    dragDistance = 0f
                },
                onDragCancel = { dragDistance = 0f }
            )
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onPreviewSelect(currentIndex - 1) },
            enabled = enabled && currentIndex > 0,
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Text("الصورة السابقة")
        }
        Text(
            "الصورة ${currentIndex + 1} من ${state.selected.size}",
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedButton(
            onClick = { onPreviewSelect(currentIndex + 1) },
            enabled = enabled && currentIndex < state.selected.lastIndex,
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Text("الصورة التالية")
        }
    }
}

@Composable
private fun HomeSelectionCard(
    state: PhotoUiState,
    onPickImages: (Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onPreviewSelect: (Int) -> Unit,
    onPreviewSwipe: (Int) -> Unit,
    onStartReview: () -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "PU",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            "خلّي صورك أوضح، بسهولة",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "اختار صورك، راجع التحسين، واحفظ نسخة جديدة من غير ما يتغيّر الأصل.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeStepChip("١", "اختيار", Modifier.weight(1f))
                    HomeStepChip("٢", "تحسين", Modifier.weight(1f))
                    HomeStepChip("٣", "حفظ", Modifier.weight(1f))
                }

                Button(
                    onClick = { onPickImages(state.selected.isNotEmpty()) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (state.selected.isEmpty()) "اختيار الصور من المعرض"
                        else "إضافة صور من المعرض",
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "تقدر تختار أكتر من صورة مرة واحدة. الصور الأصلية بتفضل زي ما هي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (state.selected.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "هتعمل إيه بعد الاختيار؟",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HomeTip("١", "قص عند الحاجة", "راجع حدود الورق وعدّل الزوايا لو الصورة مستند.")
                    HomeTip("٢", "تحسين مناسب للصورة", "الإضاءة والتفاصيل بتتظبط حسب طبيعة الصورة مع الحفاظ على ألوانها.")
                    HomeTip("٣", "معاينة وحفظ", "راجع النتيجة قبل حفظ نسخة محسّنة في مجلد منفصل.")
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().pointerInput(state.selected, state.previewIndex, enabled) {
                            var dragDistance = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    if (enabled && state.selected.size > 1) {
                                        dragDistance += dragAmount
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    if (enabled && abs(dragDistance) >= 44.dp.toPx()) {
                                        onPreviewSwipe(if (dragDistance < 0f) 1 else -1)
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f }
                            )
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val preview = state.sourcePreview
                        if (preview != null && !preview.isRecycled) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "معاينة الصورة رقم ${state.previewIndex + 1}",
                                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Medium
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(88.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("صورة", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "صورك جاهزة للمراجعة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (state.selected.size == 1) "تم اختيار صورة واحدة"
                                else "تم اختيار ${state.selected.size} صور",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (preview == null) "جاري تجهيز معاينة الصورة ${state.previewIndex + 1}…"
                                else "المعاينة الحالية للصورة ${state.previewIndex + 1} من ${state.selected.size}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ValuePill(if (state.selected.size == 1) "صورة واحدة" else "${state.selected.size} صور")
                        OutlinedButton(
                            onClick = onClearSelection,
                            enabled = enabled,
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("مسح الاختيار")
                        }
                    }
                    if (state.selectionThumbnails.isNotEmpty()) {
                        val nextUnrepresentedIndex = state.selected.indexOfFirst { selectedUri ->
                            state.selectionThumbnails.none { it.uri == selectedUri }
                        }
                        Text(
                            "معاينة سريعة للصور المختارة",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(
                                state.selectionThumbnails,
                                key = { _, thumbnail -> thumbnail.uri.toString() }
                            ) { _, thumbnail ->
                                val selectedIndex = state.selected.indexOf(thumbnail.uri)
                                val isCurrentPreview = state.previewIndex == selectedIndex
                                val thumbnailScale by animateFloatAsState(
                                    targetValue = if (isCurrentPreview) 1.045f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "selectionThumbnailScale"
                                )
                                Box(
                                    modifier = Modifier.size(72.dp)
                                        .graphicsLayer {
                                            scaleX = thumbnailScale
                                            scaleY = thumbnailScale
                                        }
                                        .clip(RoundedCornerShape(13.dp))
                                        .border(
                                            width = if (isCurrentPreview) 2.dp else 1.dp,
                                            color = if (isCurrentPreview) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(13.dp)
                                        )
                                        .clickable(enabled = enabled && selectedIndex >= 0) {
                                            onPreviewSelect(selectedIndex)
                                        }
                                ) {
                                    Image(
                                        bitmap = thumbnail.bitmap.asImageBitmap(),
                                        contentDescription = "عرض الصورة المختارة رقم ${selectedIndex + 1}",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)),
                                        contentScale = ContentScale.Crop,
                                        filterQuality = FilterQuality.Medium
                                    )
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            "${selectedIndex + 1}",
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (isCurrentPreview) {
                                        Surface(
                                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondary
                                        ) {
                                            Text(
                                                "✓",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                color = MaterialTheme.colorScheme.onSecondary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.selected.size > state.selectionThumbnails.size) {
                                item {
                                    Surface(
                                        modifier = Modifier.size(72.dp)
                                            .clickable(enabled = enabled && nextUnrepresentedIndex >= 0) {
                                                if (nextUnrepresentedIndex >= 0) {
                                                    onPreviewSelect(nextUnrepresentedIndex)
                                                }
                                            },
                                        shape = RoundedCornerShape(13.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "+${state.selected.size - state.selectionThumbnails.size}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (state.selected.isNotEmpty()) {
                        Text(
                            "جاري تجهيز معاينات صغيرة للمختارات...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        "اضغط أي صورة مصغّرة لمعاينتها، أو اسحب صف المعاينة يمينًا وشمالًا للتنقل بين الصور.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onStartReview,
                        enabled = enabled && state.selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("ابدأ مراجعة الصور", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStepChip(number: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(25.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        number,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HomeTip(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepProgress(currentStep: Int, titles: List<String>, selectedCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "مسار المعالجة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "الخطوة ${currentStep + 1} من ${titles.size} · ${titles[currentStep]}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ValuePill(if (selectedCount == 0) "ابدأ من هنا" else "${selectedCount} صورة")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                titles.indices.forEach { index ->
                    val stepSize by animateDpAsState(
                        targetValue = if (index == currentStep) 32.dp else 26.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "progressStepSize"
                    )
                    val stepContainer by animateColorAsState(
                        targetValue = when {
                            index < currentStep -> MaterialTheme.colorScheme.primaryContainer
                            index == currentStep -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = tween(durationMillis = 240),
                        label = "progressStepColor"
                    )
                    val stepTextColor by animateColorAsState(
                        targetValue = when {
                            index < currentStep -> MaterialTheme.colorScheme.onPrimaryContainer
                            index == currentStep -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 240),
                        label = "progressStepTextColor"
                    )
                    Surface(
                        modifier = Modifier.size(stepSize),
                        shape = CircleShape,
                        color = stepContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${index + 1}",
                                color = stepTextColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (index < titles.lastIndex) {
                        val connectorColor by animateColorAsState(
                            targetValue = if (index < currentStep) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            animationSpec = tween(durationMillis = 280),
                            label = "progressConnectorColor"
                        )
                        Box(
                            modifier = Modifier.weight(1f).height(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(connectorColor)
                        )
                    }
                }
            }
            Text(
                if (selectedCount == 0)
                    "اختار صورك الأول، وبعدها راجع القص والإضاءة قبل الحفظ."
                else
                    "هنحفظ نسخ محسّنة في مجلد منفصل، والصور الأصلية هتفضل زي ما هي.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepHeading(number: Int, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$number",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ValuePill(value: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
