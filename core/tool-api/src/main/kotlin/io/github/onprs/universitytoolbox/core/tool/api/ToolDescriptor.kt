package io.github.onprs.universitytoolbox.core.tool.api

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import io.github.onprs.universitytoolbox.core.license.api.LicenseProtectedTarget
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier

/**
 * 工具注册描述符契约。
 * 各业务模块通过 Hilt `@IntoSet` 提供自己的 [ToolDescriptor]，实现首页与搜索的零侵入发现。
 */
data class ToolDescriptor(
    override val id: String,
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:StringRes @get:StringRes val descriptionRes: Int,
    val category: ToolCategory,
    override val requiredTier: LicenseTier,
    override val scopeId: String = id,
    val entryKey: NavKey,
    val searchKeywords: List<String> = emptyList(),
    val iconKey: ToolIconKey = ToolIconKey.UTILITY,
    val isPinnedByDefault: Boolean = false,
    val sortOrder: Int = 0,
) : LicenseProtectedTarget
