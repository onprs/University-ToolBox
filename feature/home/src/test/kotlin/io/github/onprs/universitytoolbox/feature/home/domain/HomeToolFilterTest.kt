package io.github.onprs.universitytoolbox.feature.home.domain

import androidx.navigation3.runtime.NavKey
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.core.tool.api.ToolIconKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeToolFilterTest {
    private data object GpaKey : NavKey

    private data object QrKey : NavKey

    private data object SignKey : NavKey

    private val gpaTool = ToolDescriptor(
        id = "gpa_calculator",
        titleRes = 1,
        descriptionRes = 2,
        category = ToolCategory.ACADEMIC,
        requiredTier = LicenseTier.FREE,
        entryKey = GpaKey,
        searchKeywords = listOf("gpa", "credit"),
        iconKey = ToolIconKey.CALCULATOR,
        isPinnedByDefault = true,
        sortOrder = 1,
    )

    private val qrTool = ToolDescriptor(
        id = "qrcode_studio",
        titleRes = 3,
        descriptionRes = 4,
        category = ToolCategory.MEDIA_UTILITY,
        requiredTier = LicenseTier.STANDARD,
        entryKey = QrKey,
        searchKeywords = listOf("qr", "barcode"),
        iconKey = ToolIconKey.QR_CODE,
        isPinnedByDefault = false,
        sortOrder = 2,
    )

    private val signTool = ToolDescriptor(
        id = "chaoxing_assistant",
        titleRes = 5,
        descriptionRes = 6,
        category = ToolCategory.ACADEMIC,
        requiredTier = LicenseTier.PRO,
        entryKey = SignKey,
        searchKeywords = listOf("sign", "course"),
        iconKey = ToolIconKey.ATTENDANCE,
        isPinnedByDefault = false,
        sortOrder = 3,
    )

    private val titles = mapOf(
        "gpa_calculator" to "学分绩点计算器",
        "qrcode_studio" to "二维码检视台",
        "chaoxing_assistant" to "学习通辅助",
    )

    private fun fakeCoordinator(tier: LicenseTier): LicenseCoordinator {
        val engine = object : LicenseEngine {
            override val state: StateFlow<LicenseState> = MutableStateFlow(
                LicenseState.Unlocked(
                    LicenseEntitlement(
                        serialId = "SN-HOME-TEST",
                        tier = tier,
                        grantedScopes = setOf("*"),
                        maxSubAccounts = 3,
                        expiresAtEpochSec = null,
                        isOfficialEngine = false,
                    ),
                ),
            )

            override suspend fun getDeviceChallenge(): String = "DEV-TEST"

            override suspend fun activate(rawKeyInput: String) =
                Result.failure<LicenseEntitlement>(UnsupportedOperationException())

            override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

            override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

            override suspend fun clearLicense() = Unit
        }
        return LicenseCoordinator(engine)
    }

    @Test
    fun `filters by category pinyin query and reflects access and kill switch states`() {
        val coordinator = fakeCoordinator(LicenseTier.STANDARD)
        val snapshot = HomeToolFilter.buildSnapshot(
            HomeCatalogQuery(
                allTools = listOf(gpaTool, qrTool, signTool),
                selectedCategory = null,
                query = "",
                favoriteToolIds = setOf("gpa_calculator", "qrcode_studio"),
                recentRecords = listOf(
                    RecentToolRecord("qrcode_studio", 100L),
                    RecentToolRecord("gpa_calculator", 200L),
                ),
                disabledTools = mapOf("qrcode_studio" to "维护中"),
                licenseCoordinator = coordinator,
                localizedTitleProvider = { titles[it.id].orEmpty() },
                localizedDescriptionProvider = { "" },
            ),
        )

        assertEquals(listOf("gpa_calculator", "qrcode_studio"), snapshot.pinnedTools.map { it.descriptor.id })
        assertEquals(listOf("gpa_calculator", "qrcode_studio"), snapshot.recentTools.map { it.descriptor.id })

        val gpaItem = snapshot.filteredTools.first { it.descriptor.id == "gpa_calculator" }
        val qrItem = snapshot.filteredTools.first { it.descriptor.id == "qrcode_studio" }
        val signItem = snapshot.filteredTools.first { it.descriptor.id == "chaoxing_assistant" }

        assertTrue(gpaItem.isAvailable)
        assertEquals(ToolAccessStatus.RemotelyDisabled("维护中"), qrItem.accessStatus)
        assertTrue(signItem.accessStatus is ToolAccessStatus.Locked)

        val searchSnapshot = HomeToolFilter.buildSnapshot(
            HomeCatalogQuery(
                allTools = listOf(gpaTool, qrTool, signTool),
                selectedCategory = ToolCategory.ACADEMIC,
                query = "xxt",
                favoriteToolIds = null,
                recentRecords = emptyList(),
                disabledTools = emptyMap(),
                licenseCoordinator = coordinator,
                localizedTitleProvider = { titles[it.id].orEmpty() },
                localizedDescriptionProvider = { "" },
            ),
        )
        assertEquals(listOf("chaoxing_assistant"), searchSnapshot.filteredTools.map { it.descriptor.id })
    }
}
