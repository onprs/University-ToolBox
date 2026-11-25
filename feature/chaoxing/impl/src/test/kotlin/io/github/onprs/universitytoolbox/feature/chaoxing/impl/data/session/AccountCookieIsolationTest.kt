package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountCookieIsolationTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `concurrent requests carry strictly isolated cookies per AccountContext with zero leakage`() = runTest {
        val alphaSeenCookies = CopyOnWriteArrayList<String>()
        val betaSeenCookies = CopyOnWriteArrayList<String>()
        val publicSeenCookies = CopyOnWriteArrayList<String>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cookieHeader = request.headers["Cookie"].orEmpty()
                val path = request.target
                return when {
                    path.startsWith("/login/alpha") -> buildSessionResponse("uid_alpha", "vc3_alpha_secret")
                    path.startsWith("/login/beta") -> buildSessionResponse("uid_beta", "vc3_beta_secret")
                    path.startsWith("/alpha/") -> {
                        alphaSeenCookies += cookieHeader
                        MockResponse.Builder().code(HTTP_OK).body("ok_alpha").build()
                    }
                    path.startsWith("/beta/") -> {
                        betaSeenCookies += cookieHeader
                        MockResponse.Builder().code(HTTP_OK).body("ok_beta").build()
                    }
                    else -> {
                        publicSeenCookies += cookieHeader
                        MockResponse.Builder().code(HTTP_OK).body("ok_public").build()
                    }
                }
            }
        }

        val vault = AccountCookieVault(InMemoryAccountCookieStorage())
        val client = OkHttpClient.Builder()
            .addInterceptor(AccountSessionInterceptor(vault))
            .build()

        // 两个账号分别登录建立隔离会话
        executeGet(client, server.url("/login/alpha").toString(), accountId = "acct_alpha")
        executeGet(client, server.url("/login/beta").toString(), accountId = "acct_beta")

        assertTrue(vault.hasRequiredSessionCookies("acct_alpha"))
        assertTrue(vault.hasRequiredSessionCookies("acct_beta"))

        // 并发高频请求交替验证绝对零串号
        coroutineScope {
            val jobs = (1..CONCURRENT_ROUNDS).flatMap { round ->
                listOf(
                    async {
                        executeGet(client, server.url("/alpha/course/$round").toString(), "acct_alpha")
                    },
                    async {
                        executeGet(client, server.url("/beta/course/$round").toString(), "acct_beta")
                    },
                    async {
                        executeGet(client, server.url("/public/unis/$round").toString(), accountId = null)
                    },
                )
            }
            jobs.awaitAll()
        }

        assertEquals(CONCURRENT_ROUNDS, alphaSeenCookies.size)
        assertEquals(CONCURRENT_ROUNDS, betaSeenCookies.size)
        assertEquals(CONCURRENT_ROUNDS, publicSeenCookies.size)

        alphaSeenCookies.forEach { cookie ->
            assertTrue("Alpha 请求必须携带 alpha Cookie", cookie.contains("uid_alpha"))
            assertTrue("Alpha 请求必须携带 alpha vc3", cookie.contains("vc3_alpha_secret"))
            assertFalse("Alpha 请求严禁泄漏 beta Cookie", cookie.contains("beta"))
        }
        betaSeenCookies.forEach { cookie ->
            assertTrue("Beta 请求必须携带 beta Cookie", cookie.contains("uid_beta"))
            assertTrue("Beta 请求必须携带 beta vc3", cookie.contains("vc3_beta_secret"))
            assertFalse("Beta 请求严禁泄漏 alpha Cookie", cookie.contains("alpha"))
        }
        publicSeenCookies.forEach { cookie ->
            assertTrue("无 AccountContext 请求严禁携带任何账号 Cookie", cookie.isEmpty())
        }
    }

    @Test
    fun `migrating and clearing account session leaves no leftover cookies`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                buildSessionResponse("uid_9001", "vc3_temp_9001")
        }

        val storage = InMemoryAccountCookieStorage()
        val vault = AccountCookieVault(storage)
        val client = OkHttpClient.Builder()
            .addInterceptor(AccountSessionInterceptor(vault))
            .build()

        executeGet(client, server.url("/login").toString(), accountId = "pending_1")
        assertTrue(vault.hasRequiredSessionCookies("pending_1"))

        vault.migrateAccountSession("pending_1", "cx_9001")
        assertFalse(vault.hasRequiredSessionCookies("pending_1"))
        assertTrue(vault.hasRequiredSessionCookies("cx_9001"))

        vault.clearAccount("cx_9001")
        assertFalse(vault.hasRequiredSessionCookies("cx_9001"))
        assertEquals(0, vault.getAllCookies("cx_9001").size)
    }

    private fun buildSessionResponse(uid: String, vc3: String): MockResponse = MockResponse.Builder()
        .code(HTTP_OK)
        .addHeader("Set-Cookie", "UID=$uid; Path=/")
        .addHeader("Set-Cookie", "_d=1700000000; Path=/")
        .addHeader("Set-Cookie", "vc3=$vc3; Path=/; HttpOnly")
        .addHeader("Set-Cookie", "uf=uf_$uid; Path=/; HttpOnly")
        .body("{\"status\":true}")
        .build()

    private fun executeGet(client: OkHttpClient, url: String, accountId: String?) {
        val request = Request.Builder()
            .url(url)
            .apply {
                if (accountId != null) {
                    tag(AccountContext::class.java, AccountContext(accountId))
                }
            }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(HTTP_OK, response.code)
        }
    }

    companion object {
        private const val HTTP_OK = 200
        private const val CONCURRENT_ROUNDS = 15
    }
}
