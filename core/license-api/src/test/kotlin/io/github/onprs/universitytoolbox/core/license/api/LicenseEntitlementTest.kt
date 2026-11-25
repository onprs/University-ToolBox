package io.github.onprs.universitytoolbox.core.license.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseEntitlementTest {
    @Test
    fun `license tier hierarchy checks satisfies correctly`() {
        assertTrue(LicenseTier.FULL.satisfies(LicenseTier.PRO))
        assertTrue(LicenseTier.PRO.satisfies(LicenseTier.PRO))
        assertTrue(LicenseTier.STANDARD.satisfies(LicenseTier.FREE))
        assertFalse(LicenseTier.FREE.satisfies(LicenseTier.STANDARD))
        assertFalse(LicenseTier.STANDARD.satisfies(LicenseTier.PRO))
    }

    @Test
    fun `entitlement scope check supports wildcard and explicit scopes`() {
        val scoped = LicenseEntitlement(
            serialId = "SN-SCOPED",
            tier = LicenseTier.STANDARD,
            grantedScopes = setOf("gpa_calculator", "qrcode_studio"),
            maxSubAccounts = 2,
            expiresAtEpochSec = null,
            isOfficialEngine = false,
        )
        assertTrue(scoped.hasScope("gpa_calculator"))
        assertFalse(scoped.hasScope("chaoxing_assistant"))

        val wildcard = scoped.copy(grantedScopes = setOf(LicenseEntitlement.WILDCARD_SCOPE))
        assertTrue(wildcard.hasScope("chaoxing_assistant"))
    }

    @Test
    fun `entitlement expiration respects null perpetual and epoch boundary`() {
        val perpetual = LicenseEntitlement(
            serialId = "SN-PERM",
            tier = LicenseTier.FULL,
            grantedScopes = setOf("*"),
            maxSubAccounts = 10,
            expiresAtEpochSec = null,
            isOfficialEngine = true,
        )
        assertFalse(perpetual.isExpiredAt(Long.MAX_VALUE))

        val timed = perpetual.copy(expiresAtEpochSec = 1_800_000_000L)
        assertFalse(timed.isExpiredAt(1_799_999_999L))
        assertTrue(timed.isExpiredAt(1_800_000_000L))
        assertTrue(timed.isExpiredAt(1_800_000_001L))
    }
}
