package io.github.onprs.universitytoolbox.navigation

import androidx.navigation3.runtime.NavKey
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.AccessDecision
import io.github.onprs.universitytoolbox.core.license.api.AccessDenialReason
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.core.tool.api.ToolRegistry
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.home.navigation.HomeNavKey
import io.github.onprs.universitytoolbox.feature.settings.navigation.SettingsNavKey
import io.github.onprs.universitytoolbox.feature.unlock.navigation.UnlockNavKey
import javax.inject.Inject

/**
 * 宿主导航守卫所处的全局阶段及安全路由栈快照。
 */
sealed interface GateUiPhase {
    data object Initializing : GateUiPhase

    data class Locked(
        val reason: LockReason,
        val deviceChallenge: String,
        val initialActivationCode: String?,
        val blockedToolId: String?,
    ) : GateUiPhase

    data class Unlocked(val backStack: List<NavKey>) : GateUiPhase
}

/**
 * 门禁流转过程中产生的瞬态副作用事件。
 */
sealed interface GateEffect {
    data class RecordToolOpened(val toolId: String) : GateEffect

    data class ShowToolLockedNotice(val toolId: String, val reason: AccessDenialReason) : GateEffect

    data class ShowToolDisabledNotice(val toolId: String, val message: String?) : GateEffect

    data class ShowToolNotFoundNotice(val toolId: String) : GateEffect
}

/**
 * 单次门禁状态机输出：包含最新导航阶段与待消费副作用列表。
 */
data class GateUpdate(val phase: GateUiPhase, val effects: List<GateEffect> = emptyList())

/**
 * 宿主响应式路由守卫状态机。
 * 保证在 [LicenseState.Initializing] 与 [LicenseState.Locked] 态绝不越权打开内部工具，
 * 并在 [LicenseState.Unlocked] 态统一执行 Tier、Scope、远程熔断开关校验及连点防重。
 */
