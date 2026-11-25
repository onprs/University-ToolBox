package io.github.onprs.universitytoolbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.datastore.UserPreferences
import io.github.onprs.universitytoolbox.core.datastore.UserPreferencesRepository
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.tool.api.ToolRegistry
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.feature.home.shortcut.ToolShortcutPublisher
import io.github.onprs.universitytoolbox.navigation.AppNavigationGate
import io.github.onprs.universitytoolbox.navigation.GateEffect
import io.github.onprs.universitytoolbox.navigation.GateUiPhase
import io.github.onprs.universitytoolbox.navigation.GateUpdate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 宿主全局状态模型，聚合主题偏好与授权门禁导航状态。
 */
data class MainUiState(
    val userPreferences: UserPreferences = UserPreferences(),
    val gatePhase: GateUiPhase = GateUiPhase.Initializing,
)

/**
 * 宿主全局控制器：驱动响应式主题切换、授权门禁路由状态机与深链安全调度。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    licenseCoordinator: LicenseCoordinator,
    toolRegistry: ToolRegistry,
    remoteGovernanceRepository: RemoteGovernanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val metadataRepository: ToolboxMetadataRepository,
    private val shortcutPublisher: ToolShortcutPublisher,
) : ViewModel() {
    private val navigationGate = AppNavigationGate(
        licenseCoordinator = licenseCoordinator,
        toolRegistry = toolRegistry,
    )

    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    private val noticeChannel = Channel<GateEffect>(capacity = Channel.BUFFERED)
    val notices: Flow<GateEffect> = noticeChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                mutableUiState.update { it.copy(userPreferences = prefs) }
            }
        }
        viewModelScope.launch {
            combine(
                licenseCoordinator.state,
                remoteGovernanceRepository.disabledTools,
            ) { licenseState, disabledTools ->
                licenseState to disabledTools
            }.collect { (licenseState, disabledTools) ->
                applyGateUpdate(navigationGate.syncState(licenseState, disabledTools))
            }
        }
    }

    fun onIncomingUri(rawUri: String?) {
        if (rawUri.isNullOrBlank()) {
            return
        }
        applyGateUpdate(navigationGate.handleDeepLink(rawUri))
    }

    fun openTool(entryKey: NavKey) {
        applyGateUpdate(navigationGate.openToolByEntryKey(entryKey))
    }

    fun openSettings() {
        applyGateUpdate(navigationGate.openSettings())
    }

    fun openUnlock(initialActivationCode: String? = null, manageMode: Boolean = true) {
        applyGateUpdate(
            navigationGate.openUnlock(
                initialActivationCode = initialActivationCode,
                manageMode = manageMode,
            ),
        )
    }

    fun popBack() {
        applyGateUpdate(navigationGate.popBack())
    }

    private fun applyGateUpdate(update: GateUpdate) {
        mutableUiState.update { it.copy(gatePhase = update.phase) }
        update.effects.forEach { effect ->
            when (effect) {
                is GateEffect.RecordToolOpened -> {
                    shortcutPublisher.reportToolUsed(effect.toolId)
                    viewModelScope.launch {
                        metadataRepository.recordToolUsage(effect.toolId)
                    }
                }
                is GateEffect.ShowToolDisabledNotice,
                is GateEffect.ShowToolLockedNotice,
                is GateEffect.ShowToolNotFoundNotice,
                -> {
                    noticeChannel.trySend(effect)
                }
            }
        }
    }
}
