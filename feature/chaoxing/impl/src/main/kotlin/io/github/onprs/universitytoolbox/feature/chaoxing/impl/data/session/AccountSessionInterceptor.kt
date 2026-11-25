package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 基于 [AccountContext] Request Tag 的多账号会话拦截器。
 * 仅从当前请求绑定的 `accountId` 桶读取与写回 Cookie；未绑定 Tag 的请求不注入任何账号 Cookie。
 */
@Singleton
class AccountSessionInterceptor @Inject constructor(private val cookieVault: AccountCookieVault) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accountContext = originalRequest.tag(AccountContext::class.java)

        val outboundRequest = if (accountContext == null) {
            originalRequest.newBuilder()
                .removeHeader(HEADER_COOKIE)
                .build()
        } else {
            val cookieHeader = runBlocking {
                cookieVault.formatCookieHeader(accountContext.accountId, originalRequest.url)
            }
            originalRequest.newBuilder()
                .removeHeader(HEADER_COOKIE)
                .apply {
                    if (cookieHeader.isNotEmpty()) {
                        header(HEADER_COOKIE, cookieHeader)
                    }
                }
                .build()
        }

        val response = chain.proceed(outboundRequest)
        if (accountContext != null) {
            val receivedCookies = Cookie.parseAll(outboundRequest.url, response.headers)
            if (receivedCookies.isNotEmpty()) {
                runBlocking {
                    cookieVault.saveFromResponse(accountContext.accountId, receivedCookies)
                }
            }
        }
        return response
    }

    companion object {
        private const val HEADER_COOKIE = "Cookie"
    }
}
