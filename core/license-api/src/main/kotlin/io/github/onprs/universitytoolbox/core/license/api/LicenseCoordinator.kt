package io.github.onprs.universitytoolbox.core.license.api

import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * 工具或功能访问决策的细粒度原因说明。
 */
sealed interface AccessDenialReason {
    data object EngineInitializing : AccessDenialReason

    data class LicenseLocked(val reason: LockReason) : AccessDenialReason

    data class InsufficientTier(val current: LicenseTier, val required: LicenseTier) : AccessDenialReason

    data class MissingScope(val requiredScope: String) : AccessDenialReason

    data object Expired : AccessDenialReason

    data object RemotelyDisabled : AccessDenialReason
}

/**
 * 单次授权门禁评估结果。
 */
sealed interface AccessDecision {
    data class Granted(val entitlement: LicenseEntitlement) : AccessDecision

    data class Denied(val reason: AccessDenialReason) : AccessDecision
}

/**
 * 业务门禁协调器：消费 [LicenseEngine] 状态，为宿主导航守卫、首页工具卡片及业务配额提供统一鉴权逻辑。
 */
@Singleton
class LicenseCoordinator(private val licenseEngine: LicenseEngine, private val timeProvider: TimeProvider) {
    @Inject
    constructor(licenseEngine: LicenseEngine) : this(
        licenseEngine = licenseEngine,
        timeProvider = TimeProvider.System,
    )

    val state: StateFlow<LicenseState>
        get() = licenseEngine.state

    val isUnlocked: Flow<Boolean> = state.map { current ->
        current is LicenseState.Unlocked && !current.entitlement.isExpiredAt(timeProvider.currentEpochSeconds())
    }

    val currentEntitlement: Flow<LicenseEntitlement?> = state.map { current ->
        (current as? LicenseState.Unlocked)
            ?.entitlement
            ?.takeUnless { it.isExpiredAt(timeProvider.currentEpochSeconds()) }
    }

    /** 返回当前有效授权凭证；未解锁或已过期时返回 null。 */
    fun currentEntitlementOrNull(): LicenseEntitlement? {
        val current = state.value as? LicenseState.Unlocked ?: return null
        return current.entitlement.takeUnless { it.isExpiredAt(timeProvider.currentEpochSeconds()) }
    }

    /** 若当前处于锁定状态则返回锁定原因，否则返回 null。 */
    fun lockReasonOrNull(): LockReason? = (state.value as? LicenseState.Locked)?.reason

    /** 判断当前授权是否允许访问目标工具或能力。 */
    fun canAccess(tool: LicenseProtectedTarget, isRemoteDisabled: Boolean = false): Boolean =
        evaluateAccess(tool, isRemoteDisabled) is AccessDecision.Granted

    /** 按显式工具 ID、所需等级与作用域评估访问权限。 */
    fun canAccess(
        toolId: String,
        requiredTier: LicenseTier,
        scopeId: String = toolId,
        isRemoteDisabled: Boolean = false,
    ): Boolean = evaluateAccess(
        target = SimpleProtectedTarget(id = toolId, requiredTier = requiredTier, scopeId = scopeId),
        isRemoteDisabled = isRemoteDisabled,
    ) is AccessDecision.Granted

    /** 评估目标工具的访问权限并返回可解释的放行或拒绝原因。 */
    fun evaluateAccess(target: LicenseProtectedTarget, isRemoteDisabled: Boolean = false): AccessDecision {
        if (isRemoteDisabled) {
            return AccessDecision.Denied(AccessDenialReason.RemotelyDisabled)
        }
        return when (val current = state.value) {
            LicenseState.Initializing -> AccessDecision.Denied(AccessDenialReason.EngineInitializing)
            is LicenseState.Locked -> AccessDecision.Denied(AccessDenialReason.LicenseLocked(current.reason))
            is LicenseState.Unlocked -> evaluateEntitlement(current.entitlement, target)
        }
    }

    /** 查询当前凭证允许绑定的最大子账号配额；未解锁或已过期时返回 0。 */
    fun maxSubAccounts(): Int = currentEntitlementOrNull()?.maxSubAccounts?.coerceAtLeast(0) ?: 0

    /** 判断在当前已绑定数量基础上是否仍可新增一个子账号。 */
    fun canBindAdditionalSubAccount(currentBoundCount: Int): Boolean {
        if (currentBoundCount < 0) {
            return false
        }
        return currentBoundCount < maxSubAccounts()
    }

    private fun evaluateEntitlement(entitlement: LicenseEntitlement, target: LicenseProtectedTarget): AccessDecision {
        val denial: AccessDenialReason? = when {
            entitlement.isExpiredAt(timeProvider.currentEpochSeconds()) -> AccessDenialReason.Expired
            !entitlement.tier.satisfies(target.requiredTier) -> AccessDenialReason.InsufficientTier(
                current = entitlement.tier,
                required = target.requiredTier,
            )
            !entitlement.hasScope(target.scopeId) -> AccessDenialReason.MissingScope(target.scopeId)
            else -> null
        }
        return if (denial == null) {
            AccessDecision.Granted(entitlement)
        } else {
            AccessDecision.Denied(denial)
        }
    }

    private data class SimpleProtectedTarget(
        override val id: String,
        override val requiredTier: LicenseTier,
        override val scopeId: String,
    ) : LicenseProtectedTarget
}
