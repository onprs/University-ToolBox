package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.onprs.universitytoolbox.core.common.dispatcher.DefaultDispatcherProvider
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCredentialCipher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingDatabase
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAuthRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingHtmlParser
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingHttpExecutor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingLoginCipher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountSessionInterceptor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.InMemoryAccountCookieStorage
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device.ChaoxingSharedAccountCodec
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device.ChaoxingSharedAccountPayload
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit.ChaoxingRateLimitCoordinator
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit.JitterStrategy
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit.TokenBucketRateLimiter
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.CodeAndGestureSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.LocationSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.NormalSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.PhotoSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.QrCodeSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignAccountIdentity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionRequest
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignMissingField
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignStrategyDispatcher
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChaoxingQuotaAndSignStrategyTest {
    private lateinit var server: MockWebServer
    private lateinit var database: ChaoxingDatabase
    private lateinit var licenseEngine: FakeLicenseEngine
    private lateinit var accountManager: ChaoxingAccountManager
    private lateinit var signDispatcher: SignStrategyDispatcher
    private lateinit var batchCoordinator: MultiAccountSignCoordinator

    private var currentActiveInfoJson: String = activeInfoJson(otherId = 0, ifPhoto = 0, needVcode = 0)
    private val recordedRequests = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        configureMockDispatcher()

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChaoxingDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        licenseEngine = FakeLicenseEngine()
        val licenseCoordinator = LicenseCoordinator(licenseEngine, TimeProvider { FIXED_NOW_MS })
        val cookieVault = AccountCookieVault(InMemoryAccountCookieStorage())
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(AccountSessionInterceptor(cookieVault))
            .build()
        val baseUrl = server.url("/").toString().trimEnd('/')
        val endpoints = ChaoxingEndpointConfig(
            passportBaseUrl = baseUrl,
            ssoBaseUrl = baseUrl,
            mooc1ApiBaseUrl = baseUrl,
            mooc2AnsBaseUrl = baseUrl,
            mobilelearnBaseUrl = baseUrl,
            panYzBaseUrl = baseUrl,
            captchaBaseUrl = baseUrl,
            photoBaseUrl = baseUrl,
        )
        val executor = ChaoxingHttpExecutor(client)
        val htmlParser = ChaoxingHtmlParser()
        val authRemote = ChaoxingAuthRemoteDataSource(
            executor = executor,
            cookieVault = cookieVault,
            htmlParser = htmlParser,
            endpointConfig = endpoints,
            dispatchers = DefaultDispatcherProvider,
        )
        val remote = ChaoxingRemoteDataSource(
            executor = executor,
            cookieVault = cookieVault,
            htmlParser = htmlParser,
            endpointConfig = endpoints,
            dispatchers = DefaultDispatcherProvider,
        )

        val testCipher = object : ChaoxingCredentialCipher {
            override suspend fun encrypt(plainText: String, associatedData: String): String =
                "enc($associatedData):$plainText"

            override suspend fun decrypt(cipherTextBase64: String, associatedData: String): String =
                cipherTextBase64.substringAfter(":")
        }

        initCoordinators(licenseCoordinator, cookieVault, authRemote, remote, testCipher)
    }

    private fun initCoordinators(
        licenseCoordinator: LicenseCoordinator,
        cookieVault: AccountCookieVault,
        authRemote: ChaoxingAuthRemoteDataSource,
        remote: ChaoxingRemoteDataSource,
        testCipher: ChaoxingCredentialCipher,
    ) {
        accountManager = ChaoxingAccountManager(
            accountDao = database.accountDao(),
            courseDao = database.courseDao(),
            authRemoteDataSource = authRemote,
            remoteDataSource = remote,
            cookieVault = cookieVault,
            credentialCipher = testCipher,
            licenseCoordinator = licenseCoordinator,
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )

        signDispatcher = SignStrategyDispatcher(
            remoteDataSource = remote,
            handlers = setOf(
                NormalSignActionHandler(remote),
                PhotoSignActionHandler(remote),
                QrCodeSignActionHandler(remote),
                LocationSignActionHandler(remote),
                CodeAndGestureSignActionHandler(remote),
            ),
        )

        val rateLimiter = ChaoxingRateLimitCoordinator(
            globalLimiter = TokenBucketRateLimiter(
                capacity = RATE_LIMIT_CAPACITY,
                refillTokens = RATE_LIMIT_CAPACITY,
                refillPeriodMs = RATE_LIMIT_PERIOD_MS,
                timeProvider = TimeProvider { FIXED_NOW_MS },
            ),
            jitterStrategy = JitterStrategy { _, _ -> 0L },
            delayFn = {},
        )

        batchCoordinator = MultiAccountSignCoordinator(
            accountDao = database.accountDao(),
            courseDao = database.courseDao(),
            signRecordDao = database.signRecordDao(),
            remoteDataSource = remote,
            strategyDispatcher = signDispatcher,
            rateLimitCoordinator = rateLimiter,
            licenseCoordinator = licenseCoordinator,
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    @Test
    fun `enforces license tier gate and maxSubAccounts quota when binding accounts`() = runTest {
        // 1. 未激活态拒绝访问
        licenseEngine.setState(LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-01"))
        assertTrue(accountManager.bindWithPassword("13800000001", "pwd") is AccountBindResult.LicenseDenied)

        // 2. FREE 等级不足以访问学习通（STANDARD 级）
        licenseEngine.setUnlocked(LicenseTier.FREE, maxSubAccounts = 0)
        assertTrue(accountManager.bindWithPassword("13800000001", "pwd") is AccountBindResult.LicenseDenied)

        // 3. STANDARD（允许 1 个子账号）：主账号 + 1 个子账号成功，第 2 个子账号被配额门禁拦截
        licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 1)
        val primaryBind = accountManager.bindWithPassword("13800000001", "pwd1")
        assertTrue(primaryBind is AccountBindResult.Bound && primaryBind.account.isPrimary)

        val subBind = accountManager.bindWithPassword("13800000002", "pwd2")
        assertTrue(subBind is AccountBindResult.Bound && !subBind.account.isPrimary)

        val exceeded = accountManager.bindWithPassword("13800000003", "pwd3")
        assertEquals(AccountBindResult.QuotaExceeded(maxSubAccounts = 1, currentSubAccounts = 1), exceeded)

        // 4. 重复刷新已绑定账号不消耗新增配额
        val refreshPrimary = accountManager.bindWithPassword("13800000001", "pwd1")
        assertTrue(refreshPrimary is AccountBindResult.Bound)
    }

    @Test
    fun `dispatches normal photo qr location gesture signout and captcha strategies accurately`() = runTest {
        val identity = SignAccountIdentity(
            accountId = "cx_9001",
            uid = "9001",
            fid = "100",
            maskedAccount = "138****0001",
            displayName = "同学甲",
        )
        val baseReq = SignExecutionRequest(courseId = 2001L, clazzId = 1001L, activeId = 7001L)

        // 普通签到
        currentActiveInfoJson = activeInfoJson(otherId = 0, ifPhoto = 0, needVcode = 0)
        assertTrue(signDispatcher.dispatch(identity, baseReq) is SignExecutionResult.Success)

        // 拍照签退（activeType = 74, ifphoto = 1）缺图挂起 -> 传图后上传并提交成功
        currentActiveInfoJson = activeInfoJson(activeType = 74, otherId = 0, ifPhoto = 1, needVcode = 0)
        val missingPhoto = signDispatcher.dispatch(identity, baseReq)
        assertEquals(
            SignExecutionResult.RequiresExtraInput(SignMissingField.PHOTO_IMAGE, "该签到需要上传现场照片"),
            missingPhoto,
        )
        val photoRes = signDispatcher.dispatch(
            identity,
            baseReq.copy(photoBytes = byteArrayOf(1, 2, 3), photoFileName = "proof.jpg"),
        )
        assertTrue(photoRes is SignExecutionResult.Success)
        assertTrue(recordedRequests.any { it.contains("objectId=obj_photo_99") })

        // 二维码签到
        currentActiveInfoJson = activeInfoJson(otherId = 2, ifPhoto = 0, needVcode = 0)
        val qrRes = signDispatcher.dispatch(
            identity,
            baseReq.copy(rawQrContent = "SIGNIN:aid=7001&source=15&Code=K1&enc=QR_ENC_88"),
        )
        assertTrue(qrRes is SignExecutionResult.Success)
        assertTrue(recordedRequests.any { it.contains("enc=QR_ENC_88") })

        // 位置签到
        currentActiveInfoJson = activeInfoJson(otherId = 4, ifPhoto = 0, needVcode = 0)
        val locRes = signDispatcher.dispatch(
            identity,
            baseReq.copy(latitude = 30.5, longitude = 114.3, address = "教学楼A"),
        )
        assertTrue(locRes is SignExecutionResult.Success)
        assertTrue(recordedRequests.any { it.contains("ifTiJiao=1") })

        // 手势/签到码签到
        currentActiveInfoJson = activeInfoJson(otherId = 3, ifPhoto = 0, needVcode = 0)
        val gestureRes = signDispatcher.dispatch(identity, baseReq.copy(signCode = "1235789"))
        assertTrue(gestureRes is SignExecutionResult.Success)
        assertTrue(recordedRequests.any { it.contains("signCode=1235789") })

        // 行为验证码挂起与回填恢复
        currentActiveInfoJson = activeInfoJson(otherId = 0, ifPhoto = 0, needVcode = 1)
        val captchaSuspended = signDispatcher.dispatch(identity, baseReq)
        assertTrue(captchaSuspended is SignExecutionResult.RequiresCaptcha)
        val resumed = signDispatcher.dispatch(identity, baseReq.copy(captchaValidate = "valid_ticket_01"))
        assertTrue(resumed is SignExecutionResult.Success)
    }

    @Test
    fun `requires and submits location coordinates for signCode and signOut when ifopenAddress enabled`() = runTest {
        val identity = SignAccountIdentity(
            accountId = "cx_9001",
            uid = "9001",
            fid = "100",
            maskedAccount = "138****0001",
            displayName = "同学甲",
        )
        val baseReq = SignExecutionRequest(courseId = 2001L, clazzId = 1001L, activeId = 7001L)

        // 签到码（otherId = 5）叠加位置范围校验（ifopenAddress = 1）：必须同时要求并携带签到码与经纬度
        currentActiveInfoJson = activeInfoJson(otherId = 5, ifPhoto = 0, needVcode = 0, ifOpenAddress = 1)
        val missingCodeFirst = signDispatcher.dispatch(identity, baseReq)
        assertEquals(
            SignExecutionResult.RequiresExtraInput(SignMissingField.SIGN_CODE, "请输入教师公布的签到码"),
            missingCodeFirst,
        )
        val missingLocationSecond = signDispatcher.dispatch(identity, baseReq.copy(signCode = "6789"))
        assertEquals(
            SignExecutionResult.RequiresExtraInput(
                SignMissingField.LOCATION_COORDINATE,
                "该签到开启了位置范围校验，请先选择签到地点",
            ),
            missingLocationSecond,
        )
        recordedRequests.clear()
        val codeWithLocOk = signDispatcher.dispatch(
            identity,
            baseReq.copy(signCode = "6789", latitude = 36.007, longitude = 120.128, address = "J7教学楼"),
        )
        assertTrue(codeWithLocOk is SignExecutionResult.Success)
        assertTrue(recordedRequests.any { it.contains("signCode=6789") && it.contains("ifTiJiao=1") })

        // 签退（activeType = 74）叠加位置范围校验（ifopenAddress = 1）：必须校验并提交经纬度
        currentActiveInfoJson = activeInfoJson(
            activeType = 74,
            otherId = 0,
            ifPhoto = 0,
            needVcode = 0,
            ifOpenAddress = 1,
        )
        val signOutMissingLoc = signDispatcher.dispatch(identity, baseReq)
        assertEquals(
            SignExecutionResult.RequiresExtraInput(
                SignMissingField.LOCATION_COORDINATE,
                "该活动开启了位置范围校验，请先选择签到地点",
            ),
            signOutMissingLoc,
        )
        recordedRequests.clear()
        val signOutWithLocOk = signDispatcher.dispatch(
            identity,
            baseReq.copy(latitude = 36.007, longitude = 120.128, address = "J7教学楼"),
        )
        assertTrue(signOutWithLocOk is SignExecutionResult.Success)
        assertTrue(recordedRequests.any { it.contains("latitude=36.007") && it.contains("ifTiJiao=1") })
    }

    @Test
    fun `batch coordinator records outcomes and enforces downgraded subaccount quota`() = runTest {
        licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 1)
        database.accountDao().upsert(sampleDbAccount("cx_1", isPrimary = true))
        database.accountDao().upsert(sampleDbAccount("cx_2", isPrimary = false))
        database.accountDao().upsert(sampleDbAccount("cx_3", isPrimary = false))

        currentActiveInfoJson = activeInfoJson(otherId = 0, ifPhoto = 0, needVcode = 0)
        val summary = batchCoordinator.executeForAccounts(
            targetAccountIds = listOf("cx_1", "cx_2", "cx_3"),
            courseName = "软件工程",
            activityTitle = "随堂点名",
            request = SignExecutionRequest(courseId = 2001L, clazzId = 1001L, activeId = 7001L),
        )

        assertEquals(3, summary.reports.size)
        assertTrue(summary.reports[0].result is SignExecutionResult.Success)
        assertTrue(summary.reports[1].result is SignExecutionResult.Success)
        assertTrue(summary.reports[2].result is SignExecutionResult.QuotaRestricted)

        val savedRecords = database.signRecordDao().observeRecent().first()
        assertEquals(3, savedRecords.size)
    }

    @Test
    fun `share link imports unified device code and submits distinct deviceCode per account`() = runTest {
        licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 2)

        // 1. 主账号密码登录：默认绑定本机统一设备码，并支持导出分享包
        val primaryBound = accountManager.bindWithPassword("13800000001", "pwd_primary") as AccountBindResult.Bound
        assertTrue(primaryBound.account.isUnifiedDeviceCode)
        val shareBundleRes = accountManager.createShareBundle(primaryBound.account.accountId)
        assertTrue(shareBundleRes is AppResult.Success)
        val primaryShareUrl = (shareBundleRes as AppResult.Success).data.shareUrl
        assertTrue(primaryShareUrl.contains("dc="))

        // 2. 子账号先使用普通密码登录：分配独立固定随机设备码（isUnifiedDeviceCode = false）
        val subPlainBound = accountManager.bindWithPassword("13800000002", "pwd_sub") as AccountBindResult.Bound
        assertFalse(subPlainBound.account.isUnifiedDeviceCode)
        assertTrue(subPlainBound.account.deviceCode.isNotBlank())
        assertNotEquals(primaryBound.account.deviceCode, subPlainBound.account.deviceCode)

        // 3. 子账号随后通过绑定分享链接重新导入：原地升级为统一设备码，无需先解绑
        val subShareUrl = ChaoxingSharedAccountCodec.encodeShareUrl(
            ChaoxingSharedAccountPayload(
                accountInput = "13800000002",
                encryptedPassword = ChaoxingLoginCipher.encrypt("pwd_sub"),
                displayName = "代签同学",
                deviceCode = "OWNER_UNIFIED_DC_888",
                uniqueId = "owner_uid_888",
                deviceModel = "24031PN0DC",
            ),
        )
        val imported = accountManager.importFromSharedText(subShareUrl) as AccountBindResult.Bound
        assertEquals(subPlainBound.account.accountId, imported.account.accountId)
        assertTrue(imported.updatedDeviceBindingOnly)
        assertTrue(imported.account.isUnifiedDeviceCode)
        assertEquals("OWNER_UNIFIED_DC_888", imported.account.deviceCode)

        // 4. 协同签到时每个账号均在 stuSignajax 请求中提交各自绑定的 deviceCode
        recordedRequests.clear()
        currentActiveInfoJson = activeInfoJson(otherId = 0, ifPhoto = 0, needVcode = 0)
        val summary = batchCoordinator.executeForAccounts(
            targetAccountIds = listOf(primaryBound.account.accountId, imported.account.accountId),
            courseName = "计算机网络",
            activityTitle = "课堂签到",
            request = SignExecutionRequest(courseId = 2001L, clazzId = 1001L, activeId = 7001L),
        )
        assertTrue(summary.reports.all { it.result is SignExecutionResult.Success })
        assertTrue(recordedRequests.any { it.contains("deviceCode=${primaryBound.account.deviceCode}") })
        assertTrue(recordedRequests.any { it.contains("deviceCode=OWNER_UNIFIED_DC_888") })
    }

    private fun configureMockDispatcher() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recordedRequests += request.target
                val path = request.target
                return when {
                    path.startsWith("/mlogin") -> MockResponse.Builder()
                        .code(HTTP_OK)
                        .addHeader("Set-Cookie", "JSESSIONID=sid_mock; Path=/")
                        .body("<html/>")
                        .build()

                    path.startsWith("/fanyalogin") -> handleMockLogin(request)
                    path.startsWith("/apis/login/userLogin4Uname.do") -> handleMockProfile(request)
                    path.startsWith("/v2/apis/active/getPPTActiveInfo") ->
                        MockResponse.Builder().code(HTTP_OK).body(currentActiveInfoJson).build()

                    path.startsWith("/newsign/preSign") ->
                        MockResponse.Builder().code(HTTP_OK).body("<div class='zhuangtai'>进行中</div>").build()

                    path.startsWith("/widget/sign/pcStuSignController/checkSignCode") ->
                        MockResponse.Builder().code(HTTP_OK).body("{\"result\":1}").build()

                    path.startsWith("/api/token/uservalid") ->
                        MockResponse.Builder().code(HTTP_OK).body("{\"result\":true,\"_token\":\"tok_123\"}").build()

                    path.startsWith("/upload") ->
                        MockResponse.Builder()
                            .code(HTTP_OK)
                            .body("{\"result\":true,\"objectId\":\"obj_photo_99\"}")
                            .build()

                    path.startsWith("/pptSign/stuSignajax") ->
                        MockResponse.Builder().code(HTTP_OK).body("success").build()

                    else -> MockResponse.Builder().code(HTTP_OK).body("{\"result\":1}").build()
                }
            }
        }
    }

    private fun handleMockLogin(request: RecordedRequest): MockResponse {
        val body = request.body?.utf8().orEmpty()
        val uidSuffix = body.hashCode().toUInt().toString().take(4)
        return MockResponse.Builder()
            .code(HTTP_OK)
            .addHeader("Set-Cookie", "UID=90$uidSuffix; Path=/")
            .addHeader("Set-Cookie", "_d=1700000000; Path=/")
            .addHeader("Set-Cookie", "vc3=vc3_$uidSuffix; Path=/; HttpOnly")
            .addHeader("Set-Cookie", "uf=uf_$uidSuffix; Path=/; HttpOnly")
            .body("{\"status\":true,\"url\":\"http://i.mooc.chaoxing.com\"}")
            .build()
    }

    private fun handleMockProfile(request: RecordedRequest): MockResponse {
        val cookie = request.headers["Cookie"].orEmpty()
        val uid = Regex("""UID=(\d+)""").find(cookie)?.groupValues?.get(1)?.toLongOrNull() ?: 90001L
        val payload = """
            {
              "result": 1,
              "msg": {
                "uid": $uid,
                "puid": $uid,
                "fid": 100,
                "name": "示例用户$uid",
                "phone": "13800138000",
                "schoolname": "示例高校"
              }
            }
        """.trimIndent()
        return MockResponse.Builder().code(HTTP_OK).body(payload).build()
    }

    private fun activeInfoJson(
        activeType: Int = 2,
        otherId: Int,
        ifPhoto: Int,
        needVcode: Int,
        ifOpenAddress: Int = if (otherId == 4) 1 else 0,
    ): String = """
        {
          "result": 1,
          "msg": "success",
          "data": {
            "id": 7001,
            "clazzid": 1001,
            "name": "课堂签到",
            "activeType": $activeType,
            "otherId": $otherId,
            "status": 1,
            "ifphoto": $ifPhoto,
            "ifNeedVCode": $needVcode,
            "ifopenAddress": $ifOpenAddress,
            "starttime": 1700000000000
          }
        }
    """.trimIndent()

    private fun sampleDbAccount(accountId: String, isPrimary: Boolean): ChaoxingAccountEntity = ChaoxingAccountEntity(
        accountId = accountId,
        uid = accountId.removePrefix("cx_"),
        fid = "100",
        maskedAccount = "138****000$accountId",
        displayName = "用户$accountId",
        schoolName = "示例大学",
        avatarUrl = "",
        encryptedPasswordCiphertext = null,
        encryptedCookieVaultRef = accountId,
        isPrimary = isPrimary,
        isEnabled = true,
        sessionStatus = ChaoxingAccountManager.SESSION_ACTIVE,
        lastVerifiedAtMs = FIXED_NOW_MS,
        createdAtMs = FIXED_NOW_MS,
    )

    private class FakeLicenseEngine : LicenseEngine {
        private val mutableState = MutableStateFlow<LicenseState>(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-INIT"),
        )
        override val state: StateFlow<LicenseState> = mutableState

        fun setState(newState: LicenseState) {
            mutableState.value = newState
        }

        fun setUnlocked(tier: LicenseTier, maxSubAccounts: Int) {
            mutableState.value = LicenseState.Unlocked(
                LicenseEntitlement(
                    serialId = "SN-TEST",
                    tier = tier,
                    grantedScopes = setOf("*"),
                    maxSubAccounts = maxSubAccounts,
                    expiresAtEpochSec = null,
                    isOfficialEngine = false,
                ),
            )
        }

        override suspend fun getDeviceChallenge(): String = "DEV-INIT"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException())

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() = Unit
    }

    companion object {
        private const val HTTP_OK = 200
        private const val FIXED_NOW_MS = 1_700_000_000_000L
        private const val RATE_LIMIT_CAPACITY = 20
        private const val RATE_LIMIT_PERIOD_MS = 1000L
    }
}
