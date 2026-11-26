package io.github.onprs.universitytoolbox.core.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import kotlin.math.abs
import kotlinx.coroutines.delay

internal const val VIEWFINDER_CENTER_Y_FRACTION = 0.37f
private const val VIEWFINDER_WIDTH_FRACTION = 0.72f
private const val VIEWFINDER_MIN_SIZE_DP = 210f
private const val VIEWFINDER_MAX_SIZE_DP = 290f
private const val FOCUS_RING_VISIBLE_MS = 1_100L
private const val SCAN_LINE_DURATION_MS = 2_100
private const val PRESET_ACTIVE_TOLERANCE = 0.14f

internal val OverlayBarBackground = Color(0x88000000)
internal val OverlayChipBackground = Color(0x9916181D)
internal val OverlayAccentColor = Color(0xFF7CC4FA)
private val ScrimMaskColor = Color(0x8A000000)
private val FocusReticleColor = Color(0xFFFFD54F)

@Composable
internal fun ConfigureImmersiveCameraDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            @Suppress("DEPRECATION")
            dialogWindow.statusBarColor = AndroidColor.TRANSPARENT
            @Suppress("DEPRECATION")
            dialogWindow.navigationBarColor = AndroidColor.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dialogWindow.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dialogWindow.attributes = dialogWindow.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(dialogWindow, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        onDispose {
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
internal fun ObserveFocusIndicatorAutoDismiss(interactiveState: CameraInteractiveState) {
    LaunchedEffect(interactiveState.focusTapSequence) {
        if (interactiveState.focusTapOffset != null) {
            delay(FOCUS_RING_VISIBLE_MS)
            interactiveState.clearFocusIndicator()
        }
    }
}

/**
 * 位于屏幕上中部视觉舒适区的扫码取景框遮罩、四角引导线、扫描线与点按对焦环。
 */
@Composable
internal fun ScannerViewfinderOverlay(
    interactiveState: CameraInteractiveState,
    showScanFrame: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "scanner_laser")
    val scanProgress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SCAN_LINE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan_line_progress",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (showScanFrame) {
            val minSide = minOf(size.width, size.height)
            val frameSize = (minSide * VIEWFINDER_WIDTH_FRACTION)
                .coerceIn(VIEWFINDER_MIN_SIZE_DP.dp.toPx(), VIEWFINDER_MAX_SIZE_DP.dp.toPx())
            val left = (size.width - frameSize) / 2f
            val top = (size.height * VIEWFINDER_CENTER_Y_FRACTION - frameSize / 2f)
                .coerceAtLeast(72.dp.toPx())
            val frameRect = Rect(Offset(left, top), Size(frameSize, frameSize))
            val cornerPx = 20.dp.toPx()

            val cutoutPath = Path().apply {
                addRoundRect(RoundRect(frameRect, CornerRadius(cornerPx, cornerPx)))
            }
            clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                drawRect(color = ScrimMaskColor)
            }
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = frameRect.topLeft,
                size = frameRect.size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawViewfinderCorners(frameRect, cornerPx)

            val laserY = frameRect.top + frameRect.height * scanProgress
            drawLine(
                color = OverlayAccentColor.copy(alpha = 0.88f),
                start = Offset(frameRect.left + 18.dp.toPx(), laserY),
                end = Offset(frameRect.right - 18.dp.toPx(), laserY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        interactiveState.focusTapOffset?.let { tap ->
            drawFocusReticle(tap)
        }
    }
}

private fun DrawScope.drawViewfinderCorners(frameRect: Rect, cornerPx: Float) {
    val strokePx = 3.5.dp.toPx()
    val armLen = 28.dp.toPx()
    val inset = strokePx / 2f
    val halfCorner = cornerPx / 2f
    val r = Rect(
        frameRect.left + inset,
        frameRect.top + inset,
        frameRect.right - inset,
        frameRect.bottom - inset,
    )
    val path = Path().apply {
        moveTo(r.left, r.top + armLen)
        lineTo(r.left, r.top + halfCorner)
        quadraticTo(r.left, r.top, r.left + halfCorner, r.top)
        lineTo(r.left + armLen, r.top)

        moveTo(r.right - armLen, r.top)
        lineTo(r.right - halfCorner, r.top)
        quadraticTo(r.right, r.top, r.right, r.top + halfCorner)
        lineTo(r.right, r.top + armLen)

        moveTo(r.left, r.bottom - armLen)
        lineTo(r.left, r.bottom - halfCorner)
        quadraticTo(r.left, r.bottom, r.left + halfCorner, r.bottom)
        lineTo(r.left + armLen, r.bottom)

        moveTo(r.right - armLen, r.bottom)
        lineTo(r.right - halfCorner, r.bottom)
        quadraticTo(r.right, r.bottom, r.right, r.bottom - halfCorner)
        lineTo(r.right, r.bottom - armLen)
    }
    drawPath(
        path = path,
        color = OverlayAccentColor,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawFocusReticle(center: Offset) {
    val boxHalf = 28.dp.toPx()
    val tickLen = 6.dp.toPx()
    val topLeft = Offset(center.x - boxHalf, center.y - boxHalf)
    drawRoundRect(
        color = FocusReticleColor,
        topLeft = topLeft,
        size = Size(boxHalf * 2f, boxHalf * 2f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = Stroke(width = 2.dp.toPx()),
    )
    drawLine(
        color = FocusReticleColor,
        start = Offset(center.x, center.y - boxHalf),
        end = Offset(center.x, center.y - boxHalf + tickLen),
        strokeWidth = 2.dp.toPx(),
    )
    drawLine(
        color = FocusReticleColor,
        start = Offset(center.x, center.y + boxHalf - tickLen),
        end = Offset(center.x, center.y + boxHalf),
        strokeWidth = 2.dp.toPx(),
    )
}

/**
 * 拇指舒适区单手缩放控制条：包含快捷倍率胶囊（1x / 2x / 3x / 5x）与平滑对数滑块。
 */
@Composable
internal fun CameraZoomControlStrip(interactiveState: CameraInteractiveState, modifier: Modifier = Modifier) {
    if (!interactiveState.canZoom) {
        return
    }
    val presets = interactiveState.quickPresets
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
    ) {
        if (presets.size > 1) {
            ZoomPresetPillRow(
                presets = presets,
                currentRatio = interactiveState.zoomRatio,
                onSelectPreset = interactiveState::setManualZoomRatio,
            )
        }
        ZoomSliderBar(interactiveState = interactiveState)
    }
}

@Composable
private fun ZoomPresetPillRow(presets: List<Float>, currentRatio: Float, onSelectPreset: (Float) -> Unit) {
    val activePresetIndex = presets.indices.minByOrNull { idx -> abs(presets[idx] - currentRatio) } ?: 0
    Surface(
        shape = CircleShape,
        color = OverlayChipBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEachIndexed { index, preset ->
                val isSelected = index == activePresetIndex
                val displayLabel = if (isSelected && abs(currentRatio - preset) >= PRESET_ACTIVE_TOLERANCE) {
                    CameraZoomCalculator.formatZoomLabel(currentRatio)
                } else {
                    CameraZoomCalculator.formatZoomLabel(preset)
                }
                ZoomPresetPill(
                    label = displayLabel,
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun ZoomSliderBar(interactiveState: CameraInteractiveState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OverlayChipBackground, CircleShape)
            .padding(horizontal = ToolboxSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
    ) {
        IconButton(
            onClick = { interactiveState.stepZoomBy(-CameraZoomCalculator.ZOOM_STEP_DELTA) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = stringResource(R.string.core_scanner_zoom_out),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Slider(
            value = interactiveState.sliderFraction,
            onValueChange = interactiveState::onSliderFractionChanged,
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = OverlayAccentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        IconButton(
            onClick = { interactiveState.stepZoomBy(CameraZoomCalculator.ZOOM_STEP_DELTA) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.core_scanner_zoom_in),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ZoomPresetPill(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = if (selected) Color(0xFF101418) else Color.White.copy(alpha = 0.88f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

internal class OverlayCameraPermissionHolder(
    val hasPermission: Boolean,
    val isPermanentlyDenied: Boolean,
    val requestOrOpenSettings: () -> Unit,
)

@Composable
internal fun rememberOverlayCameraPermissionHolder(autoRequestOnEnter: Boolean = true): OverlayCameraPermissionHolder {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    var hasRequested by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = hasCameraPermission(context)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequested = true
        hasPermission = granted
    }

    LaunchedEffect(autoRequestOnEnter) {
        if (autoRequestOnEnter && !hasPermission && !hasRequested) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val activity = remember(context) { context.findHostActivity() }
    val isPermanentlyDenied = !hasPermission &&
        hasRequested &&
        activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

    return OverlayCameraPermissionHolder(
        hasPermission = hasPermission,
        isPermanentlyDenied = isPermanentlyDenied,
        requestOrOpenSettings = {
            if (isPermanentlyDenied) {
                openApplicationDetails(context)
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        },
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private fun openApplicationDetails(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@Composable
internal fun OverlayIconActionPill(
    text: String,
    icon: @Composable () -> Unit,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) Color.White else OverlayChipBackground,
        contentColor = if (highlighted) Color(0xFF101418) else Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (highlighted) 0f else 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
