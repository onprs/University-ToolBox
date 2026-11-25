package io.github.onprs.universitytoolbox.core.tool.api

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    private data object SampleChaoxingKey : NavKey

    private data object SampleGpaKey : NavKey

    private data object SampleQrKey : NavKey

    private val chaoxingTool = ToolDescriptor(
        id = "chaoxing_assistant",
        titleRes = 1,
        descriptionRes = 2,
        category = ToolCategory.ACADEMIC,
        requiredTier = LicenseTier.STANDARD,
        entryKey = SampleChaoxingKey,
        searchKeywords = listOf("chaoxing", "sign", "course"),
        iconKey = ToolIconKey.ATTENDANCE,
        isPinnedByDefault = true,
        sortOrder = 2,
    )

    private val gpaTool = ToolDescriptor(
        id = "gpa_calculator",
        titleRes = 3,
        descriptionRes = 4,
        category = ToolCategory.ACADEMIC,
        requiredTier = LicenseTier.FREE,
        entryKey = SampleGpaKey,
        searchKeywords = listOf("gpa", "grade", "credit"),
        iconKey = ToolIconKey.CALCULATOR,
        sortOrder = 1,
    )

    private val qrTool = ToolDescriptor(
        id = "qrcode_studio",
        titleRes = 5,
        descriptionRes = 6,
        category = ToolCategory.MEDIA_UTILITY,
        requiredTier = LicenseTier.PRO,
        entryKey = SampleQrKey,
        searchKeywords = listOf("qrcode", "barcode"),
        iconKey = ToolIconKey.QR_CODE,
        sortOrder = 3,
    )

    @Test
    fun `registry orders filters and searches tools deterministically`() {
        val registry = DefaultToolRegistry(setOf(qrTool, chaoxingTool, gpaTool))

        assertEquals(listOf("gpa_calculator", "chaoxing_assistant", "qrcode_studio"), registry.allTools().map { it.id })
        assertEquals(chaoxingTool, registry.findById("chaoxing_assistant"))
        assertNull(registry.findById("non_existent"))
        assertEquals(listOf(gpaTool, chaoxingTool), registry.toolsByCategory(ToolCategory.ACADEMIC))
        assertEquals(listOf(qrTool), registry.toolsByCategory(ToolCategory.MEDIA_UTILITY))

        assertEquals(listOf(chaoxingTool), registry.search("SIGN"))
        assertEquals(
            listOf(gpaTool),
            registry.search("绩点") { tool ->
                if (tool.id == "gpa_calculator") "GPA 绩点计算器" else ""
            },
        )
        assertEquals(3, registry.search("  ").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registry rejects duplicate tool ids`() {
        DefaultToolRegistry(setOf(gpaTool, gpaTool.copy(sortOrder = 99)))
    }

    @Test
    fun `tool descriptor integrates cleanly with license coordinator and nav3 entry installers`() {
        val fakeEngine = object : LicenseEngine {
            override val state: StateFlow<LicenseState> = MutableStateFlow(
                LicenseState.Unlocked(
                    LicenseEntitlement(
                        serialId = "SN-TOOL-TEST",
                        tier = LicenseTier.STANDARD,
                        grantedScopes = setOf("*"),
                        maxSubAccounts = 3,
                        expiresAtEpochSec = null,
                        isOfficialEngine = false,
                    ),
                ),
            )

            override suspend fun getDeviceChallenge(): String = "DEV-TOOL-TEST"

            override suspend fun activate(rawKeyInput: String) = Result.failure<LicenseEntitlement>(
                UnsupportedOperationException(),
            )

            override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

            override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

            override suspend fun clearLicense() = Unit
        }
        val coordinator = LicenseCoordinator(fakeEngine)

        assertTrue(coordinator.canAccess(gpaTool))
        assertTrue(coordinator.canAccess(chaoxingTool))
        assertFalse(coordinator.canAccess(qrTool))

        val installers: Set<EntryProviderInstaller> = setOf(
            { entry<SampleGpaKey> { } },
            { entry<SampleQrKey> { } },
        )
        val provider = entryProvider {
            installAll(installers)
        }
        assertNotNull(provider.invoke(SampleGpaKey))
        assertNotNull(provider.invoke(SampleQrKey))
    }
}
