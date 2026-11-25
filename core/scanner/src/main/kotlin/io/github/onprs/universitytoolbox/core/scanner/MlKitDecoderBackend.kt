package io.github.onprs.universitytoolbox.core.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.core.common.dispatcher.IoDispatcher
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.result.runCatchingApp
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 基于 Google ML Kit Barcode Scanning 的默认条码解码后端。
 */
@Singleton
class MlKitDecoderBackend @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val timeProvider: TimeProvider,
) : DecoderBackend {
    override val name: String = "mlkit-barcode"

    override suspend fun decodeBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> = withContext(ioDispatcher) {
        runCatchingApp(
            errorMapper = { throwable ->
                DomainError.Unknown(
                    message = throwable.message ?: "Failed to decode bitmap barcode",
                    cause = throwable,
                )
            },
        ) {
            val image = InputImage.fromBitmap(bitmap, 0)
            processInputImage(image)
        }
    }

    override suspend fun decodeImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> = withContext(ioDispatcher) {
        runCatchingApp(
            errorMapper = { throwable ->
                DomainError.Unknown(
                    message = throwable.message ?: "Failed to decode URI barcode",
                    cause = throwable,
                )
            },
        ) {
            val sampledBitmap = decodeDownsampledBitmapIfOversized(uri)
            if (sampledBitmap != null) {
                try {
                    processInputImage(InputImage.fromBitmap(sampledBitmap, 0))
                } finally {
                    sampledBitmap.recycle()
                }
            } else {
                val image = InputImage.fromFilePath(context, uri)
                processInputImage(image)
            }
        }
    }

    private fun decodeDownsampledBitmapIfOversized(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || maxOf(width, height) <= MAX_STATIC_DECODE_DIMENSION_PX) {
            return null
        }
        val sampleSize = calculateInSampleSize(width, height, MAX_STATIC_DECODE_DIMENSION_PX)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }.getOrNull()

    private suspend fun processInputImage(image: InputImage): List<BarcodeScanResult> {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val client = BarcodeScanning.getClient(options)
        return try {
            val barcodes = client.process(image).awaitResult()
            val timestamp = timeProvider.currentTimeMillis()
            barcodes.mapNotNull { barcode ->
                BarcodeFormatMapper.toScanResult(
                    barcode = barcode,
                    detectedAtEpochMs = timestamp,
                    sourceMode = BarcodeScannerMode.STATIC_IMAGE,
                )
            }
        } finally {
            client.close()
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }

    internal companion object {
        const val MAX_STATIC_DECODE_DIMENSION_PX = 2048

        fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
            var sampleSize = 1
            while (maxOf(width / sampleSize, height / sampleSize) > maxDimension) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}
