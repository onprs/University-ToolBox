package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavKey
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.core.update.UpdateManifest
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActivity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActivityStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAuthRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrLoginSession
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSchoolOption
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSignType
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingUserSignStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.AccountBindResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingLoginTab
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingAccountsViewModel
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingActivitiesViewModel
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingDashboardViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChaoxingNavigationAndFlowsTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sub navigation intercepts back step by step preserves overlay priority and survives screen rotation`() {
        var currentVisibleRoute = ""
        val isChildOverlayOpen = mutableStateOf(false)
        var hostBackInvokedCount = 0
        var navigateHolder: ((ChaoxingNavKey) -> Unit)? = null

        fun mountNavigationHost(activity: ComponentActivity) {
            activity.setContent {
                BackHandler(enabled = true) {
                    hostBackInvokedCount++
                }
                ChaoxingSubNavigationContainer(
                    initialRoute = ChaoxingNavKey.Dashboard,
                    onExitToHost = { hostBackInvokedCount++ },
                ) { onNavigate, _, _ ->
                    navigateHolder = onNavigate
                    entry<ChaoxingNavKey.Dashboard> {
                        LifecycleResumeEffect(Unit) {
                            currentVisibleRoute = "Dashboard"
                            onPauseOrDispose {}
                        }
                        Text("Dashboard")
                    }
                    entry<ChaoxingNavKey.Accounts> { key ->
                        LifecycleResumeEffect(key) {
                            currentVisibleRoute = "Accounts:${key.initialSharePayload.orEmpty()}"
                            onPauseOrDispose {}
                        }
                        Text("Accounts")
                    }
                    entry<ChaoxingNavKey.CourseActivities> { key ->
                        LifecycleResumeEffect(key) {
                            currentVisibleRoute = "Activities:${key.courseName}"
                            onPauseOrDispose {}
                        }
                        Text(key.courseName)
                    }
                    entry<ChaoxingNavKey.SignExecution> { key ->
                        LifecycleResumeEffect(key) {
                            currentVisibleRoute = "SignExecution:${key.activeId}"
                            onPauseOrDispose {}
                        }
                        BackHandler(enabled = isChildOverlayOpen.value) {
                            isChildOverlayOpen.value = false
                        }
                        Text("Sign")
                    }
                }
            }
        }

        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        mountNavigationHost(controller.get())
        settleComposeFrames()
        assertEquals("Dashboard", currentVisibleRoute)

        navigateHolder?.invoke(ChaoxingNavKey.CourseActivities("cx_1", 10L, 20L, 30L, "高等数学"))
        settleComposeFrames()
        assertEquals("Activities:高等数学", currentVisibleRoute)

        navigateHolder?.invoke(ChaoxingNavKey.SignExecution("cx_1", 10L, 20L, 99L, "高等数学"))
        isChildOverlayOpen.value = true
        settleComposeFrames()
        assertEquals("SignExecution:99", currentVisibleRoute)

        // 1. 子页面内部全屏覆盖层（如地图选点）开启时，系统返回优先关闭覆盖层而非弹出页面
        controller.get().onBackPressedDispatcher.onBackPressed()
        settleComposeFrames()
        assertFalse(isChildOverlayOpen.value)
        assertEquals("SignExecution:99", currentVisibleRoute)
        assertEquals(0, hostBackInvokedCount)

        // 2. 在三级签到页触发系统返回，回到二级课程活动页，绝不穿透退出学习通模块
        controller.get().onBackPressedDispatcher.onBackPressed()
        settleComposeFrames()
        assertEquals("Activities:高等数学", currentVisibleRoute)
        assertEquals(0, hostBackInvokedCount)

        // 3. 在二级课程活动页触发系统返回，回到一级课程主页
        controller.get().onBackPressedDispatcher.onBackPressed()
        settleComposeFrames()
        assertEquals("Dashboard", currentVisibleRoute)
        assertEquals(0, hostBackInvokedCount)

        // 4. 在一级主页再次触发系统返回，交由外层宿主处理
        controller.get().onBackPressedDispatcher.onBackPressed()
        settleComposeFrames()
        assertEquals(1, hostBackInvokedCount)

        // 5. 进入账号绑定页后模拟屏幕旋转触发 Activity 重建，验证子路由栈不丢失且系统返回仍正常退回主页
        navigateHolder?.invoke(ChaoxingNavKey.Accounts("share_tok"))
        settleComposeFrames()
        assertEquals("Accounts:share_tok", currentVisibleRoute)

        val bundle = android.os.Bundle()
        controller.saveInstanceState(bundle)
        controller.pause().stop().destroy()

        val recreatedController = Robolectric.buildActivity(ComponentActivity::class.java).create(bundle)
        mountNavigationHost(recreatedController.get())
        recreatedController.start().restoreInstanceState(bundle).resume().visible()
        settleComposeFrames()
        assertEquals("Accounts:share_tok", currentVisibleRoute)

        recreatedController.get().onBackPressedDispatcher.onBackPressed()
        settleComposeFrames()
        assertEquals("Dashboard", currentVisibleRoute)
        assertEquals(1, hostBackInvokedCount)
        recreatedController.pause().stop().destroy()
        settleComposeFrames()
    }

    private fun settleComposeFrames() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
    }

    @Test
    fun `pushChaoxingRoute deduplicates consecutive identical keys and updates Accounts payload in place`() {
        val stack = mutableStateListOf<NavKey>(ChaoxingNavKey.Dashboard)
        val activitiesKey = ChaoxingNavKey.CourseActivities("cx_1", 1L, 2L, 3L, "离散数学")

        stack.pushChaoxingRoute(activitiesKey)
        stack.pushChaoxingRoute(activitiesKey)
        assertEquals(2, stack.size)

        stack.pushChaoxingRoute(ChaoxingNavKey.Accounts("payload_1"))
        stack.pushChaoxingRoute(ChaoxingNavKey.Accounts("payload_2"))
        assertEquals(3, stack.size)
        assertEquals(ChaoxingNavKey.Accounts("payload_2"), stack.last())

        stack.popOrResetToDashboard()
        assertEquals(listOf<NavKey>(ChaoxingNavKey.Dashboard), stack.toList())
    }

    @Test
    fun `activities viewModel pauses auto monitor in background and refreshes when foreground resumes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val accountDao = mockk<ChaoxingAccountDao>()
        val remote = mockk<ChaoxingRemoteDataSource>()
        val fetchCounter = AtomicInteger(0)
        coEvery { accountDao.getById("cx_1") } returns null
        coEvery {
            remote.fetchActivities(any(), any(), any(), any(), any(), any())
        } answers {
            val count = fetchCounter.incrementAndGet()
            AppResult.Success(
                listOf(
                    ChaoxingActivity(
                        activeId = 100L + count,
                        courseId = 10L,
                        clazzId = 20L,
                        title = "第${count}次同步",
                        activeType = 2,
                        otherId = "0",
                        signType = ChaoxingSignType.NORMAL,
                        isSignOut = false,
                        activityStatus = ChaoxingActivityStatus.IN_PROGRESS,
                        userStatus = ChaoxingUserSignStatus.UNSIGNED,
                        startTimeMs = 1_700_000_000_000L,
                        endTimeMs = null,
                        displayTime = "",
                        releaseNum = 0,
                        attendNum = 0,
                    ),
                ),
            )
        }

        val vm = ChaoxingActivitiesViewModel(
            route = ChaoxingNavKey.CourseActivities("cx_1", 10L, 20L, 30L, "操作系统"),
            accountDao = accountDao,
            remoteDataSource = remote,
            timeProvider = TimeProvider { 1_700_000_000_000L },
        )
        runCurrent()
        assertEquals(1, fetchCounter.get())

        // 开启自动轮询：前台每 10 秒请求一次
        vm.toggleAutoMonitor(true)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(2, fetchCounter.get())

        // 模拟退到后台（Lifecycle STOPPED）：暂停轮询，60 秒内不应产生任何网络请求，但开关状态保持开启
        vm.setForegroundActive(false)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(2, fetchCounter.get())
        assertTrue(vm.uiState.value.isAutoMonitorEnabled)

        // 模拟回到前台（或从签到执行页返回）：立即刷新一次最新状态，并恢复 10 秒轮询
        vm.setForegroundActive(true)
        runCurrent()
        assertEquals(3, fetchCounter.get())

        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(4, fetchCounter.get())

        vm.toggleAutoMonitor(false)
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(4, fetchCounter.get())
    }

    @Test
    fun `accounts viewModel cancels qr polling on tab switch and resumes polling when returning to qr tab`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val accountManager = mockk<ChaoxingAccountManager>(relaxed = true)
        val authRemote = mockk<ChaoxingAuthRemoteDataSource>()
        val licenseCoordinator = LicenseCoordinator(UnlockedLicenseEngine(), TimeProvider { 1_700_000_000_000L })
        val scanner = object : BarcodeScanner {
            override suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> =
                AppResult.Success(emptyList())

            override suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> =
                AppResult.Success(emptyList())
        }

        every { accountManager.observeAccounts() } returns flowOf(emptyList())
        every { accountManager.hideDeviceCodeBadge } returns flowOf(false)

        val session = ChaoxingQrLoginSession("prov_1", "uuid_1", "enc_1", "https://localhost/qr.png")
        var qrConfirmed = false
        val pollCalls = AtomicInteger(0)

        coEvery { accountManager.startQrLogin() } returns AppResult.Success(session)
        coEvery { authRemote.fetchQrImageBytes(any(), any()) } returns AppResult.Success(byteArrayOf(1, 2, 3))
        coEvery { accountManager.pollQrLogin(session) } answers {
            pollCalls.incrementAndGet()
            if (qrConfirmed) {
                AccountBindResult.Bound(
                    account = sampleAccount("cx_qr_1"),
                    isFirstAccount = true,
                )
            } else {
                null
            }
        }

        val vm = ChaoxingAccountsViewModel(accountManager, authRemote, licenseCoordinator, scanner)
        runCurrent()

        // 切到扫码 Tab：创建会话并按 3 秒间隔轮询
        vm.selectTab(ChaoxingLoginTab.QR_CODE)
        runCurrent()
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(1, pollCalls.get())

        // 切回密码登录 Tab：轮询协程必须立即取消，15 秒内不再发起轮询请求
        vm.selectTab(ChaoxingLoginTab.PASSWORD)
        advanceTimeBy(15_000L)
        runCurrent()
        assertEquals(1, pollCalls.get())

        // 同时验证手动编辑学校输入框会清理已选学校，避免旧学校 fid 残留
        val school = ChaoxingSchoolOption(schoolId = 88L, fid = "1088", name = "示例理工大学")
        vm.selectSchool(school)
        assertEquals(school, vm.uiState.value.selectedSchool)
        vm.updateSchoolKeyword("")
        assertNull(vm.uiState.value.selectedSchool)

        // 再次切回扫码 Tab（此时已有未过期的 qrSession）：应立即恢复轮询并成功捕获扫码确认结果
        qrConfirmed = true
        vm.selectTab(ChaoxingLoginTab.QR_CODE)
        runCurrent()
        assertEquals(2, pollCalls.get())
        assertNull(vm.uiState.value.qrSession)
        assertEquals("扫码登录成功", vm.uiState.value.qrStatusText)
    }

    @Test
    fun `dashboard viewModel cancels stale sync and clears error state when switching accounts`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val accountManager = mockk<ChaoxingAccountManager>()
        val accountsState = MutableStateFlow(
            listOf(sampleAccount("cx_a", isPrimary = true), sampleAccount("cx_b", isPrimary = false)),
        )
        every { accountManager.observeAccounts() } returns accountsState
        every { accountManager.observeCourses(any()) } returns flowOf(emptyList())
        coEvery { accountManager.hasCachedCourses(any()) } returns false

        val gateA = CompletableDeferred<AppResult<List<Nothing>>>()
        coEvery { accountManager.refreshCourses("cx_a") } coAnswers { gateA.await() }
        coEvery { accountManager.refreshCourses("cx_b") } returns AppResult.Success(emptyList())

        val licenseCoordinator = LicenseCoordinator(UnlockedLicenseEngine(), TimeProvider { 1_700_000_000_000L })
        val vm = ChaoxingDashboardViewModel(accountManager, licenseCoordinator, EmptyGovernanceRepo())
        val collectorJob = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value.isLoadingCourses)

        // 当账号 A 仍在慢速请求时切换到账号 B：应立即取消账号 A 的同步并正常完成账号 B 的课程同步，不残留报错
        vm.selectAccount("cx_b")
        gateA.complete(AppResult.Failure(DomainError.Network("账号A超时")))
        advanceUntilIdle()

        assertEquals("cx_b", vm.uiState.value.selectedAccountId)
        assertFalse(vm.uiState.value.isLoadingCourses)
        assertNull(vm.uiState.value.errorMessage)
        coVerify(exactly = 1) { accountManager.refreshCourses("cx_b") }
        collectorJob.cancel()
    }

    private fun sampleAccount(id: String, isPrimary: Boolean = true): ChaoxingAccountEntity = ChaoxingAccountEntity(
        accountId = id,
        uid = id,
        fid = "100",
        maskedAccount = "138****0000",
        displayName = "测试账号$id",
        schoolName = "示例大学",
        avatarUrl = "",
        encryptedPasswordCiphertext = null,
        encryptedCookieVaultRef = id,
        isPrimary = isPrimary,
        isEnabled = true,
        sessionStatus = ChaoxingAccountManager.SESSION_ACTIVE,
        lastVerifiedAtMs = 1_700_000_000_000L,
        createdAtMs = 1_700_000_000_000L,
    )

    private class UnlockedLicenseEngine : LicenseEngine {
        override val state: StateFlow<LicenseState> = MutableStateFlow(
            LicenseState.Unlocked(LicenseEntitlement("SN-TEST", LicenseTier.STANDARD, setOf("*"), 3, null, false)),
        )

        override suspend fun getDeviceChallenge(): String = "DEV-TEST"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException())

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() = Unit
    }

    private class EmptyGovernanceRepo : RemoteGovernanceRepository {
        override val disabledTools: StateFlow<Map<String, String?>> = MutableStateFlow(emptyMap())
        override val latestManifest: StateFlow<UpdateManifest?> = MutableStateFlow(null)

        override fun isToolDisabled(toolId: String): Boolean = false

        override fun disableReason(toolId: String): String? = null

        override suspend fun fetchAndApplyManifest(manifestUrl: String): AppResult<UpdateManifest> = error("Unused")

        override suspend fun applyManifest(manifest: UpdateManifest) = Unit
    }
}
