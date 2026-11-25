package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAuthRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSchoolOption
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.AccountBindResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingAccountsUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingLoginTab
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ImageBytesHolder
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 账号绑定页一次性导航与交互事件。
 */
sealed interface ChaoxingAccountsUiEvent {
    data class FirstAccountBound(val accountId: String) : ChaoxingAccountsUiEvent
}

@HiltViewModel
@Suppress("TooManyFunctions")
class ChaoxingAccountsViewModel @Inject constructor(
    private val accountManager: ChaoxingAccountManager,
    private val authRemoteDataSource: ChaoxingAuthRemoteDataSource,
    private val licenseCoordinator: LicenseCoordinator,
    private val barcodeScanner: BarcodeScanner,
) : ViewModel() {
    constructor(
        accountManager: ChaoxingAccountManager,
        authRemoteDataSource: ChaoxingAuthRemoteDataSource,
        licenseCoordinator: LicenseCoordinator,
    ) : this(
        accountManager = accountManager,
        authRemoteDataSource = authRemoteDataSource,
        licenseCoordinator = licenseCoordinator,
        barcodeScanner = EmptyBarcodeScanner,
    )

    private val mutableUiState = MutableStateFlow(ChaoxingAccountsUiState())
    val uiState: StateFlow<ChaoxingAccountsUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<ChaoxingAccountsUiEvent>(Channel.BUFFERED)
    val events: Flow<ChaoxingAccountsUiEvent> = eventChannel.receiveAsFlow()

    private var qrPollingJob: Job? = null
    private var lastConsumedInitialPayload: String? = null

    init {
        viewModelScope.launch {
            combine(
                licenseCoordinator.state,
                accountManager.observeAccounts(),
                accountManager.hideDeviceCodeBadge,
            ) { licenseState, accounts, hideBadge ->
                val maxSubs = licenseCoordinator.maxSubAccounts()
                val currentSubs = accounts.count { !it.isPrimary }
                val hasPrimary = accounts.any { it.isPrimary }
                val canBind = licenseCoordinator.canAccess(CHAOXING_TOOL_ID, LicenseTier.STANDARD) &&
                    (!hasPrimary || licenseCoordinator.canBindAdditionalSubAccount(currentSubs))
                AccountsReactiveSnapshot(
                    licenseState = licenseState,
                    accounts = accounts,
                    maxSubAccounts = maxSubs,
                    currentSubAccounts = currentSubs,
                    canBindMore = canBind,
                    hideDeviceCodeBadge = hideBadge,
                )
            }.collect { snapshot ->
                mutableUiState.update { current ->
                    current.copy(
                        licenseState = snapshot.licenseState,
                        accounts = snapshot.accounts,
                        maxSubAccounts = snapshot.maxSubAccounts,
                        currentSubAccounts = snapshot.currentSubAccounts,
                        canBindMore = snapshot.canBindMore,
                        hideDeviceCodeBadge = snapshot.hideDeviceCodeBadge,
                    )
                }
            }
        }
    }

    fun consumeInitialSharePayload(rawPayload: String) {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty() || trimmed == lastConsumedInitialPayload) {
            return
        }
        lastConsumedInitialPayload = trimmed
        qrPollingJob?.cancel()
        mutableUiState.update {
            it.copy(
                activeTab = ChaoxingLoginTab.IMPORT_SHARED,
                importShareInput = trimmed,
                feedbackMessage = null,
            )
        }
        submitSharedImport(trimmed)
    }

    fun selectTab(tab: ChaoxingLoginTab) {
        mutableUiState.update { it.copy(activeTab = tab, feedbackMessage = null) }
        if (tab == ChaoxingLoginTab.QR_CODE && uiState.value.qrSession == null) {
            refreshQrSession()
        } else if (tab != ChaoxingLoginTab.QR_CODE) {
            qrPollingJob?.cancel()
        }
    }

    fun updateAccountInput(value: String) {
        mutableUiState.update { it.copy(accountInput = value, feedbackMessage = null) }
    }

    fun updatePasswordInput(value: String) {
        mutableUiState.update { it.copy(passwordInput = value, feedbackMessage = null) }
    }

    fun updateImportShareInput(value: String) {
        mutableUiState.update { it.copy(importShareInput = value, feedbackMessage = null) }
    }

    fun setImportScannerOpen(open: Boolean) {
        mutableUiState.update { it.copy(isImportScannerOpen = open) }
    }

    fun onImportBarcodeScanned(rawValue: String) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return
        }
        mutableUiState.update {
            it.copy(isImportScannerOpen = false, importShareInput = trimmed, feedbackMessage = null)
        }
        submitSharedImport(trimmed)
    }

    fun scanImportQrFromUri(uri: Uri) {
        viewModelScope.launch {
            when (val res = barcodeScanner.scanImageUri(uri)) {
                is AppResult.Success -> {
                    val raw = res.data.firstOrNull()?.rawValue?.trim().orEmpty()
                    if (raw.isEmpty()) {
                        mutableUiState.update {
                            it.copy(feedbackMessage = "未能从所选图片中识别出二维码", isErrorFeedback = true)
                        }
                    } else {
                        mutableUiState.update { it.copy(importShareInput = raw, feedbackMessage = null) }
                        submitSharedImport(raw)
                    }
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(feedbackMessage = res.error.message, isErrorFeedback = true)
                }
            }
        }
    }

    fun submitSharedImport(overrideText: String? = null) {
        val current = uiState.value
        val targetText = (overrideText ?: current.importShareInput).trim()
        if (current.isSubmittingLogin || targetText.isEmpty()) {
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isSubmittingLogin = true, feedbackMessage = null, isErrorFeedback = false)
            }
            val result = accountManager.importFromSharedText(
                rawText = targetText,
                validate = current.captchaValidateInput.trim(),
            )
            applyBindResult(result)
            mutableUiState.update { it.copy(isSubmittingLogin = false) }
        }
    }

    fun requestShareAccount(accountId: String) {
        viewModelScope.launch {
            when (val res = accountManager.createShareBundle(accountId)) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(activeShareBundle = res.data, feedbackMessage = null, isErrorFeedback = false)
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(feedbackMessage = res.error.message, isErrorFeedback = true)
                }
            }
        }
    }

    fun dismissShareDialog() {
        mutableUiState.update { it.copy(activeShareBundle = null) }
    }

    fun notifyShareLinkCopied() {
        mutableUiState.update {
            it.copy(
                activeShareBundle = null,
                feedbackMessage = "已复制导入链接到剪贴板",
                isErrorFeedback = false,
            )
        }
    }

    fun setDeviceInfoDialogOpen(open: Boolean) {
        mutableUiState.update { it.copy(isDeviceInfoDialogOpen = open) }
    }

    fun setHideDeviceCodeBadge(hide: Boolean) {
        viewModelScope.launch {
            accountManager.setHideDeviceCodeBadge(hide)
        }
    }

    fun repairAccountSession(accountId: String) {
        if (uiState.value.repairingAccountId != null) {
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(repairingAccountId = accountId, feedbackMessage = null) }
            val recovered = accountManager.repairSessionIfPossible(accountId)
            mutableUiState.update {
                it.copy(
                    repairingAccountId = null,
                    feedbackMessage = if (recovered) {
                        "已自动恢复登录会话"
                    } else {
                        "自动重连未成功，请重新输入密码或扫码登录"
                    },
                    isErrorFeedback = !recovered,
                )
            }
        }
    }

    fun updateSchoolKeyword(keyword: String) {
        mutableUiState.update { it.copy(schoolKeyword = keyword) }
        if (keyword.trim().length < MIN_SCHOOL_QUERY_LEN) {
            mutableUiState.update { it.copy(schoolSuggestions = emptyList()) }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearchingSchools = true) }
            when (val res = authRemoteDataSource.searchSchools(keyword)) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(schoolSuggestions = res.data, isSearchingSchools = false)
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(isSearchingSchools = false)
                }
            }
        }
    }

    fun selectSchool(school: ChaoxingSchoolOption?) {
        mutableUiState.update {
            it.copy(
                selectedSchool = school,
                schoolKeyword = school?.name.orEmpty(),
                schoolSuggestions = emptyList(),
            )
        }
    }

    fun updateCaptchaValidate(value: String) {
        mutableUiState.update { it.copy(captchaValidateInput = value) }
    }

    fun submitPasswordLogin() {
        val current = uiState.value
        if (current.isSubmittingLogin) {
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isSubmittingLogin = true, feedbackMessage = null, isErrorFeedback = false)
            }
            val fid = current.selectedSchool?.fid ?: ChaoxingEndpointConfig.DEFAULT_FID
            val result = accountManager.bindWithPassword(
                accountInput = current.accountInput,
                plainPassword = current.passwordInput,
                fid = fid,
                validate = current.captchaValidateInput.trim(),
            )
            applyBindResult(result)
            mutableUiState.update { it.copy(isSubmittingLogin = false) }
        }
    }

    fun refreshQrSession() {
        qrPollingJob?.cancel()
        qrPollingJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(qrImage = null, qrStatusText = "正在加载二维码…", feedbackMessage = null)
            }
            val session = when (val res = accountManager.startQrLogin()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    mutableUiState.update {
                        it.copy(qrStatusText = res.error.message, isErrorFeedback = true)
                    }
                    return@launch
                }
            }
            val imageBytes = when (
                val imgRes = authRemoteDataSource.fetchQrImageBytes(session.provisionalAccountId, session.qrImageUrl)
            ) {
                is AppResult.Success -> ImageBytesHolder(imgRes.data, "qr.jpg")
                is AppResult.Failure -> null
            }
            mutableUiState.update {
                it.copy(
                    qrSession = session,
                    qrImage = imageBytes,
                    qrStatusText = "请使用学习通客户端扫码并确认登录",
                )
            }
            while (isActive) {
                delay(QR_POLL_INTERVAL_MS)
                val pollResult = accountManager.pollQrLogin(session)
                if (pollResult != null) {
                    applyBindResult(pollResult)
                    break
                }
            }
        }
    }

    fun toggleAccountEnabled(accountId: String, enabled: Boolean) {
        viewModelScope.launch {
            accountManager.setAccountEnabled(accountId, enabled)
        }
    }

    fun promoteToPrimary(accountId: String) {
        viewModelScope.launch {
            accountManager.promoteToPrimary(accountId)
        }
    }

    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            accountManager.removeAccount(accountId)
        }
    }

    private fun applyBindResult(result: AccountBindResult) {
        when (result) {
            is AccountBindResult.Bound -> {
                val feedback = when {
                    result.updatedDeviceBindingOnly ->
                        "已为账号 ${result.account.maskedAccount} 同步绑定统一设备码"

                    result.account.isUnifiedDeviceCode ->
                        "已绑定账号 ${result.account.maskedAccount}（已同步统一设备码）"

                    else -> "已绑定账号 ${result.account.maskedAccount}"
                }
                mutableUiState.update {
                    it.copy(
                        accountInput = "",
                        passwordInput = "",
                        importShareInput = "",
                        captchaPromptMessage = null,
                        captchaValidateInput = "",
                        qrSession = null,
                        qrImage = null,
                        qrStatusText = "扫码登录成功",
                        feedbackMessage = feedback,
                        isErrorFeedback = false,
                    )
                }
                if (result.isFirstAccount) {
                    eventChannel.trySend(ChaoxingAccountsUiEvent.FirstAccountBound(result.account.accountId))
                }
            }

            is AccountBindResult.RequiresCaptcha -> mutableUiState.update {
                it.copy(
                    captchaPromptMessage = result.message,
                    feedbackMessage = result.message,
                    isErrorFeedback = true,
                )
            }

            is AccountBindResult.RequiresTwoFactor -> mutableUiState.update {
                it.copy(
                    feedbackMessage = "该账号开启了双因子验证，请先使用扫码登录或前往验证：${result.twoFactorUrl}",
                    isErrorFeedback = true,
                )
            }

            is AccountBindResult.WeakPasswordRequired -> mutableUiState.update {
                it.copy(
                    feedbackMessage = "该账号需先在超星官方页面修改弱密码后再登录",
                    isErrorFeedback = true,
                )
            }

            is AccountBindResult.InvalidCredentials -> mutableUiState.update {
                it.copy(feedbackMessage = result.message, isErrorFeedback = true)
            }

            is AccountBindResult.QuotaExceeded -> mutableUiState.update {
                it.copy(
                    feedbackMessage = "子账号绑定数量已达当前授权上限（${result.currentSubAccounts}/${result.maxSubAccounts}）",
                    isErrorFeedback = true,
                )
            }

            is AccountBindResult.LicenseDenied -> mutableUiState.update {
                it.copy(feedbackMessage = result.message, isErrorFeedback = true)
            }

            is AccountBindResult.Failed -> mutableUiState.update {
                it.copy(feedbackMessage = result.error.message, isErrorFeedback = true)
            }
        }
    }

    override fun onCleared() {
        qrPollingJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MIN_SCHOOL_QUERY_LEN = 2
        private const val QR_POLL_INTERVAL_MS = 3_000L
    }
}

private data class AccountsReactiveSnapshot(
    val licenseState: io.github.onprs.universitytoolbox.core.license.api.LicenseState,
    val accounts: List<io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity>,
    val maxSubAccounts: Int,
    val currentSubAccounts: Int,
    val canBindMore: Boolean,
    val hideDeviceCodeBadge: Boolean,
)

private object EmptyBarcodeScanner : BarcodeScanner {
    override suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> = AppResult.Success(emptyList())

    override suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> = AppResult.Success(emptyList())
}
