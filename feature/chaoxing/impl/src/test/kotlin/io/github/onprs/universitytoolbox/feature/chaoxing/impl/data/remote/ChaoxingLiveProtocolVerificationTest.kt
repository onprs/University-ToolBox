package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import androidx.test.core.app.ApplicationProvider
import io.github.onprs.universitytoolbox.core.common.dispatcher.DefaultDispatcherProvider
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountSessionInterceptor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.InMemoryAccountCookieStorage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 超星学习通真实在线协议只读验证测试。
 * 凭据严格从项目根目录 `.env` 动态读取；未配置 `.env` 或凭据缺失时通过 `Assume.assumeTrue` 优雅跳过。
 * 严格遵守只读核查边界：仅验证登录、课程列表与活动列表协议解析，绝不提交任何课堂写入签到。
 */
@RunWith(RobolectricTestRunner::class)
class ChaoxingLiveProtocolVerificationTest {
    @Before
    fun initOkHttp() {
        OkHttp.initialize(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `live read-only login courses and activities protocol verification`() = runTest {
        val env = loadDotEnvCredentials()
        val phone = env["CHAOXING_PHONE"].orEmpty().trim()
        val password = env["CHAOXING_PASSWORD"].orEmpty().trim()
        assumeTrue(
            "跳过在线协议验证：未检测到包含 CHAOXING_PHONE 与 CHAOXING_PASSWORD 的 .env 文件",
            phone.isNotEmpty() && password.isNotEmpty(),
        )

        val (cookieVault, authRemoteDataSource, remoteDataSource) = createClients()
        val loginResult = authRemoteDataSource.loginWithPassword(
            provisionalAccountId = "live_verify_session",
            accountInput = phone,
            plainPassword = password,
        )
        assertTrue("期望登录请求成功，实际: $loginResult", loginResult is AppResult.Success)
        val outcome = (loginResult as AppResult.Success).data
        assertTrue("期望登录返回 Authenticated，实际: $outcome", outcome is ChaoxingLoginOutcome.Authenticated)
        val auth = outcome as ChaoxingLoginOutcome.Authenticated

        assertTrue(
            "登录后必须完整保存 UID/_d/vc3/uf 核心认证 Cookie",
            cookieVault.hasRequiredSessionCookies(auth.accountId),
        )
        assertTrue("脱敏手机号不应包含原始号码", !auth.profile.maskedPhone.contains(phone))

        val coursesRes = remoteDataSource.fetchCourses(auth.accountId)
        assertTrue("期望课程列表拉取成功，实际: $coursesRes", coursesRes is AppResult.Success)
        val courses = (coursesRes as AppResult.Success).data
        assertTrue("验证账号至少应返回 1 门课程", courses.isNotEmpty())

        var inspectedDetailCount = 0
        for (course in courses.take(MAX_COURSES_TO_PROBE)) {
            val activeRes = remoteDataSource.fetchActivities(
                accountId = auth.accountId,
                fid = auth.profile.fid,
                courseId = course.courseId,
                clazzId = course.clazzId,
                cpi = course.cpi,
                showNotStarted = false,
            )
            assertTrue("课程活动列表请求应成功，实际: $activeRes", activeRes is AppResult.Success)
            val activities = (activeRes as AppResult.Success).data

            // 仅对已结束的签到活动执行只读 getPPTActiveInfo 详情探测，绝不调用任何提交接口
            val safeEndedSign = activities.firstOrNull {
                it.isSignRelated && it.activityStatus == ChaoxingActivityStatus.ENDED
            }
            if (safeEndedSign != null && inspectedDetailCount == 0) {
                val detailRes = remoteDataSource.fetchActiveDetail(auth.accountId, safeEndedSign.activeId)
                assertTrue("只读 getPPTActiveInfo 应解析成功，实际: $detailRes", detailRes is AppResult.Success)
                assertTrue((detailRes as AppResult.Success).data.activeId == safeEndedSign.activeId)
                inspectedDetailCount++
                break
            }
        }
        assertFalse(auth.accountId.isBlank())
    }

    private fun createClients(): Triple<AccountCookieVault, ChaoxingAuthRemoteDataSource, ChaoxingRemoteDataSource> {
        val cookieVault = AccountCookieVault(InMemoryAccountCookieStorage())
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(NETWORK_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(NETWORK_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(AccountSessionInterceptor(cookieVault))
            .build()
        val executor = ChaoxingHttpExecutor(okHttpClient)
        val endpointConfig = ChaoxingEndpointConfig()
        val htmlParser = ChaoxingHtmlParser()
        val authRemote = ChaoxingAuthRemoteDataSource(
            executor = executor,
            cookieVault = cookieVault,
            htmlParser = htmlParser,
            endpointConfig = endpointConfig,
            dispatchers = DefaultDispatcherProvider,
        )
        val courseRemote = ChaoxingRemoteDataSource(
            executor = executor,
            cookieVault = cookieVault,
            htmlParser = htmlParser,
            endpointConfig = endpointConfig,
            dispatchers = DefaultDispatcherProvider,
        )
        return Triple(cookieVault, authRemote, courseRemote)
    }

    private fun loadDotEnvCredentials(): Map<String, String> {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(MAX_PARENT_SEARCH_DEPTH) {
            val candidate = dir?.let { File(it, ".env") }
            if (candidate != null && candidate.isFile) {
                return parseEnvFile(candidate)
            }
            dir = dir?.parentFile
        }
        return emptyMap()
    }

    private fun parseEnvFile(file: File): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        file.readLines(Charsets.UTF_8).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) {
                return@forEach
            }
            val eqIndex = line.indexOf('=')
            if (eqIndex > 0) {
                val key = line.substring(0, eqIndex).trim()
                val value = line.substring(eqIndex + 1).trim().trim('"', '\'')
                result[key] = value
            }
        }
        return result
    }

    companion object {
        private const val NETWORK_TIMEOUT_SEC = 20L
        private const val MAX_COURSES_TO_PROBE = 5
        private const val MAX_PARENT_SEARCH_DEPTH = 6
    }
}
