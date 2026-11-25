package io.github.onprs.universitytoolbox.feature.gpa.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** GPA 计算器工具注册唯一标识。 */
const val GPA_TOOL_ID = "gpa_calculator"

/**
 * 学分绩点（GPA）多标准换算与目标绩点反推工具的 Navigation 3 路由键。
 */
@Serializable
data object GpaNavKey : NavKey
