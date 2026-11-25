package io.github.onprs.universitytoolbox.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolRegistry
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.feature.home.domain.HomeCatalogQuery
import io.github.onprs.universitytoolbox.feature.home.domain.HomeToolFilter
import io.github.onprs.universitytoolbox.feature.home.domain.HomeToolItem
import io.github.onprs.universitytoolbox.feature.home.domain.RecentToolRecord
import io.github.onprs.universitytoolbox.feature.home.domain.ToolTextResolver
import io.github.onprs.universitytoolbox.feature.home.shortcut.ToolShortcutPublisher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 工具箱首页自适应界面状态模型。
 */
data class HomeUiState(
    val searchQuery: String = "",
    val selectedCategory: ToolCategory? = null,
    val selectedToolId: String? = null,
    val pinnedTools: List<HomeToolItem> = emptyList(),
    val recentTools: List<HomeToolItem> = emptyList(),
    val filteredTools: List<HomeToolItem> = emptyList(),
    val licenseState: LicenseState = LicenseState.Initializing,
    val entitlement: LicenseEntitlement? = null,
) {
    val selectedTool: HomeToolItem?
        get() = filteredTools.firstOrNull { it.descriptor.id == selectedToolId }
            ?: pinnedTools.firstOrNull { it.descriptor.id == selectedToolId }
}

private data class FilterInputs(val query: String, val category: ToolCategory?, val selectedToolId: String?)

/**
 * 首页与自适应导航壳控制器：聚合工具注册表、收藏/最近使用、授权状态、远程熔断开关与动态快捷方式。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val licenseCoordinator: LicenseCoordinator,
    private val metadataRepository: ToolboxMetadataRepository,
    private val remoteGovernanceRepository: RemoteGovernanceRepository,
    private val toolTextResolver: ToolTextResolver,
    private val shortcutPublisher: ToolShortcutPublisher,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<ToolCategory?>(null)
    private val selectedToolId = MutableStateFlow<String?>(null)

    private var lastPublishedShortcutSignature: List<Pair<String, Boolean>>? = null
    private var hasExplicitFavoriteState: Boolean = false

    private val filterInputs = combine(
        searchQuery,
        selectedCategory,
        selectedToolId,
    ) { query, category, toolId ->
        FilterInputs(query = query, category = category, selectedToolId = toolId)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        filterInputs,
        licenseCoordinator.state,
        metadataRepository.observeFavoriteTools(),
        metadataRepository.observeRecentToolUsages(),
        remoteGovernanceRepository.disabledTools,
    ) { inputs, licenseState, favoriteEntities, recentEntities, disabledTools ->
        val allTools = toolRegistry.allTools()
        if (favoriteEntities.isNotEmpty()) {
            hasExplicitFavoriteState = true
        }
        val favoriteIds = if (hasExplicitFavoriteState) {
            favoriteEntities.map { it.toolId }.toSet()
        } else {
            null
        }
        val recentRecords = recentEntities.map { entity ->
            RecentToolRecord(
                toolId = entity.toolId,
                lastUsedAtEpochMs = entity.lastUsedAtEpochMs,
            )
        }

        val snapshot = HomeToolFilter.buildSnapshot(
            HomeCatalogQuery(
                allTools = allTools,
                selectedCategory = inputs.category,
                query = inputs.query,
                favoriteToolIds = favoriteIds,
                recentRecords = recentRecords,
                disabledTools = disabledTools,
                licenseCoordinator = licenseCoordinator,
                localizedTitleProvider = toolTextResolver::getTitle,
                localizedDescriptionProvider = toolTextResolver::getDescription,
            ),
        )

        val fullSnapshot = HomeToolFilter.buildSnapshot(
            HomeCatalogQuery(
                allTools = allTools,
                selectedCategory = null,
                query = "",
                favoriteToolIds = favoriteIds,
                recentRecords = recentRecords,
                disabledTools = disabledTools,
                licenseCoordinator = licenseCoordinator,
                localizedTitleProvider = toolTextResolver::getTitle,
                localizedDescriptionProvider = toolTextResolver::getDescription,
            ),
        )
        publishShortcutsIfNeeded(fullSnapshot.filteredTools)

        val effectiveSelectedId = inputs.selectedToolId
            ?: snapshot.filteredTools.firstOrNull()?.descriptor?.id

        HomeUiState(
            searchQuery = inputs.query,
            selectedCategory = inputs.category,
            selectedToolId = effectiveSelectedId,
            pinnedTools = snapshot.pinnedTools,
            recentTools = snapshot.recentTools,
            filteredTools = snapshot.filteredTools,
            licenseState = licenseState,
            entitlement = (licenseState as? LicenseState.Unlocked)?.entitlement,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectCategory(category: ToolCategory?) {
        selectedCategory.value = category
    }

    fun selectTool(toolId: String?) {
        selectedToolId.value = toolId
    }

    fun toggleFavorite(toolId: String) {
        viewModelScope.launch {
            val currentlyPinned = uiState.value.filteredTools
                .firstOrNull { it.descriptor.id == toolId }
                ?.isPinned == true
            if (!hasExplicitFavoriteState) {
                hasExplicitFavoriteState = true
                toolRegistry.allTools()
                    .filter { it.isPinnedByDefault && it.id != toolId }
                    .forEach { defaultTool ->
                        metadataRepository.setFavorite(defaultTool.id, true)
                    }
            }
            metadataRepository.setFavorite(toolId, !currentlyPinned)
        }
    }

    fun onToolOpened(tool: HomeToolItem) {
        val toolId = tool.descriptor.id
        selectedToolId.value = toolId
        shortcutPublisher.reportToolUsed(toolId)
        viewModelScope.launch {
            metadataRepository.recordToolUsage(toolId)
            metadataRepository.logAuditEvent(
                category = "TOOL",
                action = "OPEN",
                status = "SUCCESS",
                summary = "Opened tool $toolId",
                toolId = toolId,
            )
        }
    }

    private fun publishShortcutsIfNeeded(allItems: List<HomeToolItem>) {
        val signature = allItems.map { it.descriptor.id to (it.isAvailable && it.isPinned) } +
            allItems.map { it.descriptor.id to it.isAvailable }
        if (signature != lastPublishedShortcutSignature) {
            lastPublishedShortcutSignature = signature
            shortcutPublisher.publishShortcuts(allItems)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
