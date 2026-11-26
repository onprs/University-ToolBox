package io.github.onprs.universitytoolbox.core.scanner

import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ZoomState
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 相机缩放范围、对数滑块映射、快捷倍率档位与自动/手动变焦仲裁纯函数计算器。
 */
object CameraZoomCalculator {
    const val DEFAULT_MIN_ZOOM = 1.0f
    const val DEFAULT_MAX_ZOOM = 8.0f
    const val MAX_USABLE_ZOOM_RATIO = 10.0f
    const val MAX_AUTO_SUGGESTED_ZOOM = 5.0f
    const val MANUAL_ZOOM_COOLDOWN_MS = 3_500L
    const val ZOOM_STEP_DELTA = 0.5f

    private const val MIN_ULTRAWIDE_RATIO = 0.5f
    private const val ULTRAWIDE_PRESET_THRESHOLD = 0.85f
    private const val PINCH_EXPONENT = 1.12f
    private const val DOUBLE_TAP_MID_TARGET = 2.5f
    private const val DOUBLE_TAP_FAR_TARGET = 4.5f
    private const val DOUBLE_TAP_STAGE_ONE_CEILING = 1.75f
    private const val DOUBLE_TAP_STAGE_TWO_CEILING = 3.6f
    private const val DOUBLE_TAP_FAR_MIN_MAX_RATIO = 3.8f
    private const val AUTO_ZOOM_MIN_GAIN_FACTOR = 1.12f
    private const val INTEGER_DISPLAY_EPSILON = 0.05f
    private const val PRESET_DEDUP_THRESHOLD = 0.45f
    private const val ROUND_TENTHS_SCALE = 10f
    private const val INITIAL_PRESETS_CAPACITY = 6
    private const val MIN_SPAN_EPSILON = 1e-3f

    private val STANDARD_PRESET_CANDIDATES = floatArrayOf(1.0f, 2.0f, 3.0f, 5.0f)

    fun resolveBounds(rawMin: Float, rawMax: Float, allowUltraWide: Boolean = false): Pair<Float, Float> {
        val safeMax = if (rawMax.isFinite() && rawMax >= DEFAULT_MIN_ZOOM) {
            rawMax.coerceAtMost(MAX_USABLE_ZOOM_RATIO)
        } else {
            DEFAULT_MAX_ZOOM
        }
        val minFloor = if (allowUltraWide) MIN_ULTRAWIDE_RATIO else DEFAULT_MIN_ZOOM
        val safeMin = if (rawMin.isFinite() && rawMin > 0f) {
            rawMin.coerceIn(minFloor, DEFAULT_MIN_ZOOM.coerceAtMost(safeMax))
        } else {
            DEFAULT_MIN_ZOOM.coerceAtMost(safeMax)
        }
        return safeMin to safeMax.coerceAtLeast(safeMin)
    }

    fun buildQuickPresets(minRatio: Float, maxRatio: Float, allowUltraWide: Boolean = false): List<Float> {
        val (safeMin, safeMax) = resolveBounds(minRatio, maxRatio, allowUltraWide)
        val presets = ArrayList<Float>(INITIAL_PRESETS_CAPACITY)
        if (allowUltraWide && safeMin <= ULTRAWIDE_PRESET_THRESHOLD) {
            presets += roundTenths(safeMin)
        }
        for (candidate in STANDARD_PRESET_CANDIDATES) {
            if (candidate in safeMin..safeMax) {
                presets += candidate
            }
        }
        if (presets.isEmpty()) {
            presets += roundTenths(safeMin)
        }
        val roundedMax = roundTenths(safeMax)
        val last = presets.last()
        if (roundedMax <= STANDARD_PRESET_CANDIDATES.last() &&
            roundedMax - last >= PRESET_DEDUP_THRESHOLD
        ) {
            presets += roundedMax
        }
        return presets
    }

