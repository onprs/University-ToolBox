package io.github.onprs.universitytoolbox.feature.unlock.ui

import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.database.entity.AuditLogEntryEntity
import io.github.onprs.universitytoolbox.core.database.entity.FavoriteToolEntity
import io.github.onprs.universitytoolbox.core.database.entity.RecentToolUsageEntity
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import io.github.onprs.universitytoolbox.core.scanner.BarcodeFormat
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScannerMode
import io.github.onprs.universitytoolbox.feature.unlock.domain.UnlockFeedbackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeLicenseEngine : LicenseEngine {
        private val mutableState = MutableStateFlow<LicenseState>(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-CHALLENGE-01"),
        )
        override val state: StateFlow<LicenseState> = mutableState
        var activateCallCount = 0

        override suspend fun getDeviceChallenge(): String = "DEV-CHALLENGE-01"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> {
            activateCallCount += 1
            return if (rawKeyInput == "VALID-KEY-123") {
                val entitlement = LicenseEntitlement(
                    serialId = "SN-TEST-999",
                    tier = LicenseTier.PRO,
                    grantedScopes = setOf("*"),
                    maxSubAccounts = 5,
                    expiresAtEpochSec = null,
                    isOfficialEngine = false,
                )
                mutableState.value = LicenseState.Unlocked(entitlement)
                Result.success(entitlement)
            } else {
                Result.failure(IllegalArgumentException("Invalid key"))
            }
        }

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() {
            mutableState.value = LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-CHALLENGE-01")
        }
    }

    private class FakeMetadataRepository : ToolboxMetadataRepository {
        val actions = mutableListOf<String>()

        override fun observeFavoriteToolIds(): Flow<Set<String>> = flowOf(emptySet())

        override fun observeFavoriteTools(): Flow<List<FavoriteToolEntity>> = flowOf(emptyList())

        override suspend fun toggleFavorite(toolId: String): Boolean = false

        override suspend fun setFavorite(toolId: String, isFavorite: Boolean) = Unit

        override fun observeRecentToolUsages(limit: Int): Flow<List<RecentToolUsageEntity>> = flowOf(emptyList())

        override suspend fun recordToolUsage(toolId: String) = Unit

        override fun observeAuditLogs(limit: Int): Flow<List<AuditLogEntryEntity>> = flowOf(emptyList())

        override suspend fun logAuditEvent(
            category: String,
            action: String,
            status: String,
            summary: String,
            toolId: String?,
        ) {
            actions += "$category:$action:$status"
        }

        override suspend fun exportRedactedAuditText(limit: Int): String = ""

        override suspend fun clearRecentUsages() = Unit

        override suspend fun clearAuditLogs() = Unit

        override suspend fun clearAllHistory() = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activates via deep link qr scan and clears license with confirmation`() = runTest {
        val engine = FakeLicenseEngine()
        val metadata = FakeMetadataRepository()
        val viewModel = UnlockViewModel(engine, metadata)
        advanceUntilIdle()

        assertEquals("DEV-CHALLENGE-01", viewModel.uiState.value.deviceChallenge)
        assertEquals(LockReason.NOT_ACTIVATED, viewModel.uiState.value.lockReason)

        viewModel.applyRouteArguments("universitytoolbox://unlock?key=VALID-KEY-123", manageMode = false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isUnlocked)
        assertEquals(LicenseTier.PRO, viewModel.uiState.value.entitlement?.tier)
        assertEquals(UnlockFeedbackKind.ACTIVATION_SUCCEEDED, viewModel.uiState.value.feedbackKind)
        assertTrue("LICENSE:ACTIVATE:SUCCESS" in metadata.actions)

        viewModel.consumeFeedback()
        viewModel.requestClearLicense()
        assertTrue(viewModel.uiState.value.isClearConfirmOpen)
        viewModel.confirmClearLicense()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertEquals(UnlockFeedbackKind.LICENSE_CLEARED, viewModel.uiState.value.feedbackKind)
        assertTrue("LICENSE:CLEAR:SUCCESS" in metadata.actions)

        viewModel.openScanner()
        viewModel.onBarcodeScanned(
            BarcodeScanResult(
                rawValue = "BAD-KEY",
                displayValue = null,
                format = BarcodeFormat.QR_CODE,
                detectedAtEpochMs = 100L,
                sourceMode = BarcodeScannerMode.LIVE_CAMERA,
            ),
        )
        // 扫码器关闭后的残余帧不应触发重复激活校验
        viewModel.onBarcodeScanned(
            BarcodeScanResult(
                rawValue = "VALID-KEY-123",
                displayValue = null,
                format = BarcodeFormat.QR_CODE,
                detectedAtEpochMs = 110L,
                sourceMode = BarcodeScannerMode.LIVE_CAMERA,
            ),
        )
        advanceUntilIdle()
        assertEquals(2, engine.activateCallCount)
        assertTrue(viewModel.uiState.value.hasActivationError)
        assertEquals(UnlockFeedbackKind.ACTIVATION_FAILED_INVALID, viewModel.uiState.value.feedbackKind)
        assertNull(viewModel.uiState.value.entitlement)
    }

    @Test
    fun `paste sanitization copy feedback sequence and rebind cancel behave consistently`() = runTest {
        val engine = FakeLicenseEngine()
        val metadata = FakeMetadataRepository()
        val viewModel = UnlockViewModel(engine, metadata)
        advanceUntilIdle()

        viewModel.notifyChallengeCopied()
        val firstSeq = viewModel.uiState.value.feedbackSeq
        assertEquals(UnlockFeedbackKind.CHALLENGE_COPIED, viewModel.uiState.value.feedbackKind)
        viewModel.notifyChallengeCopied()
        assertTrue(viewModel.uiState.value.feedbackSeq > firstSeq)

        viewModel.applyPastedText("   ")
        assertEquals(UnlockFeedbackKind.CLIPBOARD_EMPTY, viewModel.uiState.value.feedbackKind)

        // 输入框直接粘贴含首尾空白与换行的密钥时自动清理
        viewModel.updateKeyInput("  \nVALID-KEY-123\r\n  ")
        assertEquals("VALID-KEY-123", viewModel.uiState.value.keyInput)

        viewModel.submitActivation()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        // 已解锁管理模式下进入重绑、打开相机再取消重绑时，相机与草稿同步复位
        viewModel.startRebindMode()
        viewModel.openScanner()
        viewModel.toggleTorch()
        viewModel.updateKeyInput("TEMP-KEY")
        assertTrue(viewModel.uiState.value.isRebinding)
        assertTrue(viewModel.uiState.value.isScannerOpen)

        viewModel.cancelRebindMode()
        assertFalse(viewModel.uiState.value.isRebinding)
        assertFalse(viewModel.uiState.value.isScannerOpen)
        assertFalse(viewModel.uiState.value.torchEnabled)
        assertEquals("", viewModel.uiState.value.keyInput)
    }
}
