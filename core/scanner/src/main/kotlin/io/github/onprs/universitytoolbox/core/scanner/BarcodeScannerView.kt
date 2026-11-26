package io.github.onprs.universitytoolbox.core.scanner

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val MIN_FRAME_DISPATCH_INTERVAL_MS = 400L
private const val TARGET_ANALYSIS_WIDTH_PX = 1920
private const val TARGET_ANALYSIS_HEIGHT_PX = 1080

/**
 * 实时相机扫码 Compose 预览组件，内部集成 CameraX [LifecycleCameraController]、1080p 高清识别帧率策略、
 * ML Kit 远距离智能自动变焦（[ZoomSuggestionOptions]）以及统一缩放/对焦测光手势控制。
 */
@Composable
fun BarcodeScannerView(
    onBarcodeDetected: (BarcodeScanResult) -> Unit,
    modifier: Modifier = Modifier,
    isScanningEnabled: Boolean = true,
    torchEnabled: Boolean = false,
    enableAutoZoom: Boolean = true,
    interactiveState: CameraInteractiveState = rememberCameraInteractiveState(),
    debounceWindowMs: Long = ScanResultDebouncer.DEFAULT_DEBOUNCE_WINDOW_MS,
    onScannerError: ((Throwable) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onScannerError)
    val currentAutoZoom by rememberUpdatedState(enableAutoZoom)
    val currentInteractiveState by rememberUpdatedState(interactiveState)

    val dispatchGate = remember(debounceWindowMs) {
        LiveScanDispatchGate(debounceWindowMs = debounceWindowMs)
    }
    val isDisposed = remember { AtomicBoolean(false) }

    val cameraController = remember(context) { createScannerCameraController(context) }
    val mlKitScanner = remember {
        createAutoZoomBarcodeScanner(
            isDisposed = isDisposed,
            isAutoZoomEnabled = { currentAutoZoom },
            onSuggestZoom = { ratio -> currentInteractiveState.applyAutoSuggestedZoom(ratio) },
        )
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(isScanningEnabled) {
        if (isScanningEnabled) {
            dispatchGate.reset()
        }
    }

    LaunchedEffect(torchEnabled, cameraController) {
        if (!isDisposed.get()) {
            applyTorchSafely(
                cameraController = cameraController,
                enabled = torchEnabled,
                onError = currentOnError,
            )
        }
    }

    BindCameraScannerSession(
        context = context,
        lifecycleOwner = lifecycleOwner,
        cameraController = cameraController,
        interactiveState = interactiveState,
        mlKitScanner = mlKitScanner,
        analysisExecutor = analysisExecutor,
        dispatchGate = dispatchGate,
        isDisposed = isDisposed,
        isScanningEnabled = isScanningEnabled,
        torchEnabled = torchEnabled,
        onBarcodeDetected = onBarcodeDetected,
        onScannerError = onScannerError,
    )

    DisposableEffect(mlKitScanner, analysisExecutor) {
        onDispose {
            isDisposed.set(true)
            runCatching { mlKitScanner.close() }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier.cameraViewportGestures(interactiveState),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
                interactiveState.attachPreviewView(this)
            }
        },
        update = { previewView ->
            if (previewView.controller !== cameraController) {
                previewView.controller = cameraController
            }
            interactiveState.attachPreviewView(previewView)
        },
    )
}

private fun createAutoZoomBarcodeScanner(
    isDisposed: AtomicBoolean,
    isAutoZoomEnabled: () -> Boolean,
    onSuggestZoom: (Float) -> Boolean,
): BarcodeScanner {
    val zoomOptions = ZoomSuggestionOptions.Builder { suggestedRatio ->
        if (!isDisposed.get() && isAutoZoomEnabled()) onSuggestZoom(suggestedRatio) else false
    }
        .setMaxSupportedZoomRatio(CameraZoomCalculator.MAX_AUTO_SUGGESTED_ZOOM)
        .build()

    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .setZoomSuggestionOptions(zoomOptions)
        .build()
    return BarcodeScanning.getClient(options)
}

private fun createScannerCameraController(context: Context): LifecycleCameraController {
    val analysisSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(TARGET_ANALYSIS_WIDTH_PX, TARGET_ANALYSIS_HEIGHT_PX),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            ),
        )
        .build()

    return LifecycleCameraController(context).apply {
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        isPinchToZoomEnabled = false
        isTapToFocusEnabled = false
        imageAnalysisResolutionSelector = analysisSelector
        setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
    }
}

