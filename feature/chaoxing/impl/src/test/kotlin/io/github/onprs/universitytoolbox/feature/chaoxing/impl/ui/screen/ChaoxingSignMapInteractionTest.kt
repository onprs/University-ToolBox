package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.designsystem.theme.UniversityToolboxTheme
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingDatabase
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingHtmlParser
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingHttpExecutor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
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
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.LocationSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignMissingField
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignStrategyDispatcher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingSignExecutionViewModel
import java.io.File
import java.io.FileOutputStream
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBitmapFactory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChaoxingSignMapInteractionTest {
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var server: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var database: ChaoxingDatabase
    private lateinit var remote: ChaoxingRemoteDataSource
    private lateinit var signCoordinator: MultiAccountSignCoordinator
    private lateinit var licenseCoordinator: LicenseCoordinator

    private val dummyScanner = object : BarcodeScanner {
        override suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> = AppResult.Success(emptyList())

        override suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> =
            AppResult.Success(emptyList())
    }

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val mainImmediate: CoroutineDispatcher = testDispatcher
        }
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.target.startsWith("/v2/apis/active/getPPTActiveInfo") -> MockResponse.Builder()
                    .code(HTTP_OK)
                    .body(LOCATION_ACTIVE_DETAIL_JSON)
                    .build()

                request.target.startsWith("/newsign/preSign") -> MockResponse.Builder()
                    .code(HTTP_OK)
                    .body("<div class='zhuangtai'>进行中</div>")
                    .build()

                request.target.startsWith("/pptSign/stuSignajax") -> MockResponse.Builder()
                    .code(HTTP_OK)
                    .body("success")
                    .build()

                else -> MockResponse.Builder().code(HTTP_OK).body("{\"result\":1}").build()
            }
        }

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChaoxingDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        val licenseEngine = UnlockedTestLicenseEngine()
        licenseCoordinator = LicenseCoordinator(licenseEngine, TimeProvider { FIXED_NOW_MS })
        val cookieVault = AccountCookieVault(InMemoryAccountCookieStorage())
        okHttpClient = OkHttpClient.Builder()
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
        remote = ChaoxingRemoteDataSource(
            executor = ChaoxingHttpExecutor(okHttpClient),
            cookieVault = cookieVault,
            htmlParser = ChaoxingHtmlParser(),
            endpointConfig = endpoints,
            dispatchers = dispatchers,
        )
        val strategyDispatcher = SignStrategyDispatcher(
            remoteDataSource = remote,
            handlers = setOf(LocationSignActionHandler(remote)),
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
            strategyDispatcher = strategyDispatcher,
            rateLimitCoordinator = limiter,
            licenseCoordinator = licenseCoordinator,
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )
    }

    @After
    fun tearDown() {
        okHttpClient.dispatcher.cancelAll()
        okHttpClient.dispatcher.executorService.shutdown()
        Dispatchers.resetMain()
        server.close()
        database.close()
    }

    @Test
    fun `pinch zoom lift transition suppresses single finger jitter and never triggers single tap`() {
        val tracker = MapGestureSessionTracker(touchSlopPx = 16f)

        // 1. 双指同时按下
        val downOutcome = tracker.onFrame(
            listOf(
                MapPointerSample(1L, Offset(100f, 100f), Offset(100f, 100f), pressed = true),
                MapPointerSample(2L, Offset(300f, 300f), Offset(300f, 300f), pressed = true),
            ),
        )
        assertEquals(MapGestureFrameOutcome.Idle, downOutcome)
        assertTrue(tracker.hadMultiTouch)

        // 2. 双指外扩缩放（超过 touchSlop）
        val zoomOutcome = tracker.onFrame(
            listOf(
                MapPointerSample(1L, Offset(100f, 100f), Offset(60f, 60f), pressed = true),
                MapPointerSample(2L, Offset(300f, 300f), Offset(340f, 340f), pressed = true),
            ),
        )
        assertTrue(zoomOutcome is MapGestureFrameOutcome.Transform)
        assertTrue((zoomOutcome as MapGestureFrameOutcome.Transform).zoomFactor > 1.2f)

        // 3. 松开第 2 指的过渡帧：质心从 (200,200) 跳到 (60,60)，必须跳过差分防止地图瞬移
        val liftSecondOutcome = tracker.onFrame(
            listOf(
                MapPointerSample(1L, Offset(60f, 60f), Offset(60f, 60f), pressed = true),
                MapPointerSample(2L, Offset(340f, 340f), Offset(340f, 340f), pressed = false),
            ),
        )
        assertEquals(MapGestureFrameOutcome.Idle, liftSecondOutcome)

        // 4. 剩余第 1 指在抬手前发生 14px 微小抖动（<= 1.5 * 16 = 24px），必须被抑制
        val jitterOutcome = tracker.onFrame(
            listOf(MapPointerSample(1L, Offset(60f, 60f), Offset(70f, 70f), pressed = true)),
        )
        assertEquals(MapGestureFrameOutcome.Idle, jitterOutcome)

        // 5. 第 1 指完全抬起，绝不能误判为干净单击
        tracker.onFrame(listOf(MapPointerSample(1L, Offset(70f, 70f), Offset(70f, 70f), pressed = false)))
        assertFalse(tracker.canTriggerCleanSingleTap())
    }

    @Test
    fun `quick two finger tap within touch slop never triggers single tap jump and large drag unlocks`() {
        val quickPinch = MapGestureSessionTracker(touchSlopPx = 18f)
        quickPinch.onFrame(
            listOf(
                MapPointerSample(1L, Offset(200f, 200f), Offset(200f, 200f), pressed = true),
                MapPointerSample(2L, Offset(260f, 260f), Offset(262f, 262f), pressed = true),
            ),
        )
        quickPinch.onFrame(
            listOf(
                MapPointerSample(1L, Offset(200f, 200f), Offset(200f, 200f), pressed = false),
                MapPointerSample(2L, Offset(262f, 262f), Offset(262f, 262f), pressed = false),
            ),
        )
        assertTrue(quickPinch.hadMultiTouch)
        assertFalse(quickPinch.hasTransformed)
        assertFalse(quickPinch.canTriggerCleanSingleTap())

        // 双指松开一指后若故意大幅拖动超过解锁阈值（> 27px），平滑恢复单指平移
        val unlockTracker = MapGestureSessionTracker(touchSlopPx = 18f)
        unlockTracker.onFrame(
            listOf(
                MapPointerSample(1L, Offset(100f, 100f), Offset(100f, 100f), pressed = true),
                MapPointerSample(2L, Offset(300f, 300f), Offset(300f, 300f), pressed = true),
            ),
        )
        unlockTracker.onFrame(
            listOf(
                MapPointerSample(1L, Offset(100f, 100f), Offset(100f, 100f), pressed = true),
                MapPointerSample(2L, Offset(300f, 300f), Offset(300f, 300f), pressed = false),
            ),
        )
        val unlockedPan = unlockTracker.onFrame(
            listOf(MapPointerSample(1L, Offset(100f, 100f), Offset(140f, 100f), pressed = true)),
        )
        assertTrue(unlockedPan is MapGestureFrameOutcome.Transform)
        assertEquals(Offset(40f, 0f), (unlockedPan as MapGestureFrameOutcome.Transform).pan)
    }

    @Test
    fun `overlay collapse spec adapts dynamically to ime visibility and focused field`() {
        // 底部地址输入框聚焦 + 键盘弹起 -> 折叠顶部搜索框以释放地图与底部面板空间
        val editingBottom = resolveMapOverlayCollapseSpec(
            availableHeightDp = 410f,
            isImeOpen = true,
            isSearchFocused = false,
            isAddressFocused = true,
        )
        assertTrue(editingBottom.collapseTopSearchControls)
        assertFalse(editingBottom.collapseBottomConfirmControls)

        // 顶部搜索框聚焦 + 键盘弹起 -> 折叠底部确认面板以留出候选列表可视高度
        val editingTop = resolveMapOverlayCollapseSpec(
            availableHeightDp = 410f,
            isImeOpen = true,
            isSearchFocused = true,
            isAddressFocused = false,
        )
        assertFalse(editingTop.collapseTopSearchControls)
        assertTrue(editingTop.collapseBottomConfirmControls)

        // 横屏超矮空间激活 ultraCompact
        val landscapeIme = resolveMapOverlayCollapseSpec(
            availableHeightDp = 260f,
            isImeOpen = true,
            isSearchFocused = false,
            isAddressFocused = true,
        )
        assertTrue(landscapeIme.ultraCompact)
    }

    @Test
    fun `viewport holder protects manual address against late reverse geocode and survives saver`() {
        val holder = MapViewportHolder(
            initialCenterGcj = GeoPoint(36.0008, 120.1220),
            initialConfirmed = true,
            initialAddress = "第三教学楼",
        )
        holder.onManualAddressInput("第三教学楼 402 教室")
        assertTrue(holder.isAddressManuallyEdited)

        // 异步延迟到达的逆地理编码不得冲刷用户手动输入的内容
        holder.applyReverseGeocodedAddressIfEligible("前湾港路579号", isAddressFieldFocused = false)
        assertEquals("第三教学楼 402 教室", holder.addressText)

        // 保存并恢复状态（模拟屏幕旋转）
        val saved = with(MapViewportHolder.Saver) { SaverScope { true }.save(holder) }
        assertNotNull(saved)
        val restored = MapViewportHolder.Saver.restore(saved!!)!!
        assertEquals("第三教学楼 402 教室", restored.addressText)
        assertTrue(restored.isAddressManuallyEdited)

        // 用户主动拖动地图选新点后，解锁并接收新位置的逆地理编码
        restored.updateCenterAndZoomByUser(GeoPoint(36.0050, 120.1280), 17.5)
        assertFalse(restored.isAddressManuallyEdited)
        assertTrue(restored.hasUserMovedMap)
        restored.applyReverseGeocodedAddressIfEligible("东区体育场", isAddressFieldFocused = false)
        assertEquals("东区体育场", restored.addressText)
    }

    @Test
    fun `map overlay mounts in host activity window and layered back press dismisses search before map`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var candidates by mutableStateOf(
            listOf(
                ResolvedPlace(
                    title = "第一教学楼",
                    address = "示例大学第一教学楼",
                    gcj02Point = GeoPoint(36.001, 120.121),
                    bd09Point = GeoPoint(36.007, 120.127),
                ),
            ),
        )
        var dismissSearchCount = 0
        var dismissMapCount = 0

        activity.setContent {
            UniversityToolboxTheme(darkTheme = false, dynamicColor = false) {
                ChaoxingMapPickerDialog(
                    initialBd09Point = GeoPoint(36.007, 120.127),
                    targetBd09Point = null,
                    fallbackBd09Point = null,
                    targetRangeMeters = 150,
                    requiredLocationHint = "第一教学楼",
                    initialAddress = "第一教学楼",
                    searchQuery = "教学楼",
                    isSearching = false,
                    hasCompletedSearch = true,
                    searchResults = candidates,
                    isLocatingCurrent = false,
                    jumpCommand = null,
                    reverseGeocodedAddress = null,
                    statusMessage = null,
                    onSearchQueryChange = { _, _ -> },
                    onExecuteSearch = { _, _, _ -> },
                    onRequestCurrentLocation = {},
                    onReverseGeocodeCenter = { _, _ -> },
                    onConfirmSelection = { _, _ -> },
                    onSaveAsPresetAndSelect = { _, _, _ -> },
                    onDismiss = { dismissMapCount++ },
                    onDismissSearchResults = {
                        dismissSearchCount++
                        candidates = emptyList()
                    },
                )
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        // 第一次系统返回：优先收起搜索候选浮层，地图本身保持打开
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, dismissSearchCount)
        assertEquals(0, dismissMapCount)

        // 第二次系统返回：候选浮层已清空，关闭地图覆盖层回到表单页
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, dismissMapCount)
    }

    @Test
    fun `viewModel suppresses auto search popup supports dismissing results and validates coordinates`() = runTest {
        database.accountDao().upsert(sampleAccount("cx_1"))
        val multiCandidateService = MultiCandidateFakeLocationService()
        val viewModel = ChaoxingSignExecutionViewModel(
            route = ChaoxingNavKey.SignExecution("cx_1", 2001L, 1001L, 7002L),
            accountDao = database.accountDao(),
            presetLocationDao = database.presetLocationDao(),
            remoteDataSource = remote,
            signCoordinator = signCoordinator,
            licenseCoordinator = licenseCoordinator,
            barcodeScanner = dummyScanner,
            locationService = multiCandidateService,
            timeProvider = TimeProvider { FIXED_NOW_MS },
        )
        viewModel.uiState.first { it.detail != null }

        // 1. autoJumpFirst = true 时（如首次打开地图自动定位要求地点），直接跳转首个匹配且不弹出遮挡浮层
        viewModel.searchMapPlaces("教学楼", proximityGcj = null, autoJumpFirst = true)
        assertNotNull(viewModel.uiState.value.mapJumpCommand)
        assertTrue(viewModel.uiState.value.mapSearchResults.isEmpty())

        // 2. 用户主动搜索（autoJumpFirst = false）返回多项时展示候选列表，点击空白地图收起时保留搜索词
        viewModel.updateMapSearchQuery("教学楼")
        viewModel.searchMapPlaces("教学楼", proximityGcj = null, autoJumpFirst = false)
        assertEquals(2, viewModel.uiState.value.mapSearchResults.size)
        viewModel.dismissMapSearchResults()
        assertTrue(viewModel.uiState.value.mapSearchResults.isEmpty())
        assertEquals("教学楼", viewModel.uiState.value.mapSearchQuery)

        // 3. 表单坐标校验：非数字拦截与经纬度填反（纬度 > 90）范围拦截
        assertNotNull(validateCoordinateFieldError("120.128667", 90.0, "纬度"))
        assertNull(validateCoordinateFieldError("36.007064", 90.0, "纬度"))

        viewModel.updateLocation("invalid_lat", "120.12", "测试地点")
        viewModel.executeSign(courseName = "高等数学")
        assertTrue(viewModel.uiState.value.inputHintMessage?.contains("格式不正确") == true)

        viewModel.updateLocation("120.128667", "36.007064", "填反经纬度")
        viewModel.executeSign(courseName = "高等数学")
        val afterOutOfBounds = viewModel.uiState.first { !it.isExecuting && it.batchSummary != null }
        val firstRes = afterOutOfBounds.batchSummary?.reports?.firstOrNull()?.result
        assertTrue(
            firstRes is SignExecutionResult.RequiresExtraInput &&
                firstRes.missingField == SignMissingField.LOCATION_COORDINATE,
        )
    }

    @Test
    fun `photo image processor downsamples large image without oom and handles corrupted stream`() {
        assertEquals(4, SignPhotoImageProcessor.computeInSampleSize(12000, 9000, 1920))
        assertEquals(1, SignPhotoImageProcessor.computeInSampleSize(1280, 960, 1920))

        val context: Context = ApplicationProvider.getApplicationContext()
        val tempFile = File(context.cacheDir, "oversized_sign_photo.png")
        val largeBitmap = Bitmap.createBitmap(2600, 2000, Bitmap.Config.ARGB_8888)
        largeBitmap.eraseColor(Color.rgb(42, 120, 200))
        FileOutputStream(tempFile).use { out ->
            largeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        largeBitmap.recycle()

        val compressedBytes = SignPhotoImageProcessor.decodeAndCompressFromUri(context, Uri.fromFile(tempFile))
        assertNotNull(compressedBytes)
        val decodedBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(compressedBytes!!, 0, compressedBytes.size, decodedBounds)
        val longestDecodedSide = maxOf(decodedBounds.outWidth, decodedBounds.outHeight)
        assertTrue(longestDecodedSide <= SignPhotoImageProcessor.MAX_UPLOAD_DIMENSION_PX)

        val preview = SignPhotoImageProcessor.decodePreviewBitmap(compressedBytes)
        assertNotNull(preview)
        assertNull(SignPhotoImageProcessor.decodePreviewBitmap(byteArrayOf()))
        assertNull(SignPhotoImageProcessor.decodeAndCompressFromUri(context, Uri.parse("file:///missing_photo.jpg")))

        val corruptedFile = File(context.cacheDir, "corrupted_photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        try {
            assertNull(SignPhotoImageProcessor.decodePreviewBitmap(byteArrayOf(1, 2, 3)))
            assertNull(SignPhotoImageProcessor.decodeAndCompressFromUri(context, Uri.fromFile(corruptedFile)))
        } finally {
            ShadowBitmapFactory.setAllowInvalidImageData(true)
            corruptedFile.delete()
            tempFile.delete()
        }
    }

    private fun sampleAccount(accountId: String): ChaoxingAccountEntity = ChaoxingAccountEntity(
        accountId = accountId,
        uid = "9001",
        fid = "100",
        maskedAccount = "138****0001",
        displayName = "测试同学",
        schoolName = "示例大学",
        avatarUrl = "",
        encryptedPasswordCiphertext = null,
        encryptedCookieVaultRef = accountId,
        isPrimary = true,
        isEnabled = true,
        sessionStatus = ChaoxingAccountManager.SESSION_ACTIVE,
        lastVerifiedAtMs = FIXED_NOW_MS,
        createdAtMs = FIXED_NOW_MS,
    )

    private class UnlockedTestLicenseEngine : LicenseEngine {
        private val mutableState = MutableStateFlow<LicenseState>(
            LicenseState.Unlocked(
                LicenseEntitlement("SN-MAP", LicenseTier.STANDARD, setOf("*"), 2, null, false),
            ),
        )
        override val state: StateFlow<LicenseState> = mutableState

        override suspend fun getDeviceChallenge(): String = "DEV-MAP"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException())

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() = Unit
    }

    private class MultiCandidateFakeLocationService : ChaoxingLocationService {
        override suspend fun getCurrentLocation(): AppResult<ResolvedPlace> = AppResult.Success(
            ResolvedPlace(
                title = "当前校内位置",
                address = "示例大学东区",
                gcj02Point = GeoPoint(36.000812, 120.122034),
                bd09Point = GeoPoint(36.007064, 120.128667),
            ),
        )

        override suspend fun searchPlaces(query: String, proximityGcj: GeoPoint?): AppResult<List<ResolvedPlace>> =
            AppResult.Success(
                listOf(
                    ResolvedPlace(
                        title = "第一教学楼",
                        address = "示例大学第一教学楼",
                        gcj02Point = GeoPoint(36.0010, 120.1220),
                        bd09Point = GeoPoint(36.0072, 120.1286),
                    ),
                    ResolvedPlace(
                        title = "第三教学楼",
                        address = "示例大学第三教学楼",
                        gcj02Point = GeoPoint(36.0030, 120.1250),
                        bd09Point = GeoPoint(36.0092, 120.1316),
                    ),
                ),
            )

        override suspend fun reverseGeocodeGcj02(gcjLatitude: Double, gcjLongitude: Double): String = "示例大学教学区"
    }

    companion object {
        private const val HTTP_OK = 200
        private const val FIXED_NOW_MS = 1_700_000_000_000L
        private const val CAPACITY = 20
        private const val PERIOD_MS = 1000L
        private val LOCATION_ACTIVE_DETAIL_JSON = """
            {
              "result": 1,
              "data": {
                "id": 7002,
                "clazzid": 1001,
                "name": "位置签到",
                "activeType": 2,
                "otherId": 4,
                "status": 1,
                "ifopenAddress": 1,
                "locationText": "第三教学楼",
                "locationRange": 150,
                "ifNeedVCode": 0,
                "starttime": 1700000000000
              }
            }
        """.trimIndent()
    }
}
