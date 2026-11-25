package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.license.api.AccessDecision
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCourseEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingDashboardUiState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChaoxingDashboardViewModel @Inject constructor(
    private val accountManager: ChaoxingAccountManager,
    private val licenseCoordinator: LicenseCoordinator,
    private val remoteGovernanceRepository: RemoteGovernanceRepository,
) : ViewModel() {
    private val selectedAccountId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)
    private val isLoadingCourses = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val autoSyncedAccountIds = LinkedHashSet<String>()

    private val accountsFlow = accountManager.observeAccounts()

    init {
        observeInitialCourseSync()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coursesFlow: StateFlow<List<ChaoxingCourseEntity>> =
        combine(accountsFlow, selectedAccountId) { accounts, explicitId ->
            accounts.firstOrNull { it.accountId == explicitId }?.accountId
                ?: accounts.firstOrNull()?.accountId
        }.flatMapLatest { activeId ->
            if (activeId == null) {
                flowOf(emptyList())
            } else {
                accountManager.observeCourses(activeId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val baseInfoFlow = combine(
        licenseCoordinator.state,
        remoteGovernanceRepository.disabledTools,
        accountsFlow,
        selectedAccountId,
        coursesFlow,
    ) { licenseState, disabledMap, accounts, explicitId, courses ->
        val isDisabled = CHAOXING_TOOL_ID in disabledMap
        val accessDecision = licenseCoordinator.evaluateAccess(
            target = SimpleChaoxingTarget,
            isRemoteDisabled = isDisabled,
        )
        val resolvedAccountId = accounts.firstOrNull { it.accountId == explicitId }?.accountId
            ?: accounts.firstOrNull()?.accountId
        ChaoxingDashboardUiState(
            licenseState = licenseState,
            accessDecision = accessDecision,
            isRemoteDisabled = isDisabled,
            disableReason = disabledMap[CHAOXING_TOOL_ID],
            accounts = accounts,
            selectedAccountId = resolvedAccountId,
            courses = courses,
        )
    }

    val uiState: StateFlow<ChaoxingDashboardUiState> = combine(
        baseInfoFlow,
        searchQuery,
        showArchived,
        isLoadingCourses,
        errorMessage,
    ) { base, query, archived, loading, error ->
        base.copy(
            searchQuery = query,
            showArchived = archived,
            isLoadingCourses = loading,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ChaoxingDashboardUiState(
            licenseState = licenseCoordinator.state.value,
            accessDecision = licenseCoordinator.evaluateAccess(
                target = SimpleChaoxingTarget,
                isRemoteDisabled = remoteGovernanceRepository.isToolDisabled(CHAOXING_TOOL_ID),
            ),
        ),
    )

    fun selectAccount(accountId: String) {
        selectedAccountId.value = accountId
        errorMessage.value = null
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleShowArchived(show: Boolean) {
        showArchived.value = show
    }

    fun refreshCourses() {
        val accountId = uiState.value.selectedAccount?.accountId ?: return
        syncCoursesForAccount(accountId)
    }

    private fun observeInitialCourseSync() {
        viewModelScope.launch {
            combine(
                licenseCoordinator.state,
                remoteGovernanceRepository.disabledTools,
                accountsFlow,
                selectedAccountId,
            ) { _, disabledMap, accounts, explicitId ->
                val granted = licenseCoordinator.evaluateAccess(
                    target = SimpleChaoxingTarget,
                    isRemoteDisabled = CHAOXING_TOOL_ID in disabledMap,
                ) is AccessDecision.Granted
                val activeAccount = accounts.firstOrNull { it.accountId == explicitId } ?: accounts.firstOrNull()
                if (granted && activeAccount?.sessionStatus == ChaoxingAccountManager.SESSION_ACTIVE) {
                    activeAccount.accountId
                } else {
                    null
                }
            }.distinctUntilChanged().collect { activeId ->
                if (activeId != null && activeId !in autoSyncedAccountIds) {
                    autoSyncedAccountIds += activeId
                    if (!accountManager.hasCachedCourses(activeId)) {
                        syncCoursesForAccount(activeId)
                    }
                }
            }
        }
    }

    private fun syncCoursesForAccount(accountId: String) {
        if (isLoadingCourses.value) {
            return
        }
        viewModelScope.launch {
            isLoadingCourses.value = true
            errorMessage.value = null
            when (val res = accountManager.refreshCourses(accountId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> errorMessage.value = res.error.message
            }
            isLoadingCourses.value = false
        }
    }

    private object SimpleChaoxingTarget :
        io.github.onprs.universitytoolbox.core.license.api.LicenseProtectedTarget {
        override val id: String = CHAOXING_TOOL_ID
        override val requiredTier: LicenseTier = LicenseTier.STANDARD
        override val scopeId: String = CHAOXING_TOOL_ID
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
