package io.github.onprs.universitytoolbox.core.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors

/**
 * 实时相机扫码 Compose 预览组件，内部集成 CameraX [LifecycleCameraController] 与 [MlKitAnalyzer]。
 * 建议业务调用方先通过权限卡片确认已授予 `CAMERA` 权限后再挂载本组件。
 */
@Composable
fun BarcodeScannerView(
    onBarcodeDetected: (BarcodeScanResult) -> Unit,
    modifier: Modifier = Modifier,
    isScanningEnabled: Boolean = true,
    torchEnabled: Boolean = false,
    debounceWindowMs: Long = ScanResultDebouncer.DEFAULT_DEBOUNCE_WINDOW_MS,
    onScannerError: ((Throwable) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDetected by rememberUpdatedState(onBarcodeDetected)
    val currentOnError by rememberUpdatedState(onScannerError)
    val currentScanningEnabled by rememberUpdatedState(isScanningEnabled)

    val debouncer = remember(debounceWindowMs) {
        ScanResultDebouncer(debounceWindowMs = debounceWindowMs)
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    val mlKitScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(torchEnabled) {
        cameraController.enableTorch(torchEnabled)
    }

    DisposableEffect(lifecycleOwner, cameraController, mlKitScanner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analyzer = MlKitAnalyzer(
            listOf(mlKitScanner),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            mainExecutor,
        ) { result ->
            if (!currentScanningEnabled) {
                return@MlKitAnalyzer
            }
            val throwable = result.getThrowable(mlKitScanner)
            if (throwable != null) {
                currentOnError?.invoke(throwable)
                return@MlKitAnalyzer
            }
            dispatchDetectedBarcodes(
                barcodes = result.getValue(mlKitScanner).orEmpty(),
                debouncer = debouncer,
                onDetected = currentOnDetected,
            )
        }

        cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        cameraController.bindToLifecycle(lifecycleOwner)

        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            mlKitScanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
            }
        },
        update = { previewView ->
            if (previewView.controller !== cameraController) {
                previewView.controller = cameraController
            }
        },
    )
}

private fun dispatchDetectedBarcodes(
    barcodes: List<Barcode>,
    debouncer: ScanResultDebouncer,
    onDetected: (BarcodeScanResult) -> Unit,
) {
    val nowMs = System.currentTimeMillis()
    val accepted = barcodes.firstNotNullOfOrNull { barcode ->
        val mapped = BarcodeFormatMapper.toScanResult(
            barcode = barcode,
            detectedAtEpochMs = nowMs,
            sourceMode = BarcodeScannerMode.LIVE_CAMERA,
        )
        mapped?.let(debouncer::filterEmit)
    }
    if (accepted != null) {
        onDetected(accepted)
    }
}
