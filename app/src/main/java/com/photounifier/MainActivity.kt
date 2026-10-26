package com.photounifier

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.photounifier.ui.PhotoUnifierTheme
import com.photounifier.ui.PhotoUnifierHeight
import com.photounifier.ui.components.PhotoUnifierButton
import com.photounifier.ui.components.PhotoUnifierOutlinedButton
import com.photounifier.ui.screens.AdjustPreviewScreen
import com.photounifier.ui.screens.CropReviewCard
import com.photounifier.ui.screens.CropWorkspaceBottomBar
import com.photounifier.ui.screens.CropWorkspaceView
import com.photounifier.ui.screens.HomeScreen
import com.photounifier.ui.screens.ResultScreen
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.photounifier.ui.components.PhotoUnifierSnackbar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    private val viewModel: PhotoViewModel by viewModels()
    private var openCvReady = false

    /**
     * Android 10+ MediaStore writes do not need broad storage permissions.
     * The external camera app writes the captured JPEG directly to this URI.
     */
    private fun createScanOutputUri(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Scan_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/PhotoUnifier/Scans"
            )
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        openCvReady = OpenCVLoader.initLocal()
        setContent {
            PhotoUnifierTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    val fallbackState = PhotoUiState(
                        status = stringResource(R.string.status_ready_select),
                        exportStatus = stringResource(R.string.status_ready_export)
                    )
                    val state by viewModel.state.observeAsState(fallbackState)
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
                    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
                    val cameraLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.TakePicture()
                    ) { captured ->
                        val outputUri = pendingCameraUri
                        pendingCameraUri = null
                        if (captured && outputUri != null) {
                            // The camera writes a full-resolution JPEG to this MediaStore URI.
                            viewModel.selectImages((state.selected + outputUri).distinct())
                        } else if (outputUri != null) {
                            // Remove the empty MediaStore row if the user cancels capture.
                            runCatching { contentResolver.delete(outputUri, null, null) }
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
                        onCaptureDocument = {
                            val outputUri = runCatching { createScanOutputUri() }.getOrNull()
                            if (outputUri == null) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.scan_prepare_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                pendingCameraUri = outputUri
                                try {
                                    cameraLauncher.launch(outputUri)
                                } catch (_: Exception) {
                                    pendingCameraUri = null
                                    runCatching { contentResolver.delete(outputUri, null, null) }
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.camera_open_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        onClearSelection = { viewModel.selectImages(emptyList()) },
                        onRemoveImage = { uri ->
                            viewModel.selectImages((state.selected - uri).distinct())
                        },
                        onPreviewSelect = viewModel::showPreview,
                        onProcess = { viewModel.process(openCvReady) },
                        onToggleExportImage = viewModel::toggleExportImage,
                        onSelectAllExportImages = viewModel::selectAllExportImages,
                        onExport = viewModel::exportSelected,
                        onBrightnessChange = viewModel::setBrightness,
                        onContrastChange = viewModel::setContrast,
                        onOpenCrop = { viewModel.openCropEditor(openCvReady) },
                        onMoveCropCorner = viewModel::moveCropCorner,
                        onSelectCropPage = { index ->
                            viewModel.showPreview(index)
                            viewModel.openCropEditor(openCvReady)
                        },
                        onRotateCropPage = viewModel::rotateCropPage,
                        onEnsureCropPageThumbnail = viewModel::ensurePageThumbnail,
                        onUndoCrop = viewModel::undoCrop,
                        onResetCrop = viewModel::resetCrop,
                        onCancelCrop = viewModel::cancelCrop,
                        onApplyCrop = viewModel::applyCrop,
                        onFinishCrop = viewModel::cancelCrop
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
    onCaptureDocument: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onPreviewSelect: (Int) -> Unit,
    onProcess: () -> Unit,
    onToggleExportImage: (String, Boolean) -> Unit,
    onSelectAllExportImages: (Boolean) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onContrastChange: (Int) -> Unit,
    onOpenCrop: () -> Unit,
    onMoveCropCorner: (Int, Float, Float) -> Unit,
    onSelectCropPage: (Int) -> Unit,
    onRotateCropPage: () -> Unit,
    onEnsureCropPageThumbnail: (Int) -> Unit,
    onUndoCrop: () -> Unit,
    onResetCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onApplyCrop: () -> Unit,
    onFinishCrop: () -> Unit
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val isCropWorkspace = currentStep == 1 && state.cropOpen
    val exportSuccessMessage = stringResource(R.string.snackbar_export_success)
    val openCvFallbackMessage = stringResource(R.string.snackbar_opencv_fallback)
    val layoutDirection = LocalLayoutDirection.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(
        enabled = currentStep > 0 && !state.cropApplying && !isCropWorkspace
    ) {
        // Keep long-running work on screen; the system Back press is consumed while it is active.
        if (!state.busy && !state.exportBusy) {
            currentStep = (currentStep - 1).coerceAtLeast(0)
        }
    }

    // Keep the home screen visible when selection is empty
    LaunchedEffect(state.selected.isEmpty()) {
        if (state.selected.isEmpty() && currentStep > 0 && !state.busy) {
            currentStep = 0
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

    // Show feedback for export completion
    LaunchedEffect(state.exportedCount, state.exportBusy) {
        if (state.exportedCount > 0 && !state.exportBusy && state.exportErrors.isEmpty()) {
            snackbarHostState.showSnackbar(
                message = exportSuccessMessage
            )
        }
    }

    // Show feedback for OpenCV failure if encountered
    LaunchedEffect(openCvReady) {
        if (!openCvReady) {
            snackbarHostState.showSnackbar(
                message = openCvFallbackMessage
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                PhotoUnifierSnackbar(snackbarData)
            }
        },
        // Crop review and editor already render their own headings; avoid stacking a second app bar.
        topBar = {
            if (currentStep == 2 || currentStep == 3) TopAppBar(
                title = {
                    Text(
                        text = if (currentStep == 2) {
                            stringResource(R.string.screen_title_adjust)
                        } else {
                            stringResource(R.string.screen_title_result)
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                )
            )
        },
        bottomBar = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactBottomBar = maxWidth < 380.dp
                if (isCropWorkspace) {
                CropWorkspaceBottomBar(
                    state = state,
                    onRotatePage = onRotateCropPage,
                    onUndoCrop = onUndoCrop,
                    onResetCrop = onResetCrop,
                    onCancelCrop = onCancelCrop,
                    onApplyCrop = onApplyCrop,
                    onFinishCrop = { onFinishCrop(); currentStep = 2 }
                )
            } else if (currentStep == 1 && !state.cropOpen) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (compactBottomBar) 6.dp else 12.dp,
                            vertical = if (compactBottomBar) 3.dp else 6.dp
                        ),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (compactBottomBar) 4.dp else 12.dp,
                                vertical = if (compactBottomBar) 3.dp else 6.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(if (compactBottomBar) 4.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhotoUnifierOutlinedButton(
                            onClick = { currentStep = 0 },
                            enabled = !state.busy && !state.cropApplying,
                            modifier = Modifier.weight(1f),
                            height = PhotoUnifierHeight.buttonTertiary,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = if (compactBottomBar) 4.dp else com.photounifier.ui.PhotoUnifierSpacing.md,
                                vertical = if (compactBottomBar) 0.dp else com.photounifier.ui.PhotoUnifierSpacing.xs
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.action_previous),
                                style = if (compactBottomBar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        PhotoUnifierButton(
                            onClick = { currentStep = 2 },
                            enabled = !state.busy && !state.cropApplying,
                            modifier = Modifier.weight(if (compactBottomBar) 1.45f else 1.3f),
                            height = PhotoUnifierHeight.buttonTertiary,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = if (compactBottomBar) 4.dp else com.photounifier.ui.PhotoUnifierSpacing.md,
                                vertical = if (compactBottomBar) 0.dp else com.photounifier.ui.PhotoUnifierSpacing.xs
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.action_next_adjustment),
                                fontWeight = FontWeight.Bold,
                                style = if (compactBottomBar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    if (currentStep == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                horizontal = if (compactBottomBar) 4.dp else 8.dp,
                                vertical = 4.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(if (compactBottomBar) 3.dp else 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhotoUnifierOutlinedButton(
                                onClick = onCaptureDocument,
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                                height = PhotoUnifierHeight.buttonTertiary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (compactBottomBar) 3.dp else com.photounifier.ui.PhotoUnifierSpacing.md,
                                    vertical = if (compactBottomBar) 0.dp else com.photounifier.ui.PhotoUnifierSpacing.xs
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.action_camera),
                                    style = if (compactBottomBar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            PhotoUnifierOutlinedButton(
                                onClick = { onPickImages(true) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                                height = PhotoUnifierHeight.buttonTertiary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (compactBottomBar) 3.dp else com.photounifier.ui.PhotoUnifierSpacing.md,
                                    vertical = if (compactBottomBar) 0.dp else com.photounifier.ui.PhotoUnifierSpacing.xs
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.action_gallery),
                                    style = if (compactBottomBar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            PhotoUnifierButton(
                                onClick = { currentStep = 1 },
                                enabled = state.selected.isNotEmpty() && !state.busy,
                                modifier = Modifier.weight(if (compactBottomBar) 1.25f else 1.2f),
                                height = PhotoUnifierHeight.buttonTertiary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (compactBottomBar) 3.dp else com.photounifier.ui.PhotoUnifierSpacing.md,
                                    vertical = if (compactBottomBar) 0.dp else com.photounifier.ui.PhotoUnifierSpacing.xs
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.action_review_images),
                                    fontWeight = FontWeight.Bold,
                                    style = if (compactBottomBar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                horizontal = if (compactBottomBar) 6.dp else 12.dp,
                                vertical = 4.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(if (compactBottomBar) 4.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhotoUnifierOutlinedButton(
                                onClick = { currentStep = 0 },
                                enabled = currentStep != 0 && !state.busy && !state.exportBusy,
                                modifier = Modifier.weight(1f),
                                height = PhotoUnifierHeight.buttonTertiary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (compactBottomBar) 4.dp else com.photounifier.ui.PhotoUnifierSpacing.md,
                                    vertical = if (compactBottomBar) 0.dp else com.photounifier.ui.PhotoUnifierSpacing.xs
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.action_documents),
                                    style = if (compactBottomBar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            PhotoUnifierButton(
                                onClick = {
                                    currentStep = when (currentStep) {
                                        1 -> 2
                                        2 -> if (state.resultPreview != null || state.enhancedPreview != null) 3 else 2
                                        else -> 2
                                    }
                                },
                                enabled = !state.busy && !state.cropApplying && !state.exportBusy &&
                                    (currentStep == 1 || currentStep == 2 &&
                                     (state.resultPreview != null || state.enhancedPreview != null) ||
                                     currentStep == 3),
                                modifier = Modifier.weight(if (compactBottomBar) 1.5f else 1.4f),
                                height = PhotoUnifierHeight.buttonTertiary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (compactBottomBar) 4.dp else com.photounifier.ui.PhotoUnifierSpacing.md,
                                    vertical = if (compactBottomBar) 0.dp else com.photounifier.ui.PhotoUnifierSpacing.xs
                                )
                            ) {
                                Text(
                                    when (currentStep) {
                                        1 -> stringResource(R.string.action_next_enhance)
                                        2 -> stringResource(R.string.action_view_export)
                                        else -> stringResource(R.string.action_back_enhance)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = if (compactBottomBar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
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
                .padding(
                    horizontal = if (isCropWorkspace) 14.dp else 12.dp,
                    vertical = if (isCropWorkspace) 2.dp else 6.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isCropWorkspace) 4.dp else 8.dp)
        ) {
            // Smooth animated transition between main workflow views
            AnimatedContent(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width ->
                            if (layoutDirection == LayoutDirection.Rtl) width else -width
                        } + fadeIn()).togetherWith(
                            slideOutHorizontally { width ->
                                if (layoutDirection == LayoutDirection.Rtl) -width else width
                            } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width ->
                            if (layoutDirection == LayoutDirection.Rtl) -width else width
                        } + fadeIn()).togetherWith(
                            slideOutHorizontally { width ->
                                if (layoutDirection == LayoutDirection.Rtl) width else -width
                            } + fadeOut()
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
                            onCaptureDocument = onCaptureDocument,
                            onClearSelection = onClearSelection,
                            onRemoveImage = onRemoveImage,
                            onPreviewSelect = onPreviewSelect,
                            enabled = !state.busy
                        )
                    }

                    1 -> {
                        if (isCropWorkspace) {
                            CropWorkspaceView(
                                state = state,
                                onMoveCropCorner = onMoveCropCorner,
                                onSelectPage = onSelectCropPage,
                                onEnsurePageThumbnail = onEnsureCropPageThumbnail,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CropReviewCard(
                                state = state,
                                openCvReady = openCvReady,
                                onOpenCrop = onOpenCrop,
                                modifier = Modifier.fillMaxSize()
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
                            },
                            onToggleExportImage = onToggleExportImage,
                            onSelectAllExportImages = onSelectAllExportImages,
                            onExportImages = { onExport(ExportFormat.IMAGES) },
                            onExportPdf = { onExport(ExportFormat.PDF) }
                        )
                    }
                }
            }
        }
    }
}
