package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 相册静态图片安全降采样解码工具，防止高分辨率原图全尺寸加载引发内存溢出。
 */
internal object SafeImageBitmapDecoder {
    const val DEFAULT_MAX_DIMENSION = 2048

    /**
     * 根据原始图片宽高计算 2 的幂次采样率，使解码后长边控制在 [maxDimension] 以内。
     */
    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int = DEFAULT_MAX_DIMENSION): Int {
        var sampleSize = 1
        if (width <= 0 || height <= 0 || maxDimension <= 0) {
            return sampleSize
        }
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * 在 IO 线程对指定 [uri] 执行两阶段边界探测与降采样解码，异常或内存不足时安全返回 `null`。
     */
    suspend fun decodeSampledBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) {
                return@runCatching null
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(rawWidth, rawHeight, maxDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()
    }
}
