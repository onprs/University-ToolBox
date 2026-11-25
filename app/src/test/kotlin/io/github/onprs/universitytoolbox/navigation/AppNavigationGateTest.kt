package io.github.onprs.universitytoolbox.navigation

import androidx.navigation3.runtime.NavKey
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.AccessDenialReason
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import io.github.onprs.universitytoolbox.core.tool.api.DefaultToolRegistry
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.gpa.api.GPA_TOOL_ID
import io.github.onprs.universitytoolbox.feature.gpa.api.GpaNavKey
import io.github.onprs.universitytoolbox.feature.home.navigation.HomeNavKey
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QR_CODE_STUDIO_TOOL_ID
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QrCodeStudioNavKey
import io.github.onprs.universitytoolbox.feature.settings.navigation.SettingsNavKey
import io.github.onprs.universitytoolbox.feature.unlock.navigation.UnlockNavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationGateTest {

    @Test
    fun `initializing state blocks tool opening until resolved`() {
        val fakeEngine = FakeLicenseEngine(LicenseState.Initializing)
        val gate = createGate(fakeEngine)

        val initUpdate = gate.syncState(LicenseState.Initializing, emptyMap())
        assertEquals(GateUiPhase.Initializing, initUpdate.phase)

        val deepLinkUpdate = gate.handleDeepLink("universitytoolbox://tool/$GPA_TOOL_ID")
        assertEquals(GateUiPhase.Initializing, deepLinkUpdate.phase)
        assertTrue(deepLinkUpdate.effects.isEmpty())

        val lockedUpdate = gate.syncState(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "CHALLENGE-123"),
            emptyMap(),
        )
        val lockedPhase = lockedUpdate.phase as GateUiPhase.Locked
        assertEquals(GPA_TOOL_ID, lockedPhase.blockedToolId)
        assertFalse(gate.canPopBack())
    }

    @Test
    fun `locked state injects activation deep link code and blocks internal tool navigation`() {
        val fakeEngine = FakeLicenseEngine(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "CHALLENGE-001"),
        )
        val gate = createGate(fakeEngine)
        gate.syncState(fakeEngine.state.value, emptyMap())

        val activationUpdate = gate.handleDeepLink("universitytoolbox://unlock?key=TEST-KEY-999")
        val activationLocked = activationUpdate.phase as GateUiPhase.Locked
        assertEquals("TEST-KEY-999", activationLocked.initialActivationCode)

        val toolAttempt = gate.handleDeepLink("universitytoolbox://tool/$CHAOXING_TOOL_ID")
        val afterToolAttempt = toolAttempt.phase as GateUiPhase.Locked
        assertEquals(CHAOXING_TOOL_ID, afterToolAttempt.blockedToolId)

        val settingsAttempt = gate.openSettings()
        assertTrue(settingsAttempt.phase is GateUiPhase.Locked)

        val directEntryAttempt = gate.openToolByEntryKey(GpaNavKey)
        assertTrue(directEntryAttempt.phase is GateUiPhase.Locked)
        assertTrue(directEntryAttempt.effects.isEmpty())
    }

    @Test
    fun `unlocking after blocked tool deep link navigates directly when tier satisfies requirement`() {
        val fakeEngine = FakeLicenseEngine(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "CHALLENGE-002"),
        )
        val gate = createGate(fakeEngine)
        gate.syncState(fakeEngine.state.value, emptyMap())

        gate.handleDeepLink("universitytoolbox://tool/$QR_CODE_STUDIO_TOOL_ID")

        val standardState = LicenseState.Unlocked(entitlement(LicenseTier.STANDARD))
        fakeEngine.emitState(standardState)
        val unlockedUpdate = gate.syncState(standardState, emptyMap())

        val unlockedPhase = unlockedUpdate.phase as GateUiPhase.Unlocked
        assertEquals(listOf<NavKey>(HomeNavKey, QrCodeStudioNavKey), unlockedPhase.backStack)
        assertTrue(GateEffect.RecordToolOpened(QR_CODE_STUDIO_TOOL_ID) in unlockedUpdate.effects)
    }

    @Test
    fun `unlocked free tier allows free tools and redirects standard tools to unlock manage`() {
        val freeState = LicenseState.Unlocked(entitlement(LicenseTier.FREE))
        val fakeEngine = FakeLicenseEngine(freeState)
        val gate = createGate(fakeEngine)
        gate.syncState(freeState, emptyMap())

        val freeOpen = gate.openToolByEntryKey(GpaNavKey)
        val freeStack = (freeOpen.phase as GateUiPhase.Unlocked).backStack
        assertEquals(listOf<NavKey>(HomeNavKey, GpaNavKey), freeStack)
        assertTrue(GateEffect.RecordToolOpened(GPA_TOOL_ID) in freeOpen.effects)

        val popped = gate.popBack()
        assertEquals(listOf<NavKey>(HomeNavKey), (popped.phase as GateUiPhase.Unlocked).backStack)

        val deniedOpen = gate.openToolByEntryKey(QrCodeStudioNavKey)
        val deniedStack = (deniedOpen.phase as GateUiPhase.Unlocked).backStack
        assertEquals(
            listOf<NavKey>(HomeNavKey, UnlockNavKey(manageMode = true)),
            deniedStack,
        )
        val notice = deniedOpen.effects.filterIsInstance<GateEffect.ShowToolLockedNotice>().single()
        assertEquals(QR_CODE_STUDIO_TOOL_ID, notice.toolId)
        assertEquals(
            AccessDenialReason.InsufficientTier(LicenseTier.FREE, LicenseTier.STANDARD),
            notice.reason,
        )

        val upgradedState = LicenseState.Unlocked(entitlement(LicenseTier.STANDARD))
        fakeEngine.emitState(upgradedState)
        val upgradedUpdate = gate.syncState(upgradedState, emptyMap())
        assertEquals(
            listOf<NavKey>(HomeNavKey, QrCodeStudioNavKey),
            (upgradedUpdate.phase as GateUiPhase.Unlocked).backStack,
        )
    }

    @Test
    fun `remote governance kill switch blocks opening and evicts active disabled tool`() {
        val standardState = LicenseState.Unlocked(entitlement(LicenseTier.STANDARD))
        val fakeEngine = FakeLicenseEngine(standardState)
        val gate = createGate(fakeEngine)
        gate.syncState(standardState, emptyMap())

        val opened = gate.openToolByEntryKey(ChaoxingNavKey.Dashboard)
        assertEquals(
            listOf<NavKey>(HomeNavKey, ChaoxingNavKey.Dashboard),
            (opened.phase as GateUiPhase.Unlocked).backStack,
        )

        val disabledMap = mapOf(CHAOXING_TOOL_ID to "Under maintenance")
        val afterDisable = gate.syncState(standardState, disabledMap)
        assertEquals(listOf<NavKey>(HomeNavKey), (afterDisable.phase as GateUiPhase.Unlocked).backStack)
        val disabledNotice = afterDisable.effects.filterIsInstance<GateEffect.ShowToolDisabledNotice>().single()
        assertEquals(CHAOXING_TOOL_ID, disabledNotice.toolId)
        assertEquals("Under maintenance", disabledNotice.message)

        val retryOpen = gate.openToolByEntryKey(ChaoxingNavKey.Dashboard)
        assertEquals(listOf<NavKey>(HomeNavKey), (retryOpen.phase as GateUiPhase.Unlocked).backStack)
        assertTrue(
            retryOpen.effects.any {
                it is GateEffect.ShowToolDisabledNotice && it.toolId == CHAOXING_TOOL_ID
            },
        )
    }

    @Test
    fun `returning to locked state clears unlocked back stack immediately`() {
        val standardState = LicenseState.Unlocked(entitlement(LicenseTier.STANDARD))
        val fakeEngine = FakeLicenseEngine(standardState)
        val gate = createGate(fakeEngine)
        gate.syncState(standardState, emptyMap())
        gate.openSettings()
        val settingsUpdate = gate.openUnlock("REBIND-CODE", manageMode = true)
        assertEquals(
            listOf<NavKey>(HomeNavKey, SettingsNavKey, UnlockNavKey("REBIND-CODE", manageMode = true)),
            (settingsUpdate.phase as GateUiPhase.Unlocked).backStack,
        )

        val lockedState = LicenseState.Locked(LockReason.REVOKED, "CHALLENGE-REV")
        fakeEngine.emitState(lockedState)
        val lockedUpdate = gate.syncState(lockedState, emptyMap())
        val lockedPhase = lockedUpdate.phase as GateUiPhase.Locked
        assertEquals(LockReason.REVOKED, lockedPhase.reason)
        assertNull(lockedPhase.initialActivationCode)

        fakeEngine.emitState(standardState)
        val relockedToUnlocked = gate.syncState(standardState, emptyMap())
        assertEquals(listOf<NavKey>(HomeNavKey), (relockedToUnlocked.phase as GateUiPhase.Unlocked).backStack)
    }

    @Test
    fun `chaoxing bind deep link queues while locked and opens accounts with payload after unlock`() {
        val initialLocked = LicenseState.Locked(LockReason.NOT_ACTIVATED, "CHALLENGE-BIND")
        val fakeEngine = FakeLicenseEngine(initialLocked)
        val gate = createGate(fakeEngine)
        gate.syncState(initialLocked, emptyMap())

        val shareUri = "universitytoolbox://chaoxing-bind?phone=13800138000&pwd=enc_pwd&dc=UNIFIED_DC"
        val lockedUpdate = gate.handleDeepLink(shareUri)
        val lockedPhase = lockedUpdate.phase as GateUiPhase.Locked
        assertEquals(CHAOXING_TOOL_ID, lockedPhase.blockedToolId)

        val standardState = LicenseState.Unlocked(entitlement(LicenseTier.STANDARD))
        fakeEngine.emitState(standardState)
        val unlockedUpdate = gate.syncState(standardState, emptyMap())
        assertEquals(
            listOf<NavKey>(HomeNavKey, ChaoxingNavKey.Accounts(initialSharePayload = shareUri)),
            (unlockedUpdate.phase as GateUiPhase.Unlocked).backStack,
        )
    }

    @Test
    fun `rapid double tap suppresses duplicate entries and duplicate effects until popped or window elapses`() {
        var nowMs = 5_000L
        val freeState = LicenseState.Unlocked(entitlement(LicenseTier.FREE))
        val fakeEngine = FakeLicenseEngine(freeState)
        val gate = createGate(
            engine = fakeEngine,
            gateClock = TimeProvider { nowMs },
        )
        gate.syncState(freeState, emptyMap())

        val firstTap = gate.openToolByEntryKey(GpaNavKey)
        assertEquals(listOf<NavKey>(HomeNavKey, GpaNavKey), (firstTap.phase as GateUiPhase.Unlocked).backStack)
        assertEquals(1, firstTap.effects.count { it == GateEffect.RecordToolOpened(GPA_TOOL_ID) })

        // 毫秒级连点同一工具：不重复入栈，也不重复产生打开计数副作用
        nowMs += 80L
        val secondTap = gate.openToolByEntryKey(GpaNavKey)
        assertEquals(listOf<NavKey>(HomeNavKey, GpaNavKey), (secondTap.phase as GateUiPhase.Unlocked).backStack)
        assertTrue(secondTap.effects.isEmpty())

        // 即使超过防抖窗口，若当前栈顶已是该工具，仍不重复入栈或重复记数
        nowMs += 500L
        val topRepeatTap = gate.openToolByEntryKey(GpaNavKey)
        assertEquals(listOf<NavKey>(HomeNavKey, GpaNavKey), (topRepeatTap.phase as GateUiPhase.Unlocked).backStack)
        assertTrue(topRepeatTap.effects.isEmpty())

        // 返回首页后立即重新点击同一工具可正常进入
        gate.popBack()
        val reopenAfterPop = gate.openToolByEntryKey(GpaNavKey)
        assertEquals(listOf<NavKey>(HomeNavKey, GpaNavKey), (reopenAfterPop.phase as GateUiPhase.Unlocked).backStack)
        assertEquals(1, reopenAfterPop.effects.count { it == GateEffect.RecordToolOpened(GPA_TOOL_ID) })
    }

    @Test
    fun `reopening settings or unlock deduplicates existing route in back stack`() {
        var nowMs = 10_000L
        val standardState = LicenseState.Unlocked(entitlement(LicenseTier.STANDARD))
        val fakeEngine = FakeLicenseEngine(standardState)
        val gate = createGate(
            engine = fakeEngine,
            gateClock = TimeProvider { nowMs },
        )
        gate.syncState(standardState, emptyMap())

        gate.openSettings()
        nowMs += 500L
        gate.openUnlock("CODE-1", manageMode = true)
        nowMs += 500L
        val reopenedSettings = gate.openSettings()
        assertEquals(
            listOf<NavKey>(HomeNavKey, UnlockNavKey("CODE-1", manageMode = true), SettingsNavKey),
            (reopenedSettings.phase as GateUiPhase.Unlocked).backStack,
        )

        nowMs += 500L
        val reopenedUnlock = gate.openUnlock("CODE-2", manageMode = true)
        assertEquals(
            listOf<NavKey>(HomeNavKey, SettingsNavKey, UnlockNavKey("CODE-2", manageMode = true)),
            (reopenedUnlock.phase as GateUiPhase.Unlocked).backStack,
        )
    }

    private fun createGate(
        engine: FakeLicenseEngine,
        gateClock: TimeProvider = TimeProvider { FIXED_EPOCH_SEC * 1_000L },
    ): AppNavigationGate {
        val coordinator = LicenseCoordinator(
            licenseEngine = engine,
            timeProvider = TimeProvider { FIXED_EPOCH_SEC },
        )
        val registry = DefaultToolRegistry(
            setOf(
                ToolDescriptor(
                    id = GPA_TOOL_ID,
                    titleRes = 1,
                    descriptionRes = 2,
                    category = ToolCategory.ACADEMIC,
                    requiredTier = LicenseTier.FREE,
                    entryKey = GpaNavKey,
                ),
                ToolDescriptor(
                    id = QR_CODE_STUDIO_TOOL_ID,
                    titleRes = 3,
                    descriptionRes = 4,
                    category = ToolCategory.MEDIA_UTILITY,
                    requiredTier = LicenseTier.STANDARD,
                    entryKey = QrCodeStudioNavKey,
                ),
                ToolDescriptor(
                    id = CHAOXING_TOOL_ID,
                    titleRes = 5,
                    descriptionRes = 6,
                    category = ToolCategory.ACADEMIC,
                    requiredTier = LicenseTier.STANDARD,
                    entryKey = ChaoxingNavKey.Dashboard,
                ),
            ),
        )
        return AppNavigationGate(
            licenseCoordinator = coordinator,
            toolRegistry = registry,
            timeProvider = gateClock,
        )
    }

    private fun entitlement(tier: LicenseTier): LicenseEntitlement = LicenseEntitlement(
        serialId = "TEST-${tier.name}",
        tier = tier,
        grantedScopes = setOf(LicenseEntitlement.WILDCARD_SCOPE),
        maxSubAccounts = 3,
        expiresAtEpochSec = null,
        isOfficialEngine = false,
    )

    private class FakeLicenseEngine(initialState: LicenseState) : LicenseEngine {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<LicenseState> = mutableState.asStateFlow()

        fun emitState(nextState: LicenseState) {
            mutableState.value = nextState
        }

        override suspend fun getDeviceChallenge(): String = "TEST-CHALLENGE"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException("Not used in gate unit test"))

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() = Unit
    }

    private companion object {
        const val FIXED_EPOCH_SEC = 1_750_000_000L
    }
}
