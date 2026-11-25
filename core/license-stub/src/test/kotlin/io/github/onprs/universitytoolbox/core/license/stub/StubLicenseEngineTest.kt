package io.github.onprs.universitytoolbox.core.license.stub

import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StubLicenseEngineTest {
    private fun createEngine(
        nowEpochSec: () -> Long = { 1_700_000_000L },
        storage: InMemoryLicenseStorage = InMemoryLicenseStorage(),
    ): StubLicenseEngine {
        val testDispatcher = UnconfinedTestDispatcher()
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val mainImmediate: CoroutineDispatcher = testDispatcher
        }
        return StubLicenseEngine(
            deviceIdentityProvider = { "DEV-STUB-TEST0001" },
            licenseStorage = storage,
            timeProvider = TimeProvider { nowEpochSec() * 1000L },
            dispatchers = dispatchers,
            autoInitialize = false,
        )
    }

    @Test
    fun `initial state resolves device challenge and stays locked until activated`() = runTest {
        val engine = createEngine()
        assertEquals(LicenseState.Initializing, engine.state.value)

        val challenge = engine.getDeviceChallenge()
        assertEquals("DEV-STUB-TEST0001", challenge)
        assertEquals(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-STUB-TEST0001"),
            engine.state.value,
        )
    }

    @Test
    fun `unlocks all preset dev keys with expected tiers quotas and non-official flag`() = runTest {
        val cases = listOf(
            StubLicenseEngine.KEY_FREE to (LicenseTier.FREE to 1),
            StubLicenseEngine.KEY_STANDARD to (LicenseTier.STANDARD to 3),
            StubLicenseEngine.KEY_PRO to (LicenseTier.PRO to 5),
            StubLicenseEngine.KEY_FULL to (LicenseTier.FULL to 10),
        )

        cases.forEach { (key, expected) ->
            val engine = createEngine()
            val result = engine.activate("  ${key.lowercase()} ")

            assertTrue(result.isSuccess)
            val entitlement = result.getOrThrow()
            assertEquals(expected.first, entitlement.tier)
            assertEquals(expected.second, entitlement.maxSubAccounts)
            assertFalse(entitlement.isOfficialEngine)
            assertTrue(entitlement.hasScope("any_tool"))
            assertEquals(LicenseState.Unlocked(entitlement), engine.state.value)
        }
    }

    @Test
    fun `invalid key fails and moves state to invalid signature`() = runTest {
        val engine = createEngine()
        val result = engine.activate("INVALID-TEST-KEY")

        assertTrue(result.isFailure)
        assertEquals(
            LicenseState.Locked(LockReason.INVALID_SIGNATURE, "DEV-STUB-TEST0001"),
            engine.state.value,
        )
    }

    @Test
    fun `supports debug lock simulation and clearLicense`() = runTest {
        val storage = InMemoryLicenseStorage()
        val engine = createEngine(storage = storage)
        engine.activate(StubLicenseEngine.KEY_PRO)

        engine.simulateLock(LockReason.EXPIRED)
        assertEquals(
            LicenseState.Locked(LockReason.EXPIRED, "DEV-STUB-TEST0001"),
            engine.state.value,
        )

        engine.simulateLock(LockReason.REVOKED)
        assertEquals(
            LicenseState.Locked(LockReason.REVOKED, "DEV-STUB-TEST0001"),
            engine.state.value,
        )

        engine.clearLicense()
        assertEquals(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-STUB-TEST0001"),
            engine.state.value,
        )
        assertNull(storage.loadActivatedKey())
    }

    @Test
    fun `revoked list and clock rollback or expiration transitions lock active license`() = runTest {
        var nowSec = 1_700_000_000L
        val engine = createEngine(nowEpochSec = { nowSec })

        engine.simulateTimedUnlock(
            tier = LicenseTier.STANDARD,
            expiresAtEpochSec = nowSec + 1_000L,
            maxSubAccounts = 3,
        )
        assertTrue(engine.state.value is LicenseState.Unlocked)

        // 时钟前移至可靠网络时间，然后检测大幅回拨
        engine.syncTrustedNetworkTime(nowSec + 600L)
        assertTrue(engine.state.value is LicenseState.Unlocked)

        engine.syncTrustedNetworkTime(nowSec + 100L)
        assertEquals(
            LicenseState.Locked(LockReason.CLOCK_ROLLBACK_DETECTED, "DEV-STUB-TEST0001"),
            engine.state.value,
        )

        // 恢复至超过过期时间，应报告 EXPIRED
        nowSec += 1_500L
        engine.syncTrustedNetworkTime(nowSec)
        assertEquals(
            LicenseState.Locked(LockReason.EXPIRED, "DEV-STUB-TEST0001"),
            engine.state.value,
        )

        // 吊销当前序列号应报告 REVOKED
        val activated = engine.activate(StubLicenseEngine.KEY_STANDARD).getOrThrow()
        engine.updateRevokedList(setOf(activated.serialId))
        assertEquals(
            LicenseState.Locked(LockReason.REVOKED, "DEV-STUB-TEST0001"),
            engine.state.value,
        )
    }
}
