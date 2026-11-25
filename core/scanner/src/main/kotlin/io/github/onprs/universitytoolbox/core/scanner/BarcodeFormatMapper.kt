package io.github.onprs.universitytoolbox.core.scanner

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * 将 ML Kit 原生条码格式码与扫描结果转换为领域统一模型。
 */
object BarcodeFormatMapper {
    private val mlKitFormatMap = mapOf(
        Barcode.FORMAT_QR_CODE to BarcodeFormat.QR_CODE,
        Barcode.FORMAT_AZTEC to BarcodeFormat.AZTEC,
        Barcode.FORMAT_CODE_128 to BarcodeFormat.CODE_128,
        Barcode.FORMAT_CODE_39 to BarcodeFormat.CODE_39,
        Barcode.FORMAT_CODE_93 to BarcodeFormat.CODE_93,
        Barcode.FORMAT_CODABAR to BarcodeFormat.CODABAR,
        Barcode.FORMAT_DATA_MATRIX to BarcodeFormat.DATA_MATRIX,
        Barcode.FORMAT_EAN_13 to BarcodeFormat.EAN_13,
        Barcode.FORMAT_EAN_8 to BarcodeFormat.EAN_8,
        Barcode.FORMAT_ITF to BarcodeFormat.ITF,
        Barcode.FORMAT_PDF417 to BarcodeFormat.PDF_417,
        Barcode.FORMAT_UPC_A to BarcodeFormat.UPC_A,
        Barcode.FORMAT_UPC_E to BarcodeFormat.UPC_E,
    )

    /** 将 ML Kit [Barcode.getFormat] 映射为 [BarcodeFormat]。 */
    fun fromMlKitFormat(mlKitFormat: Int): BarcodeFormat = mlKitFormatMap[mlKitFormat] ?: BarcodeFormat.UNKNOWN

    /** 将 ML Kit [Barcode] 实体转换为 [BarcodeScanResult]；若原始值为空则返回 null。 */
    fun toScanResult(barcode: Barcode, detectedAtEpochMs: Long, sourceMode: BarcodeScannerMode): BarcodeScanResult? {
        val raw = barcode.rawValue?.takeIf { it.isNotBlank() } ?: return null
        return BarcodeScanResult(
            rawValue = raw,
            displayValue = barcode.displayValue?.takeIf { it.isNotBlank() },
            format = fromMlKitFormat(barcode.format),
            detectedAtEpochMs = detectedAtEpochMs,
            sourceMode = sourceMode,
        )
    }
}
