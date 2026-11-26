package io.github.onprs.universitytoolbox.core.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerLogicTest {
    @Test
    fun `format mapper translates mlkit formats and handles unknown fallback`() {
        assertEquals(BarcodeFormat.QR_CODE, BarcodeFormatMapper.fromMlKitFormat(Barcode.FORMAT_QR_CODE))
        assertEquals(BarcodeFormat.CODE_128, BarcodeFormatMapper.fromMlKitFormat(Barcode.FORMAT_CODE_128))
        assertEquals(BarcodeFormat.EAN_13, BarcodeFormatMapper.fromMlKitFormat(Barcode.FORMAT_EAN_13))
        assertEquals(BarcodeFormat.AZTEC, BarcodeFormatMapper.fromMlKitFormat(Barcode.FORMAT_AZTEC))
        assertEquals(BarcodeFormat.PDF_417, BarcodeFormatMapper.fromMlKitFormat(Barcode.FORMAT_PDF417))
        assertEquals(BarcodeFormat.UNKNOWN, BarcodeFormatMapper.fromMlKitFormat(99999))
    }

    @Test
    fun `debouncer drops blank values and suppresses duplicate scans within window`() {
        val clockMs = AtomicLong(10_000L)
        val debouncer = ScanResultDebouncer(
            debounceWindowMs = 1500L,
            timeProvider = TimeProvider { clockMs.get() },
        )

        val blankResult = BarcodeScanResult(
            rawValue = "   ",
            displayValue = null,
            format = BarcodeFormat.QR_CODE,
            detectedAtEpochMs = 10_000L,
            sourceMode = BarcodeScannerMode.LIVE_CAMERA,
        )
        assertNull(debouncer.filterEmit(blankResult))

        val firstQr = BarcodeScanResult(
            rawValue = "https://example.org/sign?id=42",
            displayValue = "https://example.org/sign?id=42",
            format = BarcodeFormat.QR_CODE,
            detectedAtEpochMs = 10_000L,
            sourceMode = BarcodeScannerMode.LIVE_CAMERA,
        )
        assertNotNull(debouncer.filterEmit(firstQr))

        // 窗口内重复帧被去重抑制
        clockMs.set(11_000L)
        assertNull(debouncer.filterEmit(firstQr.copy(detectedAtEpochMs = 11_000L)))

        // 不同内容立即放行
        val secondQr = firstQr.copy(rawValue = "ONPRS-KEY-SAMPLE", detectedAtEpochMs = 11_100L)
        assertNotNull(debouncer.filterEmit(secondQr))

        // 超过防抖窗口后允许重新识别相同条码
        clockMs.set(13_000L)
        assertNotNull(debouncer.filterEmit(secondQr.copy(detectedAtEpochMs = 13_000L)))

        // 重置后可立即再次回调
        debouncer.reset()
        assertNotNull(debouncer.filterEmit(secondQr.copy(detectedAtEpochMs = 13_100L)))
    }

    @Test
    fun `live scan dispatch gate throttles burst callbacks across adjacent frames`() {
        val clockMs = AtomicLong(20_000L)
        val gate = LiveScanDispatchGate(
            debounceWindowMs = 1500L,
            timeProvider = TimeProvider { clockMs.get() },
            minFrameIntervalMs = 400L,
        )

        val qrA = BarcodeScanResult(
            rawValue = "QR-A",
            displayValue = "QR-A",
            format = BarcodeFormat.QR_CODE,
            detectedAtEpochMs = 20_000L,
            sourceMode = BarcodeScannerMode.LIVE_CAMERA,
        )
        val qrB = qrA.copy(rawValue = "QR-B", displayValue = "QR-B")

        // 同一帧内存在多个条码时仅分发首个有效结果
        val firstAccepted = gate.acceptFirst(sequenceOf(qrA, qrB))
        assertEquals("QR-A", firstAccepted?.rawValue)

        // 紧邻帧（50ms 后）即使出现另一个条码也在帧冷却窗口内被抑制，防止弹窗关闭动画期间二次触发
        clockMs.set(20_050L)
        assertNull(gate.acceptFirst(sequenceOf(qrB)))

        // 冷却窗口结束后可正常扫描新条码
        clockMs.set(20_500L)
        assertEquals("QR-B", gate.acceptFirst(sequenceOf(qrB))?.rawValue)

        // 重新启用扫描重置门闩后可立即识别相同条码
        gate.reset()
        clockMs.set(20_550L)
        assertEquals("QR-B", gate.acceptFirst(sequenceOf(qrB))?.rawValue)
    }

    @Test
    fun `static image sample size scales down oversized dimensions by powers of two`() {
        assertEquals(1, MlKitDecoderBackend.calculateInSampleSize(1920, 1080, 2048))
        assertEquals(2, MlKitDecoderBackend.calculateInSampleSize(4000, 3000, 2048))
        assertEquals(4, MlKitDecoderBackend.calculateInSampleSize(8000, 6000, 2048))
    }

    @Test
    fun `camera zoom presets logarithmic slider and double tap cycle behave ergonomically`() {
        val (scanMin, scanMax) = CameraZoomCalculator.resolveBounds(0.6f, 20.0f, allowUltraWide = false)
        assertEquals(1.0f, scanMin, 0.001f)
        assertEquals(10.0f, scanMax, 0.001f)
        assertEquals(
            listOf(1.0f, 2.0f, 3.0f, 5.0f),
            CameraZoomCalculator.buildQuickPresets(0.6f, 20.0f, allowUltraWide = false),
        )
        assertEquals(
            listOf(0.6f, 1.0f, 2.0f, 3.0f, 5.0f),
            CameraZoomCalculator.buildQuickPresets(0.6f, 12.0f, allowUltraWide = true),
        )
        assertEquals(
            listOf(1.0f, 2.0f, 3.0f, 3.8f),
            CameraZoomCalculator.buildQuickPresets(1.0f, 3.8f, allowUltraWide = false),
        )

        // 对数滑块中点在 1x..9x 区间应落在几何均值 3.0x，保证低倍常用区间拥有充足拖动行程
        val midpointRatio = CameraZoomCalculator.sliderFractionToRatio(0.5f, 1.0f, 9.0f)
        assertEquals(3.0f, midpointRatio, 0.02f)
        val roundTripFraction = CameraZoomCalculator.ratioToSliderFraction(midpointRatio, 1.0f, 9.0f)
        assertEquals(0.5f, roundTripFraction, 0.01f)

        // 双击放大三段式循环：1.0x -> 2.5x -> 4.5x -> 1.0x
        val firstTap = CameraZoomCalculator.nextDoubleTapZoom(1.0f, 1.0f, 8.0f)
        assertEquals(2.5f, firstTap, 0.01f)
        val secondTap = CameraZoomCalculator.nextDoubleTapZoom(firstTap, 1.0f, 8.0f)
        assertEquals(4.5f, secondTap, 0.01f)
        val thirdTap = CameraZoomCalculator.nextDoubleTapZoom(secondTap, 1.0f, 8.0f)
        assertEquals(1.0f, thirdTap, 0.01f)

        // 双指乘法缩放平滑响应并限制在合法上下界
        val pinched = CameraZoomCalculator.applyPinchScale(1.5f, 2.0f, 1.0f, 8.0f)
        assertTrue(pinched > 3.0f && pinched < 3.5f)
        assertEquals(8.0f, CameraZoomCalculator.applyPinchScale(6.0f, 3.0f, 1.0f, 8.0f), 0.001f)
        assertEquals("2x", CameraZoomCalculator.formatZoomLabel(2.02f))
        assertEquals("2.4x", CameraZoomCalculator.formatZoomLabel(2.4f))
    }

    @Test
    fun `camera interactive state respects manual zoom cooldown over mlkit auto zoom suggestions`() {
        val clockMs = AtomicLong(50_000L)
        val state = CameraInteractiveState(
            allowUltraWide = false,
            timeProvider = TimeProvider { clockMs.get() },
        )

        // 初始无手动干预时，识别到远处小二维码的自动变焦建议立即生效（上限 5.0x）
        assertTrue(state.applyAutoSuggestedZoom(2.6f))
        assertEquals(2.6f, state.zoomRatio, 0.01f)

        // 自动变焦不会反向缩小画面
        assertFalse(state.applyAutoSuggestedZoom(1.5f))
        assertEquals(2.6f, state.zoomRatio, 0.01f)

        // 用户手动拖动滑块回到 1.5x 后，3.5 秒冷却窗口内拒绝自动变焦抢夺控制权
        state.setManualZoomRatio(1.5f)
        assertTrue(abs(state.zoomRatio - 1.5f) < 0.01f)
        clockMs.set(52_000L)
        assertFalse(state.applyAutoSuggestedZoom(3.2f))
        assertEquals(1.5f, state.zoomRatio, 0.01f)

        // 冷却窗口结束后恢复响应远距自动变焦并限制在 MAX_AUTO_SUGGESTED_ZOOM (5.0x)
        clockMs.set(54_000L)
        assertTrue(state.applyAutoSuggestedZoom(7.5f))
        assertEquals(CameraZoomCalculator.MAX_AUTO_SUGGESTED_ZOOM, state.zoomRatio, 0.01f)
    }
}