    fun applyPinchScale(currentRatio: Float, scaleFactor: Float, minRatio: Float, maxRatio: Float): Float {
        val (safeMin, safeMax) = resolveBounds(minRatio, maxRatio, allowUltraWide = minRatio < DEFAULT_MIN_ZOOM)
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) {
            return currentRatio.coerceIn(safeMin, safeMax)
        }
        val scaled = currentRatio.coerceIn(safeMin, safeMax) * scaleFactor.pow(PINCH_EXPONENT)
        return scaled.coerceIn(safeMin, safeMax)
    }

    fun nextDoubleTapZoom(currentRatio: Float, minRatio: Float, maxRatio: Float): Float {
        val (safeMin, safeMax) = resolveBounds(minRatio, maxRatio, allowUltraWide = minRatio < DEFAULT_MIN_ZOOM)
        val baseOneX = DEFAULT_MIN_ZOOM.coerceIn(safeMin, safeMax)
        if (safeMax <= baseOneX + INTEGER_DISPLAY_EPSILON) {
            return baseOneX
        }
        return when {
            currentRatio < DOUBLE_TAP_STAGE_ONE_CEILING ->
                minOf(DOUBLE_TAP_MID_TARGET, safeMax)
            currentRatio < DOUBLE_TAP_STAGE_TWO_CEILING && safeMax >= DOUBLE_TAP_FAR_MIN_MAX_RATIO ->
                minOf(DOUBLE_TAP_FAR_TARGET, safeMax)
            else -> baseOneX
        }
    }

    fun stepZoom(currentRatio: Float, deltaRatio: Float, minRatio: Float, maxRatio: Float): Float {
        val (safeMin, safeMax) = resolveBounds(minRatio, maxRatio, allowUltraWide = minRatio < DEFAULT_MIN_ZOOM)
        return (currentRatio + deltaRatio).coerceIn(safeMin, safeMax)
    }

    fun ratioToSliderFraction(ratio: Float, minRatio: Float, maxRatio: Float): Float {
        val (safeMin, safeMax) = resolveBounds(minRatio, maxRatio, allowUltraWide = minRatio < DEFAULT_MIN_ZOOM)
        if (safeMax - safeMin <= MIN_SPAN_EPSILON) {
            return 0f
        }
        val clamped = ratio.coerceIn(safeMin, safeMax)
        return (ln(clamped / safeMin) / ln(safeMax / safeMin)).coerceIn(0f, 1f)
    }

    fun sliderFractionToRatio(fraction: Float, minRatio: Float, maxRatio: Float): Float {
        val (safeMin, safeMax) = resolveBounds(minRatio, maxRatio, allowUltraWide = minRatio < DEFAULT_MIN_ZOOM)
        if (safeMax - safeMin <= MIN_SPAN_EPSILON) {
            return safeMin
        }
        val safeFraction = fraction.coerceIn(0f, 1f)
        return (safeMin * (safeMax / safeMin).pow(safeFraction)).coerceIn(safeMin, safeMax)
    }

    fun resolveAutoSuggestedZoom(
        currentRatio: Float,
        suggestedRatio: Float,
        minRatio: Float,
        maxRatio: Float,
        lastManualZoomAtMs: Long,
        nowMs: Long,
    ): Float? {
        if (!suggestedRatio.isFinite() || suggestedRatio <= 0f) {
            return null
        }
        if (lastManualZoomAtMs > 0L && nowMs - lastManualZoomAtMs in 0 until MANUAL_ZOOM_COOLDOWN_MS) {
            return null
        }
        val (safeMin, safeMax) = resolveBounds(minRatio, maxRatio, allowUltraWide = false)
        val cappedMax = minOf(safeMax, MAX_AUTO_SUGGESTED_ZOOM)
        val target = suggestedRatio.coerceIn(safeMin, cappedMax)
        return if (target >= currentRatio * AUTO_ZOOM_MIN_GAIN_FACTOR) target else null
    }

    fun formatZoomLabel(ratio: Float): String {
        val safe = if (ratio.isFinite() && ratio > 0f) ratio else DEFAULT_MIN_ZOOM
        val roundedInt = safe.roundToInt()
        return if (abs(safe - roundedInt) < INTEGER_DISPLAY_EPSILON) {
            "${roundedInt}x"
        } else {
            String.format(Locale.US, "%.1fx", safe)
        }
    }

    private fun roundTenths(value: Float): Float = (value * ROUND_TENTHS_SCALE).roundToInt() / ROUND_TENTHS_SCALE
}

/**
 * 实时相机交互控制器状态（统一驱动双指缩放、双击放大、单手滑块、快捷倍率档位与点按对焦测光）。
 */
