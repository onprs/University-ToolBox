package io.github.onprs.universitytoolbox.core.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
