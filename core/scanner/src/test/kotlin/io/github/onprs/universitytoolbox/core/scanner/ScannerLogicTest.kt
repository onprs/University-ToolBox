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
}
