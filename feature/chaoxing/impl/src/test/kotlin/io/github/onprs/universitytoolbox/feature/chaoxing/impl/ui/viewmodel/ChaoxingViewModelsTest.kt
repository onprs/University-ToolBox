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
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountSessionInterceptor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.InMemoryAccountCookieStorage
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.MultiAccountSignCoordinator
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
        val viewModel = ChaoxingAccountsViewModel(accountManager, authRemote, licenseCoordinator)

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

        val dummyScanner = object : BarcodeScanner {
            override suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> =
                AppResult.Success(emptyList())

            override suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> =
                AppResult.Success(emptyList())
        }

        val viewModel = ChaoxingSignExecutionViewModel(
            route = ChaoxingNavKey.SignExecution("cx_1", 2001L, 1001L, 7001L),
            accountDao = database.accountDao(),
            remoteDataSource = remote,
            signCoordinator = signCoordinator,
            licenseCoordinator = licenseCoordinator,
            barcodeScanner = dummyScanner,
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

    private fun configureServer() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.target.startsWith("/v2/apis/active/getPPTActiveInfo") -> MockResponse.Builder()
                    .code(HTTP_OK)
                    .body(
                        """
                        {
                          "result": 1,
                          "data": {
                            "id": 7001,
                            "clazzid": 1001,
                            "name": "课堂二维码签到",
                            "activeType": 2,
                            "otherId": 2,
                            "status": 1,
                            "ifNeedVCode": ${if (activeInfoNeedsCaptcha) 1 else 0},
                            "starttime": 1700000000000
                          }
                        }
                        """.trimIndent(),
                    )
                    .build()

                request.target.startsWith("/newsign/preSign") ->
                    MockResponse.Builder().code(HTTP_OK).body("<div class='zhuangtai'>进行中</div>").build()

                request.target.startsWith("/pptSign/stuSignajax") ->
                    MockResponse.Builder().code(HTTP_OK).body("success").build()

                else -> MockResponse.Builder().code(HTTP_OK).body("{\"result\":1}").build()
            }
        }
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

    companion object {
        private const val HTTP_OK = 200
        private const val FIXED_NOW_MS = 1_700_000_000_000L
        private const val CAPACITY = 20
        private const val PERIOD_MS = 1000L
    }
}
