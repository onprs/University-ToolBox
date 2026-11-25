package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingActivitiesUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingActivityFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChaoxingActivitiesViewModel.Factory::class)
class ChaoxingActivitiesViewModel @AssistedInject constructor(
    @Assisted private val route: ChaoxingNavKey.CourseActivities,
    private val accountDao: ChaoxingAccountDao,
    private val remoteDataSource: ChaoxingRemoteDataSource,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        ChaoxingActivitiesUiState(
            accountId = route.accountId,
            courseId = route.courseId,
            clazzId = route.clazzId,
            cpi = route.cpi,
            courseName = route.courseName,
        ),
    )
    val uiState: StateFlow<ChaoxingActivitiesUiState> = mutableUiState.asStateFlow()

    private var monitorJob: Job? = null
    private var activeLoadJob: Job? = null
    private var isForegroundActive: Boolean = true
    private var wasStoppedWhileActive: Boolean = false

    init {
        refresh()
    }

    fun setFilter(filter: ChaoxingActivityFilter) {
        mutableUiState.update { it.copy(filter = filter) }
    }

    fun toggleAutoMonitor(enabled: Boolean) {
        mutableUiState.update { it.copy(isAutoMonitorEnabled = enabled) }
        if (!enabled) {
            cancelMonitorLoop()
        } else if (isForegroundActive) {
            startMonitorLoop()
        }
    }

    /**
     * 同步宿主页面是否处于前台可见态（`Lifecycle.State.STARTED` 及以上）。
     * 退至后台或进入下级页面时暂停后台轮询，返回前台时自动刷新一次最新状态并恢复轮询。
     */
    fun setForegroundActive(active: Boolean) {
        if (isForegroundActive == active) {
            return
        }
        isForegroundActive = active
        if (!active) {
            wasStoppedWhileActive = true
            cancelMonitorLoop()
        } else {
            if (wasStoppedWhileActive) {
                wasStoppedWhileActive = false
                refresh()
            }
            if (uiState.value.isAutoMonitorEnabled) {
                startMonitorLoop()
            }
        }
    }

    fun refresh() {
        if (activeLoadJob?.isActive == true) {
            return
        }
        activeLoadJob = viewModelScope.launch {
            loadOnce()
        }
    }

    private fun startMonitorLoop() {
        cancelMonitorLoop()
        monitorJob = viewModelScope.launch {
            while (isActive && isForegroundActive) {
                delay(AUTO_MONITOR_INTERVAL_MS)
                if (!isForegroundActive || !uiState.value.isAutoMonitorEnabled) {
                    break
                }
                if (activeLoadJob?.isActive != true) {
                    val job = viewModelScope.launch { loadOnce() }
                    activeLoadJob = job
                    job.join()
                }
            }
        }
    }

    private fun cancelMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun loadOnce() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val fid = accountDao.getById(route.accountId)?.fid ?: "0"
            when (
                val res = remoteDataSource.fetchActivities(
                    accountId = route.accountId,
                    fid = fid,
                    courseId = route.courseId,
                    clazzId = route.clazzId,
                    cpi = route.cpi,
                    showNotStarted = false,
                )
            ) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(
                        activities = res.data,
                        isLoading = false,
                        lastUpdatedAtMs = timeProvider.currentTimeMillis(),
                    )
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = res.error.message,
                    )
                }
            }
        } finally {
            mutableUiState.update { current ->
                if (current.isLoading) current.copy(isLoading = false) else current
            }
        }
    }

    override fun onCleared() {
        cancelMonitorLoop()
        activeLoadJob?.cancel()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(route: ChaoxingNavKey.CourseActivities): ChaoxingActivitiesViewModel
    }

    companion object {
        private const val AUTO_MONITOR_INTERVAL_MS = 10_000L
    }
}
