package io.github.onprs.universitytoolbox.core.scanner

import android.graphics.Bitmap
import android.net.Uri
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 可替换的底层解码引擎扩展点。
 * 默认由 Google ML Kit 实现；需要去除专有依赖时可无缝替换为 ZXing-C++ 实现。
 */
interface DecoderBackend {
    val name: String

    suspend fun decodeBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>>

    suspend fun decodeImageUri(uri: Uri): AppResult<List<BarcodeScanResult>>
}

/**
 * 统一扫码服务契约，向业务工具与激活流程提供静态图片识别能力。
 */
interface BarcodeScanner {
    suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>>

    suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>>
}

/**
 * 基于可插拔 [DecoderBackend] 的默认 [BarcodeScanner] 实现。
 */
@Singleton
class DefaultBarcodeScanner @Inject constructor(private val backend: DecoderBackend) : BarcodeScanner {
    override suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> = backend.decodeImageUri(uri)

    override suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> = backend.decodeBitmap(bitmap)
}
