package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 学习通专用 OkHttpClient 限定符（已禁用自动重定向并挂载多账号会话隔离拦截器）。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChaoxingHttpClient

/**
 * HTTP 文本响应轻量封装。
 */
internal data class HttpTextResponse(val code: Int, val body: String, val locationHeader: String?)

/**
 * 学习通 HTTP 执行器：统一处理协程取消、重定向检测与服务端异常码映射。
 */
@Singleton
class ChaoxingHttpExecutor @Inject constructor(@param:ChaoxingHttpClient private val httpClient: OkHttpClient) {
    /** 执行文本响应请求并映射网络异常与 5xx 服务端异常。 */
    internal suspend fun executeText(request: Request): AppResult<HttpTextResponse> {
        val response = try {
            executeCall(request)
        } catch (io: IOException) {
            return AppResult.Failure(DomainError.Network("网络请求失败：${io.message ?: "连接异常"}", cause = io))
        }
        return response.use { res ->
            val text = res.body.string()
            val location = res.header(HEADER_LOCATION)
            if (res.code >= HTTP_SERVER_ERROR_THRESHOLD) {
                AppResult.Failure(
                    DomainError.Network("服务端响应异常 (${res.code})", httpCode = res.code),
                )
            } else {
                AppResult.Success(
                    HttpTextResponse(
                        code = res.code,
                        body = text,
                        locationHeader = location,
                    ),
                )
            }
        }
    }

    /** 执行二进制响应请求（用于登录二维码或资源下载）。 */
    suspend fun executeBytes(request: Request): AppResult<ByteArray> {
        val response = try {
            executeCall(request)
        } catch (io: IOException) {
            return AppResult.Failure(DomainError.Network("网络请求失败：${io.message ?: "连接异常"}", cause = io))
        }
        return response.use { res ->
            if (!res.isSuccessful) {
                AppResult.Failure(
                    DomainError.Network("二进制资源请求失败 (${res.code})", httpCode = res.code),
                )
            } else {
                AppResult.Success(res.body.bytes())
            }
        }
    }

    /** 判断响应是否为登录重定向或返回了统一认证中心登录页 HTML（会话失效特征）。 */
    internal fun isSessionExpiredResponse(response: HttpTextResponse): Boolean {
        if (response.code in HTTP_REDIRECT_MIN..HTTP_REDIRECT_MAX) {
            val location = response.locationHeader.orEmpty()
            return location.contains("passport2", ignoreCase = true) ||
                location.contains("mlogin", ignoreCase = true) ||
                location.contains("/login", ignoreCase = true)
        }
        return ChaoxingSafeJson.looksLikeLoginHtml(response.body)
    }

    private suspend fun executeCall(request: Request): Response {
        val call = httpClient.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) {
                            continuation.resume(response)
                        } else {
                            response.close()
                        }
                    }
                },
            )
        }
    }

    companion object {
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_REFERER = "Referer"
        const val HEADER_ORIGIN = "Origin"
        const val HEADER_X_REQUESTED_WITH = "X-Requested-With"
        const val HEADER_P_AUTH_TOKEN = "p-auth-token"
        const val X_REQUESTED_WITH_AJAX = "XMLHttpRequest"

        private const val HEADER_LOCATION = "Location"
        private const val HTTP_REDIRECT_MIN = 300
        private const val HTTP_REDIRECT_MAX = 399
        private const val HTTP_SERVER_ERROR_THRESHOLD = 500
    }
}
