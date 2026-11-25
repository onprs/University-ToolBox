package io.github.onprs.universitytoolbox.feature.unlock.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 授权激活引导与授权管理页的 Navigation 3 路由键。
 */
@Serializable
data class UnlockNavKey(val initialActivationCode: String? = null, val manageMode: Boolean = false) : NavKey
