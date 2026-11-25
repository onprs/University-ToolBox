package io.github.onprs.universitytoolbox.core.network

import io.github.onprs.universitytoolbox.core.common.security.Redactor
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * 脱敏网络日志拦截器：在输出调试请求与响应摘要前强制通过 [Redactor] 清理 Cookie、Token、密码及手机号。
 */
class RedactingLoggingInterceptor(private val level: Level = Level.BASIC, private val logger: (String) -> Unit = {}) :
    Interceptor {

    /** 日志详细程度级别。 */
    enum class Level {
        NONE,
        BASIC,
        HEADERS,
        BODY,
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (level == Level.NONE) {
            return chain.proceed(request)
        }

        val redactedUrl = Redactor.redactText(request.url.toString())
        logger("--> ${request.method} $redactedUrl")
        if (level == Level.HEADERS || level == Level.BODY) {
            logRedactedHeaders(request.headers)
            if (level == Level.BODY) {
                logRedactedRequestBody(request)
            }
        }

        val startNs = System.nanoTime()
        val response = chain.proceed(request)
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val message = response.message.let { if (it.isEmpty()) "" else " $it" }
        logger("<-- ${response.code}$message $redactedUrl (${tookMs}ms)")

        if (level == Level.HEADERS || level == Level.BODY) {
            logRedactedHeaders(response.headers)
            if (level == Level.BODY) {
                logRedactedResponseBody(response)
            }
        }

        return response
    }

    private fun logRedactedHeaders(headers: Headers) {
        for (index in 0 until headers.size) {
            val name = headers.name(index)
            val value = headers.value(index)
            if (name.lowercase() in SENSITIVE_HEADER_NAMES) {
                logger("$name: $REDACTED_VALUE")
            } else {
                logger(Redactor.redactText("$name: $value"))
            }
        }
    }

    private fun logRedactedRequestBody(request: Request) {
        val body = request.body ?: return
        if (!isPlaintextMediaType(body.contentType())) {
            logger("(binary request body ${body.contentLength()} bytes)")
            return
        }
        val buffer = Buffer()
        body.writeTo(buffer)
        val readCount = minOf(buffer.size, MAX_LOGGED_BODY_BYTES)
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val rawText = buffer.readString(readCount, charset)
        logger(Redactor.redactText(rawText))
    }

    private fun logRedactedResponseBody(response: Response) {
        val body = response.peekBody(MAX_LOGGED_BODY_BYTES)
        if (!isPlaintextMediaType(body.contentType())) {
            logger("(binary response body)")
            return
        }
        val charset: Charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val rawText = String(body.bytes(), charset)
        if (rawText.isNotEmpty()) {
            logger(Redactor.redactText(rawText))
        }
    }

    private fun isPlaintextMediaType(mediaType: MediaType?): Boolean {
        if (mediaType == null) {
            return true
        }
        val type = mediaType.type.lowercase()
        val subtype = mediaType.subtype.lowercase()
        return type == "text" ||
            subtype == "json" ||
            subtype == "xml" ||
            subtype == "x-www-form-urlencoded" ||
            subtype.endsWith("+json") ||
            subtype.endsWith("+xml")
    }

    companion object {
        private const val REDACTED_VALUE = "<REDACTED>"
        private const val MAX_LOGGED_BODY_BYTES = 8192L
        private val SENSITIVE_HEADER_NAMES = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-auth-token",
            "proxy-authorization",
        )
    }
}
