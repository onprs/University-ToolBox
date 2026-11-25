package io.github.onprs.universitytoolbox.feature.home.domain

import io.github.onprs.universitytoolbox.core.license.api.AccessDecision
import io.github.onprs.universitytoolbox.core.license.api.AccessDenialReason
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor

/**
 * 最近使用工具的轻量纯领域记录。
 */
data class RecentToolRecord(val toolId: String, val lastUsedAtEpochMs: Long)

/**
 * 首页单个工具条目的访问状态。
 */
sealed interface ToolAccessStatus {
    data object Available : ToolAccessStatus

    data class Locked(val reason: AccessDenialReason) : ToolAccessStatus

    data class RemotelyDisabled(val noticeMessage: String?) : ToolAccessStatus
}

/**
 * 首页渲染所需单个工具的完整展示模型。
 */
data class HomeToolItem(
    val descriptor: ToolDescriptor,
    val accessStatus: ToolAccessStatus,
    val isPinned: Boolean,
    val lastUsedAtEpochMs: Long?,
) {
    val isAvailable: Boolean
        get() = accessStatus is ToolAccessStatus.Available
}

/**
 * 首页工具聚合筛选结果快照。
 */
data class HomeCatalogSnapshot(
    val pinnedTools: List<HomeToolItem>,
    val recentTools: List<HomeToolItem>,
    val filteredTools: List<HomeToolItem>,
)

internal fun AccessDecision.toToolAccessStatus(remoteDisabledMessage: String?): ToolAccessStatus = when (this) {
    is AccessDecision.Granted -> ToolAccessStatus.Available
    is AccessDecision.Denied -> {
        if (reason == AccessDenialReason.RemotelyDisabled) {
            ToolAccessStatus.RemotelyDisabled(remoteDisabledMessage)
        } else {
            ToolAccessStatus.Locked(reason)
        }
    }
}