class AppNavigationGate(
    private val licenseCoordinator: LicenseCoordinator,
    private val toolRegistry: ToolRegistry,
    private val timeProvider: TimeProvider,
    private val navigationDebounceMs: Long = DEFAULT_NAVIGATION_DEBOUNCE_MS,
) {
    @Inject
    constructor(
        licenseCoordinator: LicenseCoordinator,
        toolRegistry: ToolRegistry,
    ) : this(
        licenseCoordinator = licenseCoordinator,
        toolRegistry = toolRegistry,
        timeProvider = TimeProvider.System,
        navigationDebounceMs = DEFAULT_NAVIGATION_DEBOUNCE_MS,
    )

    private var lastLicenseState: LicenseState = LicenseState.Initializing
    private var disabledTools: Map<String, String?> = emptyMap()
    private var pendingActivationCode: String? = null
    private var pendingToolId: String? = null
    private var pendingCustomEntryKey: NavKey? = null
    private var pendingUpgradeToolId: String? = null
    private var pendingUpgradeEntryKey: NavKey? = null
    private val unlockedBackStack: MutableList<NavKey> = mutableListOf()
    private val pendingEffects: MutableList<GateEffect> = mutableListOf()
    private var lastNavigationToken: Any? = null
    private var lastNavigationAtMs: Long = Long.MIN_VALUE

    fun syncState(licenseState: LicenseState, disabledTools: Map<String, String?>): GateUpdate {
        lastLicenseState = licenseState
        this.disabledTools = disabledTools
        resetRapidNavigationGuard()

        when (licenseState) {
            LicenseState.Initializing -> Unit
            is LicenseState.Locked -> {
                unlockedBackStack.clear()
                pendingUpgradeToolId = null
                pendingUpgradeEntryKey = null
            }

            is LicenseState.Unlocked -> syncUnlockedStateLocked()
        }
        return buildUpdate()
    }

    fun handleDeepLink(rawUri: String?): GateUpdate {
        val trimmedUri = rawUri?.trim()?.takeIf { it.isNotEmpty() } ?: return buildUpdate()
        if (isDuplicateRapidNavigation("deeplink:$trimmedUri")) {
            return buildUpdate()
        }
        val target = DeepLinkParser.parse(trimmedUri) ?: return buildUpdate()
        when (target) {
            is DeepLinkTarget.Activation -> handleActivationDeepLink(target.activationCode)
            is DeepLinkTarget.Tool -> handleToolDeepLink(target.toolId, target.customEntryKey)
        }
        return buildUpdate()
    }

    fun openToolByEntryKey(entryKey: NavKey): GateUpdate {
        if (lastLicenseState !is LicenseState.Unlocked || isDuplicateRapidNavigation(entryKey)) {
            return buildUpdate()
        }
        val descriptor = toolRegistry.allTools().firstOrNull { it.entryKey == entryKey }
        if (descriptor == null) {
            pendingEffects.add(GateEffect.ShowToolNotFoundNotice(entryKey.toString()))
            return buildUpdate()
        }
        evaluateAndNavigateToTool(descriptor)
        return buildUpdate()
    }

    fun openSettings(): GateUpdate {
        if (lastLicenseState !is LicenseState.Unlocked || isDuplicateRapidNavigation(SettingsNavKey)) {
            return buildUpdate()
        }
        if (unlockedBackStack.lastOrNull() != SettingsNavKey) {
            unlockedBackStack.removeAll { it == SettingsNavKey }
            unlockedBackStack.add(SettingsNavKey)
        }
        return buildUpdate()
    }

    fun openUnlock(initialActivationCode: String?, manageMode: Boolean): GateUpdate {
        val targetKey = UnlockNavKey(
            initialActivationCode = initialActivationCode,
            manageMode = manageMode,
        )
        if (lastLicenseState !is LicenseState.Unlocked) {
            if (!initialActivationCode.isNullOrBlank()) {
                pendingActivationCode = initialActivationCode
            }
            return buildUpdate()
        }
        if (isDuplicateRapidNavigation(targetKey)) {
            return buildUpdate()
        }
        pushOrReplaceTopUnlock(targetKey)
        return buildUpdate()
    }

    fun popBack(): GateUpdate {
        resetRapidNavigationGuard()
        if (lastLicenseState is LicenseState.Unlocked && unlockedBackStack.size > 1) {
            val removed = unlockedBackStack.removeAt(unlockedBackStack.lastIndex)
            if (removed is UnlockNavKey) {
                pendingUpgradeToolId = null
                pendingUpgradeEntryKey = null
            }
        }
        return buildUpdate()
    }

    fun canPopBack(): Boolean = lastLicenseState is LicenseState.Unlocked && unlockedBackStack.size > 1

    private fun isDuplicateRapidNavigation(token: Any): Boolean {
        if (navigationDebounceMs <= 0L) {
            return false
        }
        val nowMs = timeProvider.currentTimeMillis()
        val isRapidRepeat = token == lastNavigationToken &&
            lastNavigationAtMs != Long.MIN_VALUE &&
            nowMs - lastNavigationAtMs in 0 until navigationDebounceMs
        if (!isRapidRepeat) {
            lastNavigationToken = token
            lastNavigationAtMs = nowMs
        }
        return isRapidRepeat
    }

    private fun resetRapidNavigationGuard() {
        lastNavigationToken = null
        lastNavigationAtMs = Long.MIN_VALUE
    }

    private fun syncUnlockedStateLocked() {
        pendingActivationCode = null
        if (unlockedBackStack.isEmpty()) {
            unlockedBackStack.add(HomeNavKey)
            val queuedToolId = pendingToolId
            val queuedEntryKey = pendingCustomEntryKey
            pendingToolId = null
            pendingCustomEntryKey = null
            if (queuedToolId != null) {
                openToolByIdLocked(queuedToolId, queuedEntryKey)
            }
            return
        }

        val upgradeToolId = pendingUpgradeToolId
        if (upgradeToolId != null && unlockedBackStack.lastOrNull() is UnlockNavKey) {
            val descriptor = toolRegistry.findById(upgradeToolId)
            if (descriptor != null &&
                licenseCoordinator.evaluateAccess(descriptor, descriptor.id in disabledTools) is AccessDecision.Granted
            ) {
                val targetKey = pendingUpgradeEntryKey ?: descriptor.entryKey
                pendingUpgradeToolId = null
                pendingUpgradeEntryKey = null
                unlockedBackStack.removeAt(unlockedBackStack.lastIndex)
                unlockedBackStack.removeAll { it == targetKey }
                unlockedBackStack.add(targetKey)
                pendingEffects.add(GateEffect.RecordToolOpened(descriptor.id))
            }
        }

        enforceCurrentBackStackAccess()
    }

    private fun enforceCurrentBackStackAccess() {
        val activeToolEntryIndex = unlockedBackStack.indexOfFirst { key ->
            key != HomeNavKey && key != SettingsNavKey && key !is UnlockNavKey
        }
        if (activeToolEntryIndex < 0) {
            return
        }
        val activeKey = unlockedBackStack[activeToolEntryIndex]
        val descriptor = toolRegistry.allTools().firstOrNull { candidate ->
            candidate.entryKey == activeKey || (activeKey is ChaoxingNavKey && candidate.id == CHAOXING_TOOL_ID)
        } ?: return
        val decision = licenseCoordinator.evaluateAccess(
            target = descriptor,
            isRemoteDisabled = descriptor.id in disabledTools,
        )
        if (decision is AccessDecision.Denied) {
            while (unlockedBackStack.size > activeToolEntryIndex) {
                unlockedBackStack.removeAt(unlockedBackStack.lastIndex)
            }
            handleDeniedTool(descriptor.id, decision.reason, customEntryKey = activeKey)
        }
    }

    private fun handleActivationDeepLink(activationCode: String?) {
        if (lastLicenseState is LicenseState.Unlocked) {
            pushOrReplaceTopUnlock(
                UnlockNavKey(
                    initialActivationCode = activationCode,
                    manageMode = true,
                ),
            )
        } else {
            pendingActivationCode = activationCode
        }
    }

    private fun handleToolDeepLink(toolId: String, customEntryKey: NavKey?) {
        if (lastLicenseState is LicenseState.Unlocked) {
            openToolByIdLocked(toolId, customEntryKey)
        } else {
            pendingToolId = toolId
            pendingCustomEntryKey = customEntryKey
        }
    }

    private fun openToolByIdLocked(toolId: String, customEntryKey: NavKey?) {
        val descriptor = toolRegistry.findById(toolId)
        if (descriptor == null) {
            pendingEffects.add(GateEffect.ShowToolNotFoundNotice(toolId))
            return
        }
        evaluateAndNavigateToTool(descriptor, customEntryKey)
    }

    private fun evaluateAndNavigateToTool(descriptor: ToolDescriptor, customEntryKey: NavKey? = null) {
        val isRemoteDisabled = descriptor.id in disabledTools
        when (val decision = licenseCoordinator.evaluateAccess(descriptor, isRemoteDisabled = isRemoteDisabled)) {
            is AccessDecision.Granted -> {
                pendingUpgradeToolId = null
                pendingUpgradeEntryKey = null
                val targetKey = customEntryKey ?: descriptor.entryKey
                val currentTop = unlockedBackStack.lastOrNull()
                var didNavigate = false
                if (currentTop is ChaoxingNavKey.Accounts && targetKey is ChaoxingNavKey.Accounts) {
                    if (currentTop != targetKey) {
                        unlockedBackStack[unlockedBackStack.lastIndex] = targetKey
                        didNavigate = true
                    }
                } else if (currentTop != targetKey) {
                    unlockedBackStack.removeAll { it == targetKey }
                    unlockedBackStack.add(targetKey)
                    didNavigate = true
                }
                if (didNavigate) {
                    pendingEffects.add(GateEffect.RecordToolOpened(descriptor.id))
                }
            }

            is AccessDecision.Denied -> handleDeniedTool(descriptor.id, decision.reason, customEntryKey)
        }
    }

    private fun handleDeniedTool(toolId: String, reason: AccessDenialReason, customEntryKey: NavKey? = null) {
        when (reason) {
            AccessDenialReason.RemotelyDisabled -> {
                pendingEffects.add(GateEffect.ShowToolDisabledNotice(toolId, disabledTools[toolId]))
            }

            AccessDenialReason.EngineInitializing -> Unit
            AccessDenialReason.Expired,
            is AccessDenialReason.InsufficientTier,
            is AccessDenialReason.LicenseLocked,
            is AccessDenialReason.MissingScope,
            -> {
                pendingUpgradeToolId = toolId
                pendingUpgradeEntryKey = customEntryKey
                pushOrReplaceTopUnlock(UnlockNavKey(manageMode = true))
                pendingEffects.add(GateEffect.ShowToolLockedNotice(toolId, reason))
            }
        }
    }

    private fun pushOrReplaceTopUnlock(targetKey: UnlockNavKey) {
        if (unlockedBackStack.lastOrNull() is UnlockNavKey) {
            unlockedBackStack[unlockedBackStack.lastIndex] = targetKey
        } else {
            unlockedBackStack.removeAll { it is UnlockNavKey }
            unlockedBackStack.add(targetKey)
        }
    }

    private fun buildUpdate(): GateUpdate {
        val phase = when (val state = lastLicenseState) {
            LicenseState.Initializing -> GateUiPhase.Initializing
            is LicenseState.Locked -> GateUiPhase.Locked(
                reason = state.reason,
                deviceChallenge = state.deviceChallenge,
                initialActivationCode = pendingActivationCode,
                blockedToolId = pendingToolId,
            )

            is LicenseState.Unlocked -> GateUiPhase.Unlocked(
                backStack = unlockedBackStack.toList(),
            )
        }
        val drainedEffects = pendingEffects.toList()
        pendingEffects.clear()
        return GateUpdate(phase = phase, effects = drainedEffects)
    }

    companion object {
        const val DEFAULT_NAVIGATION_DEBOUNCE_MS = 350L
    }
}
