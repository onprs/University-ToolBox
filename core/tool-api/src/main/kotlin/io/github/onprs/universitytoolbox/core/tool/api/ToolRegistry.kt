package io.github.onprs.universitytoolbox.core.tool.api

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已注册工具目录查询接口。
 */
interface ToolRegistry {
    /** 按排序权重与标识返回全部已注册工具。 */
    fun allTools(): List<ToolDescriptor>

    /** 按唯一标识查找工具描述。 */
    fun findById(toolId: String): ToolDescriptor?

    /** 按分类筛选工具列表。 */
    fun toolsByCategory(category: ToolCategory): List<ToolDescriptor>

    /** 按关键字和本地化标题匹配工具。 */
    fun search(query: String, localizedTitleProvider: (ToolDescriptor) -> String = { "" }): List<ToolDescriptor>
}

/**
 * 基于 Hilt Multibinding 集合的默认工具注册表实现。
 */
@Singleton
class DefaultToolRegistry @Inject constructor(registeredTools: Set<@JvmSuppressWildcards ToolDescriptor>) :
    ToolRegistry {
    private val orderedTools: List<ToolDescriptor>
    private val toolsById: Map<String, ToolDescriptor>

    init {
        val sorted = registeredTools.sortedWith(
            compareBy<ToolDescriptor> { it.sortOrder }
                .thenBy { it.category.sortOrder }
                .thenBy { it.id },
        )
        val byId = LinkedHashMap<String, ToolDescriptor>(sorted.size)
        for (tool in sorted) {
            require(byId.put(tool.id, tool) == null) {
                "Duplicate tool id registered: ${tool.id}"
            }
        }
        orderedTools = sorted
        toolsById = byId
    }

    override fun allTools(): List<ToolDescriptor> = orderedTools

    override fun findById(toolId: String): ToolDescriptor? = toolsById[toolId]

    override fun toolsByCategory(category: ToolCategory): List<ToolDescriptor> =
        orderedTools.filter { it.category == category }

    override fun search(query: String, localizedTitleProvider: (ToolDescriptor) -> String): List<ToolDescriptor> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) {
            return orderedTools
        }
        return orderedTools.filter { tool ->
            tool.id.lowercase().contains(normalizedQuery) ||
                localizedTitleProvider(tool).lowercase().contains(normalizedQuery) ||
                tool.searchKeywords.any { keyword -> keyword.lowercase().contains(normalizedQuery) }
        }
    }
}
