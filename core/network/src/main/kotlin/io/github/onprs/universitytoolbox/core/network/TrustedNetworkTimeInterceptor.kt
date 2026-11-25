package io.github.onprs.universitytoolbox.core.network

import io.github.onprs.universitytoolbox.core.common.dispatcher.ApplicationScope
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 可信网络时间同步拦截器：从 HTTPS 响应头 `Date` 提取服务端 UTC 时间并同步至 [LicenseEngine]。
 * 通过注入 [Provider] 打破潜在的 Hilt 依赖环，并内置节流阀避免并发请求重复触发磁盘写入。
 */
@Singleton
class TrustedNetworkTimeInterceptor internal constructor(
    private val licenseEngineProvider: Provider<LicenseEngine>,
    private val scope: CoroutineScope,
    private val requireHttps: Boolean,
    private val minSyncIntervalSec: Long,
) : Interceptor {

    @Inject
    constructor(
        licenseEngineProvider: Provider<LicenseEngine>,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        licenseEngineProvider = licenseEngineProvider,
        scope = scope,
        requireHttps = true,
        minSyncIntervalSec = DEFAULT_MIN_SYNC_INTERVAL_SEC,
    )

    private val lastSyncedEpochSec = AtomicLong(Long.MIN_VALUE)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!requireHttps || request.isHttps) {
            val dateHeader = response.header(HEADER_DATE)
            val parsedEpochSec = parseHttpDateEpochSeconds(dateHeader)
            if (parsedEpochSec != null && shouldSync(parsedEpochSec)) {
                scope.launch {
                    licenseEngineProvider.get().syncTrustedNetworkTime(parsedEpochSec)
                }
            }
        }
        return response
    }

    private fun shouldSync(epochSec: Long): Boolean {
        while (true) {
            val previous = lastSyncedEpochSec.get()
            if (previous != Long.MIN_VALUE && kotlin.math.abs(epochSec - previous) < minSyncIntervalSec) {
                return false
            }
            if (lastSyncedEpochSec.compareAndSet(previous, epochSec)) {
                return true
            }
        }
    }

    companion object {
        private const val HEADER_DATE = "Date"
        private const val DEFAULT_MIN_SYNC_INTERVAL_SEC = 30L

        /**
         * 将 HTTP RFC 1123 格式 `Date` 响应头解析为 UTC 秒级时间戳；格式无效时返回 null。
         */
        fun parseHttpDateEpochSeconds(headerValue: String?): Long? {
            if (headerValue.isNullOrBlank()) {
                return null
            }
            return try {
                ZonedDateTime.parse(headerValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
