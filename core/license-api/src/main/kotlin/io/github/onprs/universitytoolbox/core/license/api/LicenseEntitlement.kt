package io.github.onprs.universitytoolbox.core.license.api

/**
 * 解锁成功后的结构化授权凭证（仅包含脱敏编号与权限/配额字段，不含任何密钥原文或算法参数）。
 */
data class LicenseEntitlement(
    val serialId: String,
    val tier: LicenseTier,
    val grantedScopes: Set<String>,
    val maxSubAccounts: Int,
    val expiresAtEpochSec: Long?,
    val isOfficialEngine: Boolean,
) {
    /** 判断授权范围是否覆盖指定工具或能力标识。 */
    fun hasScope(scopeId: String): Boolean = WILDCARD_SCOPE in grantedScopes || scopeId in grantedScopes

    /** 判断凭证在指定 UTC 秒级时间戳下是否已过期；永久凭证始终返回 false。 */
    fun isExpiredAt(epochSec: Long): Boolean = expiresAtEpochSec != null && epochSec >= expiresAtEpochSec

    companion object {
        const val WILDCARD_SCOPE = "*"
    }
}
