package io.github.onprs.universitytoolbox.core.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.ui.PermissionGuideCard
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

private const val MAX_PHOTO_CAPTURE_DIMENSION_PX = 1920
private const val PHOTO_JPEG_QUALITY = 85
private const val NO_SCALE_THRESHOLD = 0.999f

/**
 * 全屏沉浸式实时扫码覆盖层对话框：
 * - 取景框位于屏幕上中部平视舒适区，告别底部抽屉遮挡与父级滚动手势冲突；
 * - 支持双指丝滑缩放、双击快速放大、单击对焦测光、快捷倍率档位、单手平滑滑块、远距自动变焦、手电筒与相册选图。
 */
@Suppress("LongParameterList")
@Composable
fun FullScreenBarcodeScannerDialog(
    onBarcodeDetected: (BarcodeScanResult) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.core_scanner_default_scan_title),
    hintText: String = stringResource(R.string.core_scanner_scan_hint),
    isScanningEnabled: Boolean = true,
    externalTorchEnabled: Boolean? = null,
    onExternalToggleTorch: (() -> Unit)? = null,
    onPickFromGallery: (() -> Unit)? = null,
    externalHasCameraPermission: Boolean? = null,
    externalIsPermanentlyDenied: Boolean? = null,
    onExternalPermissionAction: (() -> Unit)? = null,
) {
    var internalTorch by rememberSaveable { mutableStateOf(false) }
    val effectiveTorch = externalTorchEnabled ?: internalTorch
    val toggleTorch: () -> Unit = onExternalToggleTorch ?: { internalTorch = !internalTorch }

    val internalPerm = rememberOverlayCameraPermissionHolder(
        autoRequestOnEnter = externalHasCameraPermission == null,
    )
    val hasPermission = externalHasCameraPermission ?: internalPerm.hasPermission
    val isPermanentlyDenied = externalIsPermanentlyDenied ?: internalPerm.isPermanentlyDenied
    val onPermissionAction = onExternalPermissionAction ?: internalPerm.requestOrOpenSettings

    val interactiveState = rememberCameraInteractiveState(allowUltraWide = false)
    ObserveFocusIndicatorAutoDismiss(interactiveState)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        ConfigureImmersiveCameraDialogWindow()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (hasPermission) {
                ActiveScannerViewportContent(
                    hintText = hintText,
                    isScanningEnabled = isScanningEnabled,
                    torchEnabled = effectiveTorch,
                    interactiveState = interactiveState,
                    onBarcodeDetected = onBarcodeDetected,
                    onToggleTorch = toggleTorch,
                    onPickFromGallery = onPickFromGallery,
                )
            } else {
                CameraPermissionFallbackBox(
                    rationale = stringResource(
                        if (isPermanentlyDenied) {
                            R.string.core_scanner_perm_scan_denied
                        } else {
                            R.string.core_scanner_perm_scan_rationale
                        },
                    ),
                    isPermanentlyDenied = isPermanentlyDenied,
                    onRequestPermission = onPermissionAction,
                    onPickFromGallery = onPickFromGallery,
                )
            }

            FullScreenCameraTopBar(
                title = title,
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ActiveScannerViewportContent(
    hintText: String,
    isScanningEnabled: Boolean,
    torchEnabled: Boolean,
    interactiveState: CameraInteractiveState,
    onBarcodeDetected: (BarcodeScanResult) -> Unit,
    onToggleTorch: () -> Unit,
    onPickFromGallery: (() -> Unit)?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val frameSizeDp = (minOf(maxWidth, maxHeight) * 0.72f).coerceIn(210.dp, 290.dp)
        val hintTopOffsetDp = (maxHeight * VIEWFINDER_CENTER_Y_FRACTION + frameSizeDp / 2f + 14.dp)
            .coerceAtMost(maxHeight - 196.dp)

        BarcodeScannerView(
            onBarcodeDetected = onBarcodeDetected,
            isScanningEnabled = isScanningEnabled,
            torchEnabled = torchEnabled,
            enableAutoZoom = true,
            interactiveState = interactiveState,
            modifier = Modifier.fillMaxSize(),
        )

        ScannerViewfinderOverlay(
            interactiveState = interactiveState,
            showScanFrame = true,
        )

        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolboxSpacing.large)
                .offset { IntOffset(x = 0, y = hintTopOffsetDp.roundToPx()) },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = ToolboxSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
        ) {
            CameraZoomControlStrip(interactiveState = interactiveState)

            Row(
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (interactiveState.hasFlashUnit) {
                    OverlayIconActionPill(
                        text = stringResource(
                            if (torchEnabled) R.string.core_scanner_torch_on else R.string.core_scanner_torch_off,
                        ),
                        highlighted = torchEnabled,
                        onClick = onToggleTorch,
                        icon = {
                            Icon(
                                imageVector = if (torchEnabled) {
                                    Icons.Outlined.FlashlightOff
                                } else {
                                    Icons.Outlined.FlashlightOn
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
                if (onPickFromGallery != null) {
                    OverlayIconActionPill(
                        text = stringResource(R.string.core_scanner_pick_gallery),
                        highlighted = false,
                        onClick = onPickFromGallery,
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 全屏沉浸式现场拍照相机对话框：
 * 支持前后镜头切换、照明补光、快捷倍率与平滑滑块缩放、双指缩放、双击放大及点按对焦测光，返回正向压缩后的 JPEG 字节数组。
 */
@Composable
fun FullScreenPhotoCaptureDialog(
    onPhotoCaptured: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.core_scanner_photo_title),
    maxDimensionPx: Int = MAX_PHOTO_CAPTURE_DIMENSION_PX,
    onPickFromGallery: (() -> Unit)? = null,
    onCaptureError: (() -> Unit)? = null,
) {
    val perm = rememberOverlayCameraPermissionHolder(autoRequestOnEnter = true)
    val interactiveState = rememberCameraInteractiveState(allowUltraWide = true)
    ObserveFocusIndicatorAutoDismiss(interactiveState)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        ConfigureImmersiveCameraDialogWindow()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (perm.hasPermission) {
                ActivePhotoCaptureViewport(
                    interactiveState = interactiveState,
                    maxDimensionPx = maxDimensionPx,
                    onPhotoCaptured = onPhotoCaptured,
                    onPickFromGallery = onPickFromGallery,
                    onCaptureError = onCaptureError,
                )
            } else {
                CameraPermissionFallbackBox(
                    rationale = stringResource(
                        if (perm.isPermanentlyDenied) {
                            R.string.core_scanner_perm_photo_denied
                        } else {
                            R.string.core_scanner_perm_photo_rationale
                        },
                    ),
                    isPermanentlyDenied = perm.isPermanentlyDenied,
                    onRequestPermission = perm.requestOrOpenSettings,
                    onPickFromGallery = onPickFromGallery,
                )
            }

            FullScreenCameraTopBar(
                title = title,
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun ActivePhotoCaptureViewport(
    interactiveState: CameraInteractiveState,
    maxDimensionPx: Int,
    onPhotoCaptured: (ByteArray) -> Unit,
    onPickFromGallery: (() -> Unit)?,
    onCaptureError: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val cameraController = remember(context) { createPhotoCameraController(context, maxDimensionPx) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    BindPhotoCameraSession(
        cameraController = cameraController,
        interactiveState = interactiveState,
        lensFacing = lensFacing,
        torchEnabled = torchEnabled,
        captureExecutor = captureExecutor,
        onResetTorch = { torchEnabled = false },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .cameraViewportGestures(interactiveState),
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

        ScannerViewfinderOverlay(
            interactiveState = interactiveState,
            showScanFrame = false,
        )

        PhotoCaptureBottomDeck(
            interactiveState = interactiveState,
            isCapturing = isCapturing,
            torchEnabled = torchEnabled,
            isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
            onToggleTorch = { torchEnabled = !torchEnabled },
            onSwitchCamera = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            },
            onPickFromGallery = onPickFromGallery,
            onShutterClick = {
                if (!isCapturing) {
                    isCapturing = true
                    capturePhotoAsJpeg(
                        cameraController = cameraController,
                        executor = captureExecutor,
                        mirrorHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT,
                        maxDimensionPx = maxDimensionPx,
                        onResult = { bytes ->
                            scope.launch {
                                isCapturing = false
                                if (bytes != null) {
                                    onPhotoCaptured(bytes)
                                } else {
                                    onCaptureError?.invoke()
                                }
                            }
                        },
                    )
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun BindPhotoCameraSession(
    cameraController: LifecycleCameraController,
    interactiveState: CameraInteractiveState,
    lensFacing: Int,
    torchEnabled: Boolean,
    captureExecutor: java.util.concurrent.ExecutorService,
    onResetTorch: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lensFacing, cameraController) {
        val selector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        if (runCatching { cameraController.hasCamera(selector) }.getOrDefault(true)) {
            onResetTorch()
            runCatching { cameraController.cameraSelector = selector }
            runCatching { cameraController.setZoomRatio(CameraZoomCalculator.DEFAULT_MIN_ZOOM) }
            interactiveState.refreshFlashCapability()
        }
    }

    LaunchedEffect(torchEnabled, cameraController) {
        applyTorchSafely(cameraController, torchEnabled, onError = null)
    }

    DisposableEffect(lifecycleOwner, cameraController, captureExecutor) {
        val zoomObserver = Observer<ZoomState> { state ->
            interactiveState.onHardwareZoomStateChanged(state)
        }
        runCatching {
            cameraController.bindToLifecycle(lifecycleOwner)
            cameraController.zoomState.observe(lifecycleOwner, zoomObserver)
            interactiveState.attachCamera(cameraController, previewView = null)
            interactiveState.refreshFlashCapability()
        }
        onDispose {
            runCatching { cameraController.zoomState.removeObserver(zoomObserver) }
            runCatching { cameraController.enableTorch(false) }
            runCatching { cameraController.unbind() }
            captureExecutor.shutdown()
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PhotoCaptureBottomDeck(
    interactiveState: CameraInteractiveState,
    isCapturing: Boolean,
    torchEnabled: Boolean,
    isFrontCamera: Boolean,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onPickFromGallery: (() -> Unit)?,
    onShutterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayBarBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = ToolboxSpacing.medium, bottom = ToolboxSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
    ) {
        Text(
            text = stringResource(R.string.core_scanner_photo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )

        CameraZoomControlStrip(interactiveState = interactiveState)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolboxSpacing.extraLarge),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoSideControlButton(
                visible = !isFrontCamera && interactiveState.hasFlashUnit,
                onClick = onToggleTorch,
                altAction = onPickFromGallery,
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                    contentDescription = stringResource(R.string.core_scanner_torch_off),
                    tint = if (torchEnabled) OverlayAccentColor else Color.White,
                )
            }

            ShutterCaptureButton(
                isCapturing = isCapturing,
                onClick = onShutterClick,
            )

            Surface(
                onClick = onSwitchCamera,
                shape = CircleShape,
                color = OverlayChipBackground,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Cameraswitch,
                        contentDescription = stringResource(R.string.core_scanner_switch_camera),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSideControlButton(
    visible: Boolean,
    onClick: () -> Unit,
    altAction: (() -> Unit)?,
    icon: @Composable () -> Unit,
) {
    when {
        visible -> Surface(
            onClick = onClick,
            shape = CircleShape,
            color = OverlayChipBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }

        altAction != null -> Surface(
            onClick = altAction,
            shape = CircleShape,
            color = OverlayChipBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = stringResource(R.string.core_scanner_pick_gallery),
                    tint = Color.White,
                )
            }
        }

        else -> Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun ShutterCaptureButton(isCapturing: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !isCapturing,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(4.dp, Color.White),
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isCapturing) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun FullScreenCameraTopBar(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayBarBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = ToolboxSpacing.small, vertical = ToolboxSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.core_scanner_close),
                tint = Color.White,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 48.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CameraPermissionFallbackBox(
    rationale: String,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onPickFromGallery: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ToolboxSpacing.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PermissionGuideCard(
            title = stringResource(R.string.core_scanner_perm_title),
            rationale = rationale,
            isPermanentlyDenied = isPermanentlyDenied,
            onRequestPermission = onRequestPermission,
        )
        if (onPickFromGallery != null) {
            Spacer(modifier = Modifier.size(ToolboxSpacing.medium))
            OverlayIconActionPill(
                text = stringResource(R.string.core_scanner_pick_gallery),
                highlighted = false,
                onClick = onPickFromGallery,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

private fun createPhotoCameraController(context: Context, maxDimensionPx: Int): LifecycleCameraController {
    val captureSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(maxDimensionPx, maxDimensionPx),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            ),
        )
        .build()
    return LifecycleCameraController(context).apply {
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        isPinchToZoomEnabled = false
        isTapToFocusEnabled = false
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        imageCaptureResolutionSelector = captureSelector
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
    }
}

private fun capturePhotoAsJpeg(
    cameraController: LifecycleCameraController,
    executor: java.util.concurrent.ExecutorService,
    mirrorHorizontally: Boolean,
    maxDimensionPx: Int,
    onResult: (ByteArray?) -> Unit,
) {
    runCatching {
        cameraController.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val encoded = runCatching {
                        encodeImageProxyToJpeg(image, mirrorHorizontally, maxDimensionPx)
                    }.getOrNull()
                    image.close()
                    onResult(encoded)
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(null)
                }
            },
        )
    }.onFailure {
        onResult(null)
    }
}

internal fun encodeImageProxyToJpeg(image: ImageProxy, mirrorHorizontally: Boolean, maxDimensionPx: Int): ByteArray? {
    val rawBitmap = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees
    val longest = maxOf(rawBitmap.width, rawBitmap.height).coerceAtLeast(1)
    val scale = if (maxDimensionPx in 1 until longest) {
        maxDimensionPx.toFloat() / longest.toFloat()
    } else {
        1f
    }

    val transformed = if (rotation != 0 || mirrorHorizontally || scale < NO_SCALE_THRESHOLD) {
        val matrix = Matrix().apply {
            if (rotation != 0) {
                postRotate(rotation.toFloat())
            }
            if (mirrorHorizontally) {
                postScale(-1f, 1f)
            }
            if (scale < NO_SCALE_THRESHOLD) {
                postScale(scale, scale)
            }
        }
        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    } else {
        rawBitmap
    }

    val stream = ByteArrayOutputStream()
    val ok = transformed.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, stream)
    if (transformed !== rawBitmap) {
        transformed.recycle()
    }
    rawBitmap.recycle()
    return stream.toByteArray().takeIf { ok && it.isNotEmpty() }
}
