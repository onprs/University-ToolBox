package io.github.onprs.universitytoolbox.core.license.api

import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseCoordinatorTest {
    private class FakeLicenseEngine(initialState: LicenseState = LicenseState.Initializing) : LicenseEngine {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<LicenseState> = mutableState

        fun setState(newState: LicenseState) {
            mutableState.value = newState
        }

        override suspend fun getDeviceChallenge(): String = "TEST-CHALLENGE"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException("Not used in coordinator test"))

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() {
            mutableState.value = LicenseState.Locked(LockReason.NOT_ACTIVATED, "TEST-CHALLENGE")
        }
    }

    private data class TestTarget(
        override val id: String,
        override val requiredTier: LicenseTier,
        override val scopeId: String = id,
    ) : LicenseProtectedTarget

    @Test
    fun `denies access when engine is initializing or locked`() = runTest {
        val engine = FakeLicenseEngine(LicenseState.Initializing)
        var nowSec = 1_700_000_000L
        val coordinator = LicenseCoordinator(engine, TimeProvider { nowSec * 1000L })
        val target = TestTarget(id = "chaoxing_assistant", requiredTier = LicenseTier.STANDARD)

        assertEquals(
            AccessDecision.Denied(AccessDenialReason.EngineInitializing),
            coordinator.evaluateAccess(target),
        )
        assertFalse(coordinator.canAccess(target))
        assertFalse(coordinator.isUnlocked.first())
        assertNull(coordinator.currentEntitlementOrNull())
        assertEquals(0, coordinator.maxSubAccounts())

        engine.setState(LicenseState.Locked(LockReason.CLOCK_ROLLBACK_DETECTED, "DEV-001"))
        assertEquals(LockReason.CLOCK_ROLLBACK_DETECTED, coordinator.lockReasonOrNull())
        assertEquals(
            AccessDecision.Denied(AccessDenialReason.LicenseLocked(LockReason.CLOCK_ROLLBACK_DETECTED)),
            coordinator.evaluateAccess(target),
        )
    }

    @Test
    fun `evaluates tier scope expiration and remote kill switch accurately`() = runTest {
        val nowSec = 1_700_000_000L
        val engine = FakeLicenseEngine()
        val coordinator = LicenseCoordinator(engine, TimeProvider { nowSec * 1000L })

        val entitlement = LicenseEntitlement(
            serialId = "SN-TEST-01",
            tier = LicenseTier.STANDARD,
            grantedScopes = setOf("chaoxing_assistant", "gpa_calculator"),
            maxSubAccounts = 3,
            expiresAtEpochSec = nowSec + 3600L,
            isOfficialEngine = false,
        )
        engine.setState(LicenseState.Unlocked(entitlement))

        val chaoxingTool = TestTarget(id = "chaoxing_assistant", requiredTier = LicenseTier.STANDARD)
        val proTool = TestTarget(id = "advanced_tool", requiredTier = LicenseTier.PRO, scopeId = "chaoxing_assistant")
        val unscopedTool = TestTarget(id = "other_tool", requiredTier = LicenseTier.FREE, scopeId = "other_scope")

        assertTrue(coordinator.canAccess(chaoxingTool))
        assertTrue(coordinator.canAccess("gpa_calculator", LicenseTier.FREE))
        assertEquals(
            AccessDecision.Denied(AccessDenialReason.RemotelyDisabled),
            coordinator.evaluateAccess(chaoxingTool, isRemoteDisabled = true),
        )
        assertEquals(
            AccessDecision.Denied(
                AccessDenialReason.InsufficientTier(
                    current = LicenseTier.STANDARD,
                    required = LicenseTier.PRO,
                ),
            ),
            coordinator.evaluateAccess(proTool),
        )
        assertEquals(
            AccessDecision.Denied(AccessDenialReason.MissingScope("other_scope")),
            coordinator.evaluateAccess(unscopedTool),
        )
        assertTrue(coordinator.isUnlocked.first())
        assertEquals(entitlement, coordinator.currentEntitlement.first())

        val expiredEntitlement = entitlement.copy(expiresAtEpochSec = nowSec)
        engine.setState(LicenseState.Unlocked(expiredEntitlement))
        assertEquals(
            AccessDecision.Denied(AccessDenialReason.Expired),
            coordinator.evaluateAccess(chaoxingTool),
        )
        assertNull(coordinator.currentEntitlementOrNull())
        assertEquals(0, coordinator.maxSubAccounts())
    }

    @Test
    fun `subaccount quota checks boundary values and negative counts safely`() {
        val engine = FakeLicenseEngine(
            LicenseState.Unlocked(
                LicenseEntitlement(
                    serialId = "SN-QUOTA",
                    tier = LicenseTier.PRO,
                    grantedScopes = setOf("*"),
                    maxSubAccounts = 3,
                    expiresAtEpochSec = null,
                    isOfficialEngine = true,
                ),
            ),
        )
        val coordinator = LicenseCoordinator(engine)

        assertEquals(3, coordinator.maxSubAccounts())
        assertTrue(coordinator.canBindAdditionalSubAccount(0))
        assertTrue(coordinator.canBindAdditionalSubAccount(2))
        assertFalse(coordinator.canBindAdditionalSubAccount(3))
        assertFalse(coordinator.canBindAdditionalSubAccount(4))
        assertFalse(coordinator.canBindAdditionalSubAccount(-1))
    }
}
