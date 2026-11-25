package io.github.onprs.universitytoolbox.core.update

import kotlinx.serialization.Serializable

/**
 * 统一远程治理与自更新清单模型，同时承担版本更新校验、工具级熔断开关与吊销序列号下发职责。
 */
@Serializable
data class UpdateManifest(
    val channel: String = DEFAULT_CHANNEL,
    val latestVersionCode: Long,
    val latestVersionName: String,
    val minSupportedVersionCode: Long = 0L,
    val apkUrl: String,
    val sha256: String,
    val changelog: String = "",
    val disabledTools: Map<String, String?> = emptyMap(),
    val revokedSerialIds: List<String> = emptyList(),
) {
    /** 判断远端是否存在更高版本的安装包。 */
    fun isUpdateAvailable(currentVersionCode: Long): Boolean = latestVersionCode > currentVersionCode

    /** 判断当前版本是否低于服务端声明的最低受支持版本。 */
    fun isForceUpdateRequired(currentVersionCode: Long): Boolean = currentVersionCode < minSupportedVersionCode

    companion object {
        const val DEFAULT_CHANNEL = "stable"
    }
}
