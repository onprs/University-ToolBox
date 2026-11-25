package io.github.onprs.universitytoolbox.core.network

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 图片与地图瓦片加载兼容拦截器：
 * 1. 为未指定浏览器标识的静态图片请求补充移动端浏览器 User-Agent，避免超星等 CDN 对默认 okhttp 客户端标识返回 403；
 * 2. 为超星系静态图片域名自动补充同源 Referer，并将历史明文 HTTP 图片地址升级为 HTTPS。
 */
class ImageCompatRequestInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val upgradedUrl = upgradeInsecureKnownImageUrl(original.url)
        val builder = original.newBuilder().url(upgradedUrl)

        val existingUa = original.header(HEADER_USER_AGENT)
        if (existingUa.isNullOrBlank() || existingUa.startsWith(OKHTTP_UA_PREFIX, ignoreCase = true)) {
            builder.header(HEADER_USER_AGENT, MOBILE_BROWSER_USER_AGENT)
        }

        if (original.header(HEADER_REFERER).isNullOrBlank() && isChaoxingImageHost(upgradedUrl.host)) {
            builder.header(HEADER_REFERER, CHAOXING_IMAGE_REFERER)
        }

        return chain.proceed(builder.build())
    }

    companion object {
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_REFERER = "Referer"
        private const val OKHTTP_UA_PREFIX = "okhttp/"
        private const val SCHEME_HTTP = "http"
        private const val SCHEME_HTTPS = "https"

        const val CHAOXING_IMAGE_REFERER = "https://mooc2-ans.chaoxing.com/"
        const val MOBILE_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val CHAOXING_IMAGE_DOMAIN_SUFFIXES = listOf(
            "chaoxing.com",
            "cldisk.com",
            "cxwfw.com",
            "xuexitong.com",
        )

        internal fun isChaoxingImageHost(host: String): Boolean {
            val normalized = host.trim().lowercase()
            return CHAOXING_IMAGE_DOMAIN_SUFFIXES.any { suffix ->
                normalized == suffix || normalized.endsWith(".$suffix")
            }
        }

        internal fun upgradeInsecureKnownImageUrl(url: HttpUrl): HttpUrl {
            if (!url.scheme.equals(SCHEME_HTTP, ignoreCase = true)) {
                return url
            }
            return if (isChaoxingImageHost(url.host)) {
                url.newBuilder().scheme(SCHEME_HTTPS).build()
            } else {
                url
            }
        }
    }
}