@Stable
class CameraInteractiveState(
    val allowUltraWide: Boolean = false,
    private val timeProvider: TimeProvider = TimeProvider.System,
) {
    var minZoomRatio by mutableFloatStateOf(CameraZoomCalculator.DEFAULT_MIN_ZOOM)
        private set
    var maxZoomRatio by mutableFloatStateOf(CameraZoomCalculator.DEFAULT_MAX_ZOOM)
        private set
    var zoomRatio by mutableFloatStateOf(CameraZoomCalculator.DEFAULT_MIN_ZOOM)
        private set
    var hasFlashUnit by mutableStateOf(true)
        private set
    var focusTapOffset by mutableStateOf<Offset?>(null)
        private set
    var focusTapSequence by mutableLongStateOf(0L)
        private set

    @Volatile
    var lastManualZoomAtMs: Long = 0L
        private set

    private var controllerRef = WeakReference<LifecycleCameraController>(null)
    private var previewViewRef = WeakReference<PreviewView>(null)

    val canZoom: Boolean
        get() = maxZoomRatio - minZoomRatio > 0.08f

    val sliderFraction: Float
        get() = CameraZoomCalculator.ratioToSliderFraction(zoomRatio, minZoomRatio, maxZoomRatio)

    val quickPresets: List<Float>
        get() = CameraZoomCalculator.buildQuickPresets(minZoomRatio, maxZoomRatio, allowUltraWide)

    internal fun attachCamera(controller: LifecycleCameraController, previewView: PreviewView?) {
        controllerRef = WeakReference(controller)
        if (previewView != null) {
            previewViewRef = WeakReference(previewView)
        }
        hasFlashUnit = controller.cameraInfo?.hasFlashUnit() ?: true
    }

    internal fun attachPreviewView(previewView: PreviewView) {
        previewViewRef = WeakReference(previewView)
    }

    internal fun onHardwareZoomStateChanged(state: ZoomState?) {
        if (state == null) return
        val (resolvedMin, resolvedMax) = CameraZoomCalculator.resolveBounds(
            rawMin = state.minZoomRatio,
            rawMax = state.maxZoomRatio,
            allowUltraWide = allowUltraWide,
        )
        minZoomRatio = resolvedMin
        maxZoomRatio = resolvedMax
        zoomRatio = state.zoomRatio.coerceIn(resolvedMin, resolvedMax)
    }

    internal fun refreshFlashCapability() {
        hasFlashUnit = controllerRef.get()?.cameraInfo?.hasFlashUnit() ?: true
    }

    fun setManualZoomRatio(targetRatio: Float) {
        val clamped = targetRatio.coerceIn(minZoomRatio, maxZoomRatio)
        lastManualZoomAtMs = timeProvider.currentTimeMillis()
        zoomRatio = clamped
        runCatching { controllerRef.get()?.setZoomRatio(clamped) }
    }

    fun onPinchScale(scaleFactor: Float) {
        val next = CameraZoomCalculator.applyPinchScale(zoomRatio, scaleFactor, minZoomRatio, maxZoomRatio)
        setManualZoomRatio(next)
    }

    fun onDoubleTapZoom() {
        val next = CameraZoomCalculator.nextDoubleTapZoom(zoomRatio, minZoomRatio, maxZoomRatio)
        setManualZoomRatio(next)
    }

    fun stepZoomBy(deltaRatio: Float = CameraZoomCalculator.ZOOM_STEP_DELTA) {
        val next = CameraZoomCalculator.stepZoom(zoomRatio, deltaRatio, minZoomRatio, maxZoomRatio)
        setManualZoomRatio(next)
    }

    fun onSliderFractionChanged(fraction: Float) {
        val next = CameraZoomCalculator.sliderFractionToRatio(fraction, minZoomRatio, maxZoomRatio)
        setManualZoomRatio(next)
    }

    internal fun applyAutoSuggestedZoom(suggestedRatio: Float): Boolean {
        val nowMs = timeProvider.currentTimeMillis()
        val resolved = CameraZoomCalculator.resolveAutoSuggestedZoom(
            currentRatio = zoomRatio,
            suggestedRatio = suggestedRatio,
            minRatio = minZoomRatio,
            maxRatio = maxZoomRatio,
            lastManualZoomAtMs = lastManualZoomAtMs,
            nowMs = nowMs,
        ) ?: return false
        zoomRatio = resolved
        runCatching { controllerRef.get()?.setZoomRatio(resolved) }
        return true
    }

    fun triggerFocusAndMetering(tapOffset: Offset) {
        focusTapOffset = tapOffset
        focusTapSequence += 1L
        val preview = previewViewRef.get() ?: return
        val cameraControl = controllerRef.get()?.cameraControl ?: return
        runCatching {
            val point = preview.meteringPointFactory.createPoint(tapOffset.x, tapOffset.y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
            )
                .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
                .build()
            cameraControl.startFocusAndMetering(action)
        }
    }

    fun clearFocusIndicator() {
        focusTapOffset = null
    }

    companion object {
        private const val FOCUS_AUTO_CANCEL_SECONDS = 3L
    }
}

