package io.github.onprs.universitytoolbox.core.ui

import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import io.github.onprs.universitytoolbox.core.tool.api.ToolIconKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUiHelpersTest {
    @Test
    fun `dev stub banner is visible only when engine is not official`() {
        assertTrue(shouldShowDevStubBanner(isOfficialEngine = false))
        assertFalse(shouldShowDevStubBanner(isOfficialEngine = true))
    }

    @Test
    fun `license state stub detection handles locked and unlocked states`() {
        val stubEntitlement = LicenseEntitlement(
            serialId = "STUB-01",
            tier = LicenseTier.FULL,
            grantedScopes = setOf("*"),
            maxSubAccounts = 5,
            expiresAtEpochSec = null,
            isOfficialEngine = false,
        )
        val officialEntitlement = stubEntitlement.copy(isOfficialEngine = true)

        assertTrue(isStubLicenseState(LicenseState.Unlocked(stubEntitlement)))
        assertFalse(isStubLicenseState(LicenseState.Unlocked(officialEntitlement)))
        assertTrue(isStubLicenseState(LicenseState.Locked(LockReason.NOT_ACTIVATED, "DEV-STUB-12345678")))
        assertFalse(isStubLicenseState(LicenseState.Locked(LockReason.NOT_ACTIVATED, "ONPRS-DEV-ABCDEF")))
        assertFalse(isStubLicenseState(LicenseState.Initializing))
    }

    @Test
    fun `all tool icon keys and license tiers resolve cleanly`() {
        ToolIconKey.entries.forEach { key ->
            key.toImageVector()
        }
        assertEquals(R.string.core_ui_tier_free, LicenseTier.FREE.labelRes())
        assertEquals(R.string.core_ui_tier_standard, LicenseTier.STANDARD.labelRes())
        assertEquals(R.string.core_ui_tier_pro, LicenseTier.PRO.labelRes())
        assertEquals(R.string.core_ui_tier_full, LicenseTier.FULL.labelRes())
    }
}
