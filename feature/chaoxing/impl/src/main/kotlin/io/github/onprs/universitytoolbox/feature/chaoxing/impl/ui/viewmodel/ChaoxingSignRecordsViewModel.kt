package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingSignRecordDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingSignRecordsUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChaoxingSignRecordsViewModel.Factory::class)
class ChaoxingSignRecordsViewModel @AssistedInject constructor(
    @Assisted private val route: ChaoxingNavKey.SignRecords,
    private val signRecordDao: ChaoxingSignRecordDao,
) : ViewModel() {
    val uiState: StateFlow<ChaoxingSignRecordsUiState> = run {
        val targetAccountId = route.accountId
        if (targetAccountId != null) {
            signRecordDao.observeByAccount(targetAccountId)
        } else {
            signRecordDao.observeRecent()
        }
    }
        .map { records ->
            ChaoxingSignRecordsUiState(records = records, filterAccountId = route.accountId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ChaoxingSignRecordsUiState(filterAccountId = route.accountId),
        )

    fun clearAllRecords() {
        viewModelScope.launch {
            signRecordDao.clearAll()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(route: ChaoxingNavKey.SignRecords): ChaoxingSignRecordsViewModel
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