@Composable
fun rememberCameraInteractiveState(allowUltraWide: Boolean = false): CameraInteractiveState =
    remember(allowUltraWide) { CameraInteractiveState(allowUltraWide = allowUltraWide) }

private const val DOUBLE_TAP_TIMEOUT_MS = 260L
private const val DOUBLE_TAP_MAX_SLOP_DP = 44f
private const val TAP_MOVEMENT_SLOP_DP = 18f
private const val PINCH_DELTA_EPSILON = 0.002f

/**
 * 统一处理相机全屏取景手势：双指无死区丝滑缩放、双击快速切换远景倍率、单击即时对焦测光。
 */
@Composable
fun Modifier.cameraViewportGestures(interactiveState: CameraInteractiveState): Modifier {
    val currentState by rememberUpdatedState(interactiveState)
    return pointerInput(Unit) {
        detectCameraGestures(
            onPinchZoom = { scale -> currentState.onPinchScale(scale) },
            onSingleTap = { offset -> currentState.triggerFocusAndMetering(offset) },
            onDoubleTap = { currentState.onDoubleTapZoom() },
        )
    }
}

private suspend fun PointerInputScope.detectCameraGestures(
    onPinchZoom: (Float) -> Unit,
    onSingleTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
) {
    val tapSlopPx = TAP_MOVEMENT_SLOP_DP.dp.toPx()
    val doubleTapSlopPx = DOUBLE_TAP_MAX_SLOP_DP.dp.toPx()
    val tapTracker = DoubleTapTracker(doubleTapSlopPx)

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPos = down.position
        var isMultiTouch = false
        var exceededTapSlop = false
        var upPos = downPos

        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                isMultiTouch = true
                consumePinchZoomEvent(event, onPinchZoom)
            } else if (!isMultiTouch && event.changes.isNotEmpty()) {
                upPos = event.changes.first().position
                if ((upPos - downPos).getDistance() > tapSlopPx) {
                    exceededTapSlop = true
                }
            }
        } while (event.changes.any { it.pressed })

        if (!isMultiTouch && !exceededTapSlop) {
            tapTracker.onTapReleased(upPos, onSingleTap, onDoubleTap)
        }
    }
}

private fun consumePinchZoomEvent(
    event: androidx.compose.ui.input.pointer.PointerEvent,
    onPinchZoom: (Float) -> Unit,
) {
    val zoomChange = event.calculateZoom()
    if (zoomChange.isFinite() && abs(zoomChange - 1f) > PINCH_DELTA_EPSILON) {
        onPinchZoom(zoomChange)
    }
    event.changes.forEach { change ->
        if (change.positionChanged()) {
            change.consume()
        }
    }
}

private class DoubleTapTracker(private val doubleTapSlopPx: Float) {
    private var lastTapUpTimeMs: Long = 0L
    private var lastTapUpPos: Offset = Offset.Zero

    fun onTapReleased(upPos: Offset, onSingleTap: (Offset) -> Unit, onDoubleTap: () -> Unit) {
        val nowMs = System.currentTimeMillis()
        val isDoubleTap = lastTapUpTimeMs > 0L &&
            nowMs - lastTapUpTimeMs <= DOUBLE_TAP_TIMEOUT_MS &&
            (upPos - lastTapUpPos).getDistance() <= doubleTapSlopPx
        if (isDoubleTap) {
            lastTapUpTimeMs = 0L
            onDoubleTap()
        } else {
            lastTapUpTimeMs = nowMs
            lastTapUpPos = upPos
            onSingleTap(upPos)
        }
    }
}
