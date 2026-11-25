package io.github.onprs.universitytoolbox.feature.home.ui

import androidx.navigation3.runtime.NavKey
import app.cash.turbine.test
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.database.entity.AuditLogEntryEntity
import io.github.onprs.universitytoolbox.core.database.entity.FavoriteToolEntity
import io.github.onprs.universitytoolbox.core.database.entity.RecentToolUsageEntity
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.tool.api.DefaultToolRegistry
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.core.update.UpdateManifest
import io.github.onprs.universitytoolbox.feature.home.domain.HomeToolItem
import io.github.onprs.universitytoolbox.feature.home.domain.ToolAccessStatus
import io.github.onprs.universitytoolbox.feature.home.domain.ToolTextResolver
import io.github.onprs.universitytoolbox.feature.home.shortcut.ToolShortcutPublisher
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private data object GpaKey : NavKey

    private data object QrKey : NavKey

    private class FakeMetadataRepository : ToolboxMetadataRepository {
        val favorites = MutableStateFlow<List<FavoriteToolEntity>>(emptyList())
        val recents = MutableStateFlow<List<RecentToolUsageEntity>>(emptyList())
        val openedTools = mutableListOf<String>()

        override fun observeFavoriteToolIds(): Flow<Set<String>> = flowOf(emptySet())

        override fun observeFavoriteTools(): Flow<List<FavoriteToolEntity>> = favorites

        override suspend fun toggleFavorite(toolId: String): Boolean {
            val current = favorites.value.toMutableList()
            val exists = current.any { it.toolId == toolId }
            if (exists) {
                favorites.value = current.filterNot { it.toolId == toolId }
                return false
            }
            favorites.value = current + FavoriteToolEntity(toolId, 100L)
            return true
        }

        override suspend fun setFavorite(toolId: String, isFavorite: Boolean) = Unit

        override fun observeRecentToolUsages(limit: Int): Flow<List<RecentToolUsageEntity>> = recents

        override suspend fun recordToolUsage(toolId: String) {
            openedTools += toolId
            recents.value = listOf(RecentToolUsageEntity(toolId, 500L, 1))
        }

        override fun observeAuditLogs(limit: Int): Flow<List<AuditLogEntryEntity>> = flowOf(emptyList())

        override suspend fun logAuditEvent(
            category: String,
            action: String,
            status: String,
            summary: String,
            toolId: String?,
        ) = Unit

        override suspend fun exportRedactedAuditText(limit: Int): String = ""

        override suspend fun clearRecentUsages() = Unit

        override suspend fun clearAuditLogs() = Unit

        override suspend fun clearAllHistory() = Unit
    }

    private class FakeGovernanceRepo : RemoteGovernanceRepository {
        override val disabledTools = MutableStateFlow<Map<String, String?>>(emptyMap())
        override val latestManifest = MutableStateFlow<UpdateManifest?>(null)

        override fun isToolDisabled(toolId: String): Boolean = toolId in disabledTools.value

        override fun disableReason(toolId: String): String? = disabledTools.value[toolId]

        override suspend fun fetchAndApplyManifest(manifestUrl: String) = throw UnsupportedOperationException()

        override suspend fun applyManifest(manifest: UpdateManifest) = Unit
    }

    private class FakeShortcutPublisher : ToolShortcutPublisher {
        val publishedIds = mutableListOf<List<String>>()
        val reportedUsedIds = mutableListOf<String>()

        override fun publishShortcuts(tools: List<HomeToolItem>) {
            publishedIds += tools.filter { it.isAvailable }.map { it.descriptor.id }
        }

        override fun reportToolUsed(toolId: String) {
            reportedUsedIds += toolId
        }
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
    fun `home view model reacts to search license remote kill switch and publishes available shortcuts`() = runTest {
        val gpaTool = ToolDescriptor(
            id = "gpa_calculator",
            titleRes = 1,
            descriptionRes = 2,
            category = ToolCategory.ACADEMIC,
            requiredTier = LicenseTier.FREE,
            entryKey = GpaKey,
            isPinnedByDefault = true,
            sortOrder = 1,
        )
        val qrTool = ToolDescriptor(
            id = "qrcode_studio",
            titleRes = 3,
            descriptionRes = 4,
            category = ToolCategory.MEDIA_UTILITY,
            requiredTier = LicenseTier.STANDARD,
            entryKey = QrKey,
            sortOrder = 2,
        )
        val registry = DefaultToolRegistry(setOf(gpaTool, qrTool))
        val licenseState = MutableStateFlow<LicenseState>(
            LicenseState.Unlocked(
                LicenseEntitlement(
                    serialId = "SN-HOME-VM",
                    tier = LicenseTier.FREE,
                    grantedScopes = setOf("*"),
                    maxSubAccounts = 1,
                    expiresAtEpochSec = null,
                    isOfficialEngine = false,
                ),
            ),
        )
        val engine = object : LicenseEngine {
            override val state: StateFlow<LicenseState> = licenseState
            override suspend fun getDeviceChallenge(): String = "DEV"
            override suspend fun activate(rawKeyInput: String) = Result.failure<LicenseEntitlement>(Exception())
            override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit
            override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit
            override suspend fun clearLicense() = Unit
        }
        val coordinator = LicenseCoordinator(engine)
        val metadata = FakeMetadataRepository()
        val governance = FakeGovernanceRepo()
        val shortcuts = FakeShortcutPublisher()
        val textResolver = object : ToolTextResolver {
            override fun getTitle(tool: ToolDescriptor): String =
                if (tool.id == "gpa_calculator") "学分绩点计算器" else "条码检视工作台"

            override fun getDescription(tool: ToolDescriptor): String = ""
        }

        val viewModel = HomeViewModel(
            toolRegistry = registry,
            licenseCoordinator = coordinator,
            metadataRepository = metadata,
            remoteGovernanceRepository = governance,
            toolTextResolver = textResolver,
            shortcutPublisher = shortcuts,
        )

        viewModel.uiState.test {
            skipItems(1)
            val initial = awaitItem()
            assertEquals(2, initial.filteredTools.size)
            assertTrue(initial.filteredTools.first { it.descriptor.id == "gpa_calculator" }.isAvailable)
            val qrStatus = initial.filteredTools.first { it.descriptor.id == "qrcode_studio" }.accessStatus
            assertTrue(qrStatus is ToolAccessStatus.Locked)
            assertEquals(listOf("gpa_calculator"), shortcuts.publishedIds.last())

            governance.disabledTools.value = mapOf("gpa_calculator" to "Maintenance")
            advanceUntilIdle()
            val afterKillSwitch = awaitItem()
            assertEquals(
                ToolAccessStatus.RemotelyDisabled("Maintenance"),
                afterKillSwitch.filteredTools.first { it.descriptor.id == "gpa_calculator" }.accessStatus,
            )
            assertEquals(emptyList<String>(), shortcuts.publishedIds.last())

            governance.disabledTools.value = emptyMap()
            viewModel.updateSearchQuery("jd")
            advanceUntilIdle()
            val afterSearch = expectMostRecentItem()
            assertEquals(listOf("gpa_calculator"), afterSearch.filteredTools.map { it.descriptor.id })

            viewModel.onToolOpened(afterSearch.filteredTools.single())
            advanceUntilIdle()
            assertTrue("gpa_calculator" in metadata.openedTools)
            assertTrue("gpa_calculator" in shortcuts.reportedUsedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
