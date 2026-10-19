package com.photounifier

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.photounifier.ui.PhotoUnifierTheme
import com.photounifier.ui.components.StepProgress
import com.photounifier.ui.screens.AdjustPreviewScreen
import com.photounifier.ui.screens.CropReviewCard
import com.photounifier.ui.screens.CropWorkspaceBottomBar
import com.photounifier.ui.screens.CropWorkspaceView
import com.photounifier.ui.screens.HomeScreen
import com.photounifier.ui.screens.ResultScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()
    private var openCvReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCvReady = OpenCVLoader.initLocal()
        setContent {
            PhotoUnifierTheme {
                CompositionLocalProvider(
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
                        onRemoveImage = { uri ->
                            viewModel.selectImages((state.selected - uri).distinct())
                        },
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
    onRemoveImage: (Uri) -> Unit,
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
    var currentStep by remember { mutableIntStateOf(0) }
    val stepTitles = listOf("اختيار الصور", "قص الصورة", "الضبط والمعاينة", "النتيجة")
    val isCropWorkspace = currentStep == 1 && state.cropOpen

    // Keep the home screen visible when selection is empty
    LaunchedEffect(state.selected.isEmpty()) {
        if (state.selected.isEmpty() && currentStep > 0 && !state.busy) {
            currentStep = 0
        }
    }

    // Manual crop is scoped to the first selected image
    LaunchedEffect(currentStep, state.previewIndex) {
        if (currentStep == 1 && state.previewIndex != 0 && !state.busy) {
            onPreviewSelect(0)
        }
    }

    // Automatically advance to adjustment step when crop is successfully applied
    LaunchedEffect(state.cropApplied) {
        if (state.cropApplied && currentStep == 1) {
            currentStep = 2
        }
    }

    // Automatically navigate to result step when processing is active or completed
    LaunchedEffect(state.busy, state.status, state.resultPreview, state.enhancedPreview) {
        if (state.busy) {
            currentStep = 3
        } else if (currentStep == 3 && (state.resultPreview != null || state.enhancedPreview != null)) {
            currentStep = 3
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مُوحِّد الصور",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "اكتشاف • تصحيح المنظور • تحسين ذكي",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            )
        },
        bottomBar = {
            if (isCropWorkspace) {
                CropWorkspaceBottomBar(
                    state = state,
                    onUndoCrop = onUndoCrop,
                    onResetCrop = onResetCrop,
                    onCancelCrop = onCancelCrop,
                    onApplyCrop = onApplyCrop
                )
            } else if (currentStep == 1 && !state.cropOpen) {
                Surface(
                    modifier = Modifier
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = 0 },
                            enabled = !state.busy && !state.cropApplying,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("السابق")
                        }

                        Button(
                            onClick = { currentStep = 2 },
                            enabled = !state.busy && !state.cropApplying,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("التالي: الضبط والمعاينة ➔", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .then(
                    if (isCropWorkspace) {
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(if (isCropWorkspace) 8.dp else 14.dp)
        ) {
            // Stepper indicator for multi-step progress
            if (!isCropWorkspace) {
                StepProgress(
                    currentStep = currentStep,
                    titles = stepTitles,
                    selectedCount = state.selected.size,
                    onStepClick = { target ->
                        if (!state.busy && !state.cropApplying) {
                            currentStep = target
                        }
                    }
                )
            }

            // Smooth animated transition between main workflow views
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    }
                },
                label = "stepAnimation"
            ) { step ->
                when (step) {
                    0 -> {
                        HomeScreen(
                            state = state,
                            onPickImages = onPickImages,
                            onClearSelection = onClearSelection,
                            onRemoveImage = onRemoveImage,
                            onPreviewSelect = onPreviewSelect,
                            onStartReview = { currentStep = 1 },
                            enabled = !state.busy
                        )
                    }

                    1 -> {
                        if (isCropWorkspace) {
                            CropWorkspaceView(
                                state = state,
                                onMoveCropCorner = onMoveCropCorner
                            )
                        } else {
                            CropReviewCard(
                                state = state,
                                openCvReady = openCvReady,
                                onOpenCrop = onOpenCrop
                            )
                        }
                    }

                    2 -> {
                        AdjustPreviewScreen(
                            state = state,
                            openCvReady = openCvReady,
                            onPreviewSelect = onPreviewSelect,
                            onBrightnessChange = onBrightnessChange,
                            onContrastChange = onContrastChange,
                            onProcess = onProcess
                        )
                    }

                    3 -> {
                        ResultScreen(
                            state = state,
                            onBackToAdjust = { currentStep = 2 },
                            onPickNewBatch = {
                                onClearSelection()
                                currentStep = 0
                            }
                        )
                    }
                }
            }
        }
    }
}
