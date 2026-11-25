package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.AccessDecision
import io.github.onprs.universitytoolbox.core.license.api.AccessDenialReason
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.core.update.UpdateManifest
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCredentialCipher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingDatabase
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAuthRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingHtmlParser
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingHttpExecutor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingUserSignStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountSessionInterceptor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.InMemoryAccountCookieStorage
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.MultiAccountSignCoordinator
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.ChaoxingLocationService
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.GeoPoint
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.ResolvedPlace
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit.ChaoxingRateLimitCoordinator
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit.JitterStrategy
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit.TokenBucketRateLimiter
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.NormalSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.QrCodeSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignStrategyDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChaoxingViewModelsTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }
    private lateinit var server: MockWebServer
    private lateinit var database: ChaoxingDatabase
    private lateinit var licenseEngine: TestLicenseEngine
    private lateinit var governanceRepo: TestGovernanceRepository
    private lateinit var accountManager: ChaoxingAccountManager
    private lateinit var authRemote: ChaoxingAuthRemoteDataSource
    private lateinit var remote: ChaoxingRemoteDataSource
    private lateinit var signCoordinator: MultiAccountSignCoordinator
    private lateinit var licenseCoordinator: LicenseCoordinator

    private var activeInfoNeedsCaptcha = false

    private val dummyScanner = object : BarcodeScanner {
        override suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> = AppResult.Success(emptyList())

        override suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> =
            AppResult.Success(emptyList())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        configureServer()

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChaoxingDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor { command -> command.run() }
            .setTransactionExecutor { command -> command.run() }
            .build()

        licenseEngine = TestLicenseEngine()
        governanceRepo = TestGovernanceRepository()
        licenseCoordinator = LicenseCoordinator(licenseEngine, TimeProvider { FIXED_NOW_MS })
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
        authRemote = ChaoxingAuthRemoteDataSource(
            executor = executor,
            cookieVault = cookieVault,
            htmlParser = htmlParser,
            endpointConfig = endpoints,
            dispatchers = testDispatcherProvider,
        )
        remote = ChaoxingRemoteDataSource(
            executor = executor,
            cookieVault = cookieVault,
            htmlParser = htmlParser,
            endpointConfig = endpoints,
            dispatchers = testDispatcherProvider,
        )
        initCoordinators(cookieVault)
    }

    private fun initCoordinators(cookieVault: AccountCookieVault) {
        val dummyCipher = object : ChaoxingCredentialCipher {
            override suspend fun encrypt(plainText: String, associatedData: String): String = plainText

            override suspend fun decrypt(cipherTextBase64: String, associatedData: String): String = cipherTextBase64
        }
        accountManager = ChaoxingAccountManager(
            accountDao = database.accountDao(),
            courseDao = database.courseDao(),
            authRemoteDataSource = authRemote,
            remoteDataSource = remote,
            cookieVault = cookieVault,
            credentialCipher = dummyCipher,
            licenseCoordinator = licenseCoordinator,
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )
        val dispatcher = SignStrategyDispatcher(
            remoteDataSource = remote,
            handlers = setOf(NormalSignActionHandler(remote), QrCodeSignActionHandler(remote)),
        )
        val limiter = ChaoxingRateLimitCoordinator(
            globalLimiter = TokenBucketRateLimiter(CAPACITY, CAPACITY, PERIOD_MS, TimeProvider { FIXED_NOW_MS }),
            jitterStrategy = JitterStrategy { _, _ -> 0L },
            delayFn = {},
        )
        signCoordinator = MultiAccountSignCoordinator(
            accountDao = database.accountDao(),
            courseDao = database.courseDao(),
            signRecordDao = database.signRecordDao(),
            remoteDataSource = remote,
            strategyDispatcher = dispatcher,
            rateLimitCoordinator = limiter,
            licenseCoordinator = licenseCoordinator,
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `dashboard viewModel reacts to license tier and remote kill switch`() = runTest {
        val viewModel = ChaoxingDashboardViewModel(accountManager, licenseCoordinator, governanceRepo)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.accessDecision is AccessDecision.Denied)

            licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 1)
            val unlocked = awaitItem()
            assertTrue(unlocked.accessDecision is AccessDecision.Granted)

            governanceRepo.applyManifest(
                UpdateManifest(
                    latestVersionCode = 1,
                    latestVersionName = "1.0.0",
                    apkUrl = "https://localhost/app.apk",
                    sha256 = "0000",
                    disabledTools = mapOf(CHAOXING_TOOL_ID to "临时维护"),
                ),
            )
            val disabled = awaitItem()
            assertTrue(disabled.isRemoteDisabled)
            assertEquals(
                AccessDecision.Denied(AccessDenialReason.RemotelyDisabled),
                disabled.accessDecision,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `accounts viewModel tracks quota limits and filters bound accounts`() = runTest {
        licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 1)
        database.accountDao().upsert(sampleAccount("cx_1", isPrimary = true))
        val viewModel = ChaoxingAccountsViewModel(accountManager, authRemote, licenseCoordinator, dummyScanner)

        val primaryLoaded = viewModel.uiState.first { it.accounts.size == 1 }
        assertTrue(primaryLoaded.canBindMore)
        assertEquals(0, primaryLoaded.currentSubAccounts)

        database.accountDao().upsert(sampleAccount("cx_2", isPrimary = false))
        val subLoaded = viewModel.uiState.first { it.accounts.size == 2 }
        assertFalse(subLoaded.canBindMore)
        assertEquals(1, subLoaded.currentSubAccounts)
    }

    @Test
    fun `sign execution viewModel parses qr payload and handles captcha suspended flow`() = runTest {
        licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 2)
        database.accountDao().upsert(sampleAccount("cx_1", isPrimary = true))
        activeInfoNeedsCaptcha = true

        val viewModel = ChaoxingSignExecutionViewModel(
            route = ChaoxingNavKey.SignExecution("cx_1", 2001L, 1001L, 7001L),
            accountDao = database.accountDao(),
            presetLocationDao = database.presetLocationDao(),
            remoteDataSource = remote,
            signCoordinator = signCoordinator,
            licenseCoordinator = licenseCoordinator,
            barcodeScanner = dummyScanner,
            locationService = FakeLocationService(),
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )

        val detailLoaded = viewModel.uiState.first { it.detail != null }
        assertNotNull(detailLoaded.detail)
        viewModel.onBarcodeScanned("SIGNIN:aid=7001&source=15&Code=K1&enc=ENC_FROM_SCANNER")
        assertEquals("ENC_FROM_SCANNER", viewModel.uiState.value.parsedQrEnc)

        viewModel.executeSign(courseName = "数据结构")
        val captchaState = viewModel.uiState.first { !it.isExecuting && it.pendingCaptchaId != null }
        assertNotNull(captchaState.pendingCaptchaId)

        viewModel.updateCaptchaValidate("captcha_validate_ticket")
        viewModel.executeSign(courseName = "数据结构")
        val completedState = viewModel.uiState.first { !it.isExecuting && it.batchSummary != null }
        val firstReport = completedState.batchSummary?.reports?.firstOrNull()
        assertTrue(firstReport?.result is SignExecutionResult.Success)
    }

    @Test
    fun `first account login emits navigate event and dashboard auto syncs empty courses`() = runTest {
        licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 2)
        val accountsVm = ChaoxingAccountsViewModel(accountManager, authRemote, licenseCoordinator, dummyScanner)

        accountsVm.events.test {
            accountsVm.updateAccountInput("13800138001")
            accountsVm.updatePasswordInput("pass123")
            accountsVm.submitPasswordLogin()
            val firstEvent = awaitItem()
            assertEquals(ChaoxingAccountsUiEvent.FirstAccountBound("cx_9001"), firstEvent)

            // 绑定第二个账号时不应再次发出首个账号导航事件
            accountsVm.updateAccountInput("13800138002")
            accountsVm.updatePasswordInput("pass456")
            accountsVm.submitPasswordLogin()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        val dashboardVm = ChaoxingDashboardViewModel(accountManager, licenseCoordinator, governanceRepo)
        val syncedState = dashboardVm.uiState.first { it.courses.isNotEmpty() }
        assertEquals("自动同步示例课程", syncedState.courses.first().name)
    }

    @Test
    fun `location sign supports preset saving selection map picking and current device location`() = runTest {
        licenseEngine.setUnlocked(LicenseTier.STANDARD, maxSubAccounts = 1)
        database.accountDao().upsert(sampleAccount("cx_1", isPrimary = true))

        val viewModel = ChaoxingSignExecutionViewModel(
            route = ChaoxingNavKey.SignExecution("cx_1", 2001L, 1001L, 7002L),
            accountDao = database.accountDao(),
            presetLocationDao = database.presetLocationDao(),
            remoteDataSource = remote,
            signCoordinator = signCoordinator,
            licenseCoordinator = licenseCoordinator,
            barcodeScanner = dummyScanner,
            locationService = FakeLocationService(),
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )

        viewModel.uiState.first { it.detail != null }
        // 活动未公开坐标时调用 applyTargetCourseLocation 应引导打开预设坐标面板
        viewModel.applyTargetCourseLocation()
        assertTrue(viewModel.uiState.value.isPresetSheetOpen)

        // 保存预设并填入
        viewModel.savePresetLocation("三教201", 40.008500, 116.332100, "海淀区第三教学楼")
        val afterPresetSaved = viewModel.uiState.first { it.presetLocations.isNotEmpty() }
        assertEquals("40.008500", afterPresetSaved.latitudeInput)
        assertEquals("116.332100", afterPresetSaved.longitudeInput)

        // 从地图选点确认回填
        viewModel.confirmMapSelection(GeoPoint(40.012345, 116.345678), "东区操场")
        assertEquals("40.012345", viewModel.uiState.value.latitudeInput)
        assertEquals("东区操场", viewModel.uiState.value.addressInput)

        // 重新应用预设地点
        viewModel.applyPresetLocation(afterPresetSaved.presetLocations.first())
        assertEquals("40.008500", viewModel.uiState.value.latitudeInput)
        assertEquals("海淀区第三教学楼", viewModel.uiState.value.addressInput)

        // 设备当前定位回填及地图重复点击回正单调序号递增验证
        viewModel.useCurrentDeviceLocation(jumpInMap = true)
        assertEquals("36.007064", viewModel.uiState.value.latitudeInput)
        assertEquals("120.128667", viewModel.uiState.value.longitudeInput)
        val firstSeq = viewModel.uiState.value.mapJumpCommand?.sequence
        assertEquals(1L, firstSeq)

        viewModel.useCurrentDeviceLocation(jumpInMap = true)
        assertEquals(2L, viewModel.uiState.value.mapJumpCommand?.sequence)
    }

    @Test
    fun `activities viewModel enriches real attendance status from getAttendInfo instead of fake userStatus`() =
        runTest {
            database.accountDao().upsert(sampleAccount("cx_1", isPrimary = true))
            val activitiesVm = ChaoxingActivitiesViewModel(
                route = ChaoxingNavKey.CourseActivities(
                    accountId = "cx_1",
                    courseId = 2001L,
                    clazzId = 1001L,
                    cpi = 3001L,
                    courseName = "面向对象程序设计",
                ),
                accountDao = database.accountDao(),
                remoteDataSource = remote,
                timeProvider = TimeProvider { FIXED_NOW_MS },
            )
            val loaded = activitiesVm.uiState.first { !it.isLoading && it.activities.isNotEmpty() }
            assertEquals(3, loaded.activities.size)
            assertEquals(ChaoxingUserSignStatus.UNSIGNED, loaded.activities[0].userStatus)
            assertEquals(ChaoxingUserSignStatus.ABSENT, loaded.activities[1].userStatus)
            assertEquals(ChaoxingUserSignStatus.SIGNED, loaded.activities[2].userStatus)
        }

    private fun configureServer() {
        var nextUid = 9001L
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.target.startsWith("/fanyalogin") -> mockFanyaLoginResponse(nextUid)
                request.target.startsWith("/apis/login/userLogin4Uname.do") -> mockUserLoginResponse(nextUid++)
                request.target.startsWith("/mycourse/backclazzdata") -> mockCourseListResponse()
                request.target.startsWith("/v2/apis/active/getPPTActiveInfo") -> mockActiveInfoResponse(request)
                request.target.startsWith("/v2/apis/active/student/activelist") -> mockActiveListResponse()
                request.target.startsWith("/v2/apis/sign/getAttendInfo") -> mockAttendInfoResponse(request)
                request.target.startsWith("/newsign/preSign") ->
                    MockResponse.Builder().code(HTTP_OK).body("<div class='zhuangtai'>进行中</div>").build()
                request.target.startsWith("/pptSign/stuSignajax") ->
                    MockResponse.Builder().code(HTTP_OK).body("success").build()
                else -> MockResponse.Builder().code(HTTP_OK).body("{\"result\":1}").build()
            }
        }
    }

    private fun mockFanyaLoginResponse(uid: Long): MockResponse = MockResponse.Builder()
        .code(HTTP_OK)
        .addHeader("Set-Cookie", "UID=$uid; Domain=.localhost; Path=/")
        .addHeader("Set-Cookie", "_d=1700; Domain=.localhost; Path=/")
        .addHeader("Set-Cookie", "vc3=vc_tok; Domain=.localhost; Path=/")
        .addHeader("Set-Cookie", "uf=uf_tok; Domain=.localhost; Path=/")
        .body("{\"status\":true}")
        .build()

    private fun mockUserLoginResponse(uid: Long): MockResponse = MockResponse.Builder()
        .code(HTTP_OK)
        .body(
            """
            {"result":1,"msg":{"uid":$uid,"fid":100,"name":"测试用户$uid","phone":"13800138000","schoolname":"示例大学"}}
            """.trimIndent(),
        )
        .build()

    private fun mockCourseListResponse(): MockResponse = MockResponse.Builder()
        .code(HTTP_OK)
        .body(
            """
            {
              "result": 1,
              "channelList": [
                {
                  "cataName": "课程",
                  "key": 1001,
                  "cpi": 3001,
                  "content": {
                    "id": 1001,
                    "cpi": 3001,
                    "name": "1班",
                    "course": {
                      "data": [{"id": 2001, "name": "自动同步示例课程", "teacherfactor": "张老师"}]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        .build()

    private fun mockActiveInfoResponse(request: RecordedRequest): MockResponse {
        val isLoc = request.target.contains("7002")
        return MockResponse.Builder()
            .code(HTTP_OK)
            .body(
                """
                {
                  "result": 1,
                  "data": {
                    "id": ${if (isLoc) 7002 else 7001},
                    "clazzid": 1001,
                    "name": "${if (isLoc) "位置签到" else "课堂二维码签到"}",
                    "activeType": 2,
                    "otherId": ${if (isLoc) 4 else 2},
                    "status": 1,
                    "ifopenAddress": ${if (isLoc) 1 else 0},
                    "locationText": "第三教学楼",
                    "ifNeedVCode": ${if (!isLoc && activeInfoNeedsCaptcha) 1 else 0},
                    "starttime": 1700000000000
                  }
                }
                """.trimIndent(),
            )
            .build()
    }

    private fun mockActiveListResponse(): MockResponse = MockResponse.Builder()
        .code(HTTP_OK)
        .body(
            """
            {
              "result": 1,
              "data": {
                "activeList": [
                  {"id": 9101, "activeType": 2, "otherId": "4", "status": 2, "userStatus": 1, "nameOne": "未签活动"},
                  {"id": 9102, "activeType": 2, "otherId": "0", "status": 2, "userStatus": 1, "nameOne": "缺勤活动"},
                  {"id": 9103, "activeType": 2, "otherId": "0", "status": 2, "userStatus": 1, "nameOne": "已签活动"}
                ]
              }
            }
            """.trimIndent(),
        )
        .build()

    private fun mockAttendInfoResponse(request: RecordedRequest): MockResponse {
        val status = when {
            request.target.contains("9101") -> 0
            request.target.contains("9102") -> 5
            else -> 1
        }
        val recId = if (status == 0) "null" else "88001"
        return MockResponse.Builder()
            .code(HTTP_OK)
            .body("""{"result":1,"msg":"success","data":{"id":$recId,"status":$status}}""")
            .build()
    }

    private fun sampleAccount(accountId: String, isPrimary: Boolean): ChaoxingAccountEntity = ChaoxingAccountEntity(
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

    private class TestLicenseEngine : LicenseEngine {
        private val mutableState = MutableStateFlow<LicenseState>(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-INIT"),
        )
        override val state: StateFlow<LicenseState> = mutableState

        fun setUnlocked(tier: LicenseTier, maxSubAccounts: Int) {
            mutableState.value = LicenseState.Unlocked(
                LicenseEntitlement("SN-VM", tier, setOf("*"), maxSubAccounts, null, false),
            )
        }

        override suspend fun getDeviceChallenge(): String = "DEV-INIT"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException())

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() = Unit
    }

    private class TestGovernanceRepository : RemoteGovernanceRepository {
        private val mutableDisabled = MutableStateFlow<Map<String, String?>>(emptyMap())
        private val mutableManifest = MutableStateFlow<UpdateManifest?>(null)

        override val disabledTools: StateFlow<Map<String, String?>> = mutableDisabled
        override val latestManifest: StateFlow<UpdateManifest?> = mutableManifest

        override fun isToolDisabled(toolId: String): Boolean = toolId in mutableDisabled.value

        override fun disableReason(toolId: String): String? = mutableDisabled.value[toolId]

        override suspend fun fetchAndApplyManifest(manifestUrl: String): AppResult<UpdateManifest> = error("Not used")

        override suspend fun applyManifest(manifest: UpdateManifest) {
            mutableDisabled.value = manifest.disabledTools
            mutableManifest.value = manifest
        }
    }

    private class FakeLocationService : ChaoxingLocationService {
        override suspend fun getCurrentLocation(): AppResult<ResolvedPlace> = AppResult.Success(
            ResolvedPlace(
                title = "校内定位点",
                address = "山东省青岛市黄岛区山东科技大学",
                gcj02Point = GeoPoint(36.000812, 120.122034),
                bd09Point = GeoPoint(36.007064, 120.128667),
            ),
        )

        override suspend fun searchPlaces(query: String, proximityGcj: GeoPoint?): AppResult<List<ResolvedPlace>> =
            AppResult.Success(emptyList())

        override suspend fun reverseGeocodeGcj02(gcjLatitude: Double, gcjLongitude: Double): String = "示例教学楼"
    }

    companion object {
        private const val HTTP_OK = 200
        private const val FIXED_NOW_MS = 1_700_000_000_000L
        private const val CAPACITY = 20
        private const val PERIOD_MS = 1000L
    }
}