@Suppress("LongParameterList")
@Composable
private fun BindCameraScannerSession(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraController: LifecycleCameraController,
    interactiveState: CameraInteractiveState,
    mlKitScanner: BarcodeScanner,
    analysisExecutor: ExecutorService,
    dispatchGate: LiveScanDispatchGate,
    isDisposed: AtomicBoolean,
    isScanningEnabled: Boolean,
    torchEnabled: Boolean,
    onBarcodeDetected: (BarcodeScanResult) -> Unit,
    onScannerError: ((Throwable) -> Unit)?,
) {
    val currentOnDetected by rememberUpdatedState(onBarcodeDetected)
    val currentOnError by rememberUpdatedState(onScannerError)
    val currentScanningEnabled by rememberUpdatedState(isScanningEnabled)
    val currentTorchEnabled by rememberUpdatedState(torchEnabled)
    val currentInteractive by rememberUpdatedState(interactiveState)

    DisposableEffect(lifecycleOwner, cameraController, mlKitScanner, analysisExecutor) {
        val zoomObserver = Observer<ZoomState> { state ->
            currentInteractive.onHardwareZoomStateChanged(state)
        }
        if (!isDisposed.get()) {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val analyzer = MlKitAnalyzer(
                listOf(mlKitScanner),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor,
            ) { result ->
                if (isDisposed.get() || !currentScanningEnabled) {
                    return@MlKitAnalyzer
                }
                runCatching {
                    val throwable = result.getThrowable(mlKitScanner)
                    if (throwable != null) {
                        currentOnError?.invoke(throwable)
                        return@runCatching
                    }
                    dispatchDetectedBarcodes(
                        barcodes = result.getValue(mlKitScanner).orEmpty(),
                        dispatchGate = dispatchGate,
                        onDetected = currentOnDetected,
                    )
                }.onFailure { error ->
                    currentOnError?.invoke(error)
                }
            }

            runCatching {
                cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
                cameraController.bindToLifecycle(lifecycleOwner)
                cameraController.zoomState.observe(lifecycleOwner, zoomObserver)
                currentInteractive.attachCamera(cameraController, previewView = null)
                currentInteractive.refreshFlashCapability()
                val defaultOneX = CameraZoomCalculator.DEFAULT_MIN_ZOOM
                cameraController.setZoomRatio(defaultOneX)
            }.onSuccess {
                applyTorchSafely(
                    cameraController = cameraController,
                    enabled = currentTorchEnabled,
                    onError = currentOnError,
                )
            }.onFailure { error ->
                currentOnError?.invoke(error)
            }
        }

        onDispose {
            runCatching { cameraController.zoomState.removeObserver(zoomObserver) }
            runCatching { cameraController.enableTorch(false) }
            runCatching { cameraController.clearImageAnalysisAnalyzer() }
            runCatching { cameraController.unbind() }
        }
    }
}

internal class LiveScanDispatchGate(
    debounceWindowMs: Long,
    private val timeProvider: TimeProvider = TimeProvider.System,
    minFrameIntervalMs: Long = MIN_FRAME_DISPATCH_INTERVAL_MS,
) {
    private val lock = Any()
    private val debouncer = ScanResultDebouncer(
        debounceWindowMs = debounceWindowMs,
        timeProvider = timeProvider,
    )
    private val effectiveMinIntervalMs = minOf(debounceWindowMs.coerceAtLeast(0L), minFrameIntervalMs)
    private var lastDispatchedAtMs: Long = Long.MIN_VALUE

    fun acceptFirst(candidates: Sequence<BarcodeScanResult>): BarcodeScanResult? {
        val nowMs = timeProvider.currentTimeMillis()
        synchronized(lock) {
            if (lastDispatchedAtMs != Long.MIN_VALUE &&
                nowMs - lastDispatchedAtMs in 0 until effectiveMinIntervalMs
            ) {
                return null
            }
            val accepted = candidates.firstNotNullOfOrNull(debouncer::filterEmit)
            if (accepted != null) {
                lastDispatchedAtMs = nowMs
            }
            return accepted
        }
    }

    fun reset() {
        synchronized(lock) {
            debouncer.reset()
            lastDispatchedAtMs = Long.MIN_VALUE
        }
    }
}

internal fun applyTorchSafely(
    cameraController: LifecycleCameraController,
    enabled: Boolean,
    onError: ((Throwable) -> Unit)?,
) {
    runCatching {
        val hasFlash = cameraController.cameraInfo?.hasFlashUnit() ?: true
        if (!hasFlash) {
            return
        }
        val future = cameraController.enableTorch(enabled)
        future.addListener(
            {
                runCatching { future.get() }.onFailure { error ->
                    if (error !is CameraControl.OperationCanceledException &&
                        error.cause !is CameraControl.OperationCanceledException
                    ) {
                        onError?.invoke(error)
                    }
                }
            },
            Executor { command -> command.run() },
        )
    }.onFailure { error ->
        if (error !is CameraControl.OperationCanceledException) {
            onError?.invoke(error)
        }
    }
}

private fun dispatchDetectedBarcodes(
    barcodes: List<Barcode>,
    dispatchGate: LiveScanDispatchGate,
    onDetected: (BarcodeScanResult) -> Unit,
) {
    if (barcodes.isEmpty()) {
        return
    }
    val nowMs = System.currentTimeMillis()
    val candidates = barcodes.asSequence().mapNotNull { barcode ->
        BarcodeFormatMapper.toScanResult(
            barcode = barcode,
            detectedAtEpochMs = nowMs,
            sourceMode = BarcodeScannerMode.LIVE_CAMERA,
        )
    }
    val accepted = dispatchGate.acceptFirst(candidates)
    if (accepted != null) {
        onDetected(accepted)
    }
}
