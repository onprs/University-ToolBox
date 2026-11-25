package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 跨设备多账号导入分享载荷（携带传输态加密密码与统一签到设备码）。
 */
data class ChaoxingSharedAccountPayload(
    val accountInput: String,
    val encryptedPassword: String,
    val displayName: String = "",
    val fid: String = ChaoxingEndpointConfig.DEFAULT_FID,
    val deviceCode: String = "",
    val uniqueId: String = "",
    val deviceModel: String = "",
)

/**
 * 学习通账号绑定分享链接与二维码编解码器。
 * 支持生成 `universitytoolbox://chaoxing-bind` 标准深链，并兼容解析携带同款参数的二维码或分享链接。
 */
object ChaoxingSharedAccountCodec {
    const val SCHEME_TOOLBOX = "universitytoolbox"
    const val HOST_CHAOXING_BIND = "chaoxing-bind"

    private const val DEFAULT_QR_SIZE_PX = 640
    private const val QR_MARGIN_MODULES = 1
    private const val PNG_COMPRESS_QUALITY = 100
    private val URL_CANDIDATE_REGEX = Regex("(?:universitytoolbox|https?)://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    /** 将账号分享载荷编码为标准深链字符串。 */
    fun encodeShareUrl(payload: ChaoxingSharedAccountPayload): String = buildString {
        append("$SCHEME_TOOLBOX://$HOST_CHAOXING_BIND")
        append("?phone=").append(urlEncode(payload.accountInput.trim()))
        append("&pwd=").append(urlEncode(payload.encryptedPassword.trim()))
        if (payload.displayName.isNotBlank()) {
            append("&name=").append(urlEncode(payload.displayName.trim()))
        }
        if (payload.fid.isNotBlank() && payload.fid != ChaoxingEndpointConfig.DEFAULT_FID) {
            append("&fid=").append(urlEncode(payload.fid.trim()))
        }
        if (payload.deviceCode.isNotBlank()) {
            append("&dc=").append(urlEncode(payload.deviceCode.trim()))
        }
        if (payload.uniqueId.isNotBlank()) {
            append("&uid=").append(urlEncode(payload.uniqueId.trim()))
        }
        if (payload.deviceModel.isNotBlank()) {
            append("&dm=").append(urlEncode(payload.deviceModel.trim()))
        }
    }

    /**
     * 从二维码扫码内容、深链或包含链接的剪贴板文本中解析账号绑定载荷；格式不匹配时返回 null。
     */
    fun parseShareText(rawText: String?): ChaoxingSharedAccountPayload? {
        val trimmed = rawText?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }
        val extractedUrl = URL_CANDIDATE_REGEX.find(trimmed)?.value ?: trimmed
        val uri = runCatching { URI(extractedUrl) }.getOrNull() ?: return null
        val params = parseQueryMap(uri.rawQuery)
        val phone = params["phone"]?.trim().orEmpty()
        val encryptedPwd = params["pwd"]?.trim()?.replace(" ", "+").orEmpty()
        if (phone.isEmpty() || encryptedPwd.isEmpty()) {
            return null
        }
        return ChaoxingSharedAccountPayload(
            accountInput = phone,
            encryptedPassword = encryptedPwd,
            displayName = params["name"]?.trim().orEmpty(),
            fid = params["fid"]?.trim().orEmpty().ifBlank { ChaoxingEndpointConfig.DEFAULT_FID },
            deviceCode = params["dc"]?.trim()?.replace(" ", "+").orEmpty(),
            uniqueId = params["uid"]?.trim().orEmpty(),
            deviceModel = params["dm"]?.trim().orEmpty(),
        )
    }

    /** 将分享链接渲染为二维码 PNG 字节数组。 */
    fun generateQrPngBytes(content: String, sizePx: Int = DEFAULT_QR_SIZE_PX): ByteArray {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
            EncodeHintType.MARGIN to QR_MARGIN_MODULES,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val rowOffset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_COMPRESS_QUALITY, stream)
            stream.toByteArray()
        }
    }

    private fun parseQueryMap(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }
        val map = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { segment ->
            if (segment.isNotEmpty()) {
                val eqIndex = segment.indexOf('=')
                if (eqIndex > 0) {
                    val key = urlDecode(segment.substring(0, eqIndex)).trim()
                    val value = urlDecode(segment.substring(eqIndex + 1))
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlDecode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
