package io.github.onprs.universitytoolbox.feature.home.domain

import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor

/**
 * 首页工具目录筛选查询参数对象。
 */
data class HomeCatalogQuery(
    val allTools: List<ToolDescriptor>,
    val selectedCategory: ToolCategory?,
    val query: String,
    val favoriteToolIds: Set<String>?,
    val recentRecords: List<RecentToolRecord>,
    val disabledTools: Map<String, String?>,
    val licenseCoordinator: LicenseCoordinator,
    val localizedTitleProvider: (ToolDescriptor) -> String,
    val localizedDescriptionProvider: (ToolDescriptor) -> String,
    val maxRecentItems: Int = HomeToolFilter.DEFAULT_MAX_RECENT_ITEMS,
)

/**
 * 首页工具目录筛选、置顶收藏、最近使用与状态合并纯逻辑。
 */
object HomeToolFilter {
    const val DEFAULT_MAX_RECENT_ITEMS = 6

    /**
     * 生成首页展示所需的置顶列表、最近使用列表及按分类/关键词筛选后的全部工具列表。
     */
    fun buildSnapshot(catalogQuery: HomeCatalogQuery): HomeCatalogSnapshot {
        val recentById = catalogQuery.recentRecords.associateBy { it.toolId }
        val resolvedFavorites = catalogQuery.favoriteToolIds
            ?: catalogQuery.allTools.filter { it.isPinnedByDefault }.map { it.id }.toSet()

        val enriched = catalogQuery.allTools.map { descriptor ->
            val isRemoteDisabled = descriptor.id in catalogQuery.disabledTools
            val decision = catalogQuery.licenseCoordinator.evaluateAccess(
                descriptor,
                isRemoteDisabled = isRemoteDisabled,
            )
            HomeToolItem(
                descriptor = descriptor,
                accessStatus = decision.toToolAccessStatus(catalogQuery.disabledTools[descriptor.id]),
                isPinned = descriptor.id in resolvedFavorites,
                lastUsedAtEpochMs = recentById[descriptor.id]?.lastUsedAtEpochMs,
            )
        }

        val pinnedTools = enriched
            .filter { it.isPinned }
            .sortedWith(compareBy<HomeToolItem> { it.descriptor.sortOrder }.thenBy { it.descriptor.id })

        val recentTools = enriched
            .filter { it.lastUsedAtEpochMs != null }
            .sortedByDescending { it.lastUsedAtEpochMs }
            .take(catalogQuery.maxRecentItems.coerceAtLeast(0))

        val trimmedQuery = catalogQuery.query.trim()
        val filteredTools = enriched
            .asSequence()
            .filter { catalogQuery.selectedCategory == null || it.descriptor.category == catalogQuery.selectedCategory }
            .map { item ->
                val score = ToolSearchMatcher.matchScore(
                    tool = item.descriptor,
                    rawQuery = trimmedQuery,
                    localizedTitle = catalogQuery.localizedTitleProvider(item.descriptor),
                    localizedDescription = catalogQuery.localizedDescriptionProvider(item.descriptor),
                )
                item to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<HomeToolItem, Int>> { (_, score) -> score }
                    .thenByDescending { (item, _) -> item.isPinned }
                    .thenBy { (item, _) -> item.descriptor.sortOrder }
                    .thenBy { (item, _) -> item.descriptor.id },
            )
            .map { (item, _) -> item }
            .toList()

        return HomeCatalogSnapshot(
            pinnedTools = pinnedTools,
            recentTools = recentTools,
            filteredTools = filteredTools,
        )
    }
}
