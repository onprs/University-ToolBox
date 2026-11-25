package io.github.onprs.universitytoolbox.core.scanner

/**
 * 统一条码/二维码格式枚举，屏蔽具体底层识别库（ML Kit / ZXing-C++）的枚举差异。
 */
enum class BarcodeFormat {
    QR_CODE,
    AZTEC,
    CODE_128,
    CODE_39,
    CODE_93,
    CODABAR,
    DATA_MATRIX,
    EAN_13,
    EAN_8,
    ITF,
    PDF_417,
    UPC_A,
    UPC_E,
    UNKNOWN,
}

/**
 * 扫码识别来源模式。
 */
enum class BarcodeScannerMode {
    LIVE_CAMERA,
    STATIC_IMAGE,
}

/**
 * 统一扫码识别结果模型。
 */
data class BarcodeScanResult(
    val rawValue: String,
    val displayValue: String?,
    val format: BarcodeFormat,
    val detectedAtEpochMs: Long,
    val sourceMode: BarcodeScannerMode,
)
