package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrParser
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.MultiAccountSignCoordinator
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionRequest
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingSignExecutionUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ImageBytesHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChaoxingSignExecutionViewModel.Factory::class)
@Suppress("TooManyFunctions")
class ChaoxingSignExecutionViewModel @AssistedInject constructor(
    @Assisted private val route: ChaoxingNavKey.SignExecution,
    private val accountDao: ChaoxingAccountDao,
    private val remoteDataSource: ChaoxingRemoteDataSource,
    private val signCoordinator: MultiAccountSignCoordinator,
    private val licenseCoordinator: LicenseCoordinator,
    private val barcodeScanner: BarcodeScanner,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        ChaoxingSignExecutionUiState(
            accountId = route.accountId,
            courseId = route.courseId,
            clazzId = route.clazzId,
            activeId = route.activeId,
            selectedAccountIds = setOf(route.accountId),
        ),
    )
    val uiState: StateFlow<ChaoxingSignExecutionUiState> = mutableUiState.asStateFlow()

    init {
        loadDetailAndAccounts()
    }

    fun loadDetailAndAccounts() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoadingDetail = true, loadError = null) }
            val accounts = accountDao.getAll().filter { it.isEnabled }
            val maxSub = licenseCoordinator.maxSubAccounts()
            when (val detailRes = remoteDataSource.fetchActiveDetail(route.accountId, route.activeId)) {
                is AppResult.Success -> {
                    val detail = detailRes.data
                    mutableUiState.update { current ->
                        current.copy(
                            detail = detail,
                            isLoadingDetail = false,
                            availableAccounts = accounts,
                            maxSubAccountsQuota = maxSub,
                            addressInput = current.addressInput.ifBlank { detail.locationText.orEmpty() },
                        )
                    }
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        loadError = detailRes.error.message,
                        availableAccounts = accounts,
                        maxSubAccountsQuota = maxSub,
                    )
                }
            }
        }
    }

    fun toggleAccountSelection(accountId: String) {
        mutableUiState.update { current ->
            val next = if (accountId in current.selectedAccountIds) {
                current.selectedAccountIds - accountId
            } else {
                current.selectedAccountIds + accountId
            }
            current.copy(selectedAccountIds = next)
        }
    }

    fun selectAllEnabledAccounts() {
        mutableUiState.update { current ->
            current.copy(selectedAccountIds = current.availableAccounts.map { it.accountId }.toSet())
        }
    }

    fun updateQrRawInput(raw: String) {
        val parsed = ChaoxingQrParser.parse(raw)
        mutableUiState.update {
            it.copy(
                qrRawInput = raw,
                parsedQrEnc = parsed?.enc,
                inputHintMessage = null,
            )
        }
    }

    fun onBarcodeScanned(rawValue: String) {
        val parsed = ChaoxingQrParser.parse(rawValue)
        mutableUiState.update {
            it.copy(
                qrRawInput = rawValue,
                parsedQrEnc = parsed?.enc,
                isScannerSheetOpen = false,
                inputHintMessage = if (parsed != null) "已解析签到二维码校验参数" else "已扫码，请确认二维码内容",
            )
        }
    }

    fun scanQrFromImageUri(uri: Uri) {
        viewModelScope.launch {
            when (val res = barcodeScanner.scanImageUri(uri)) {
                is AppResult.Success -> {
                    val first = res.data.firstOrNull()
                    if (first != null) {
                        onBarcodeScanned(first.rawValue)
                    } else {
                        mutableUiState.update { it.copy(inputHintMessage = "未在所选图片中识别到有效二维码") }
                    }
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(inputHintMessage = res.error.message)
                }
            }
        }
    }

    fun setScannerSheetOpen(open: Boolean) {
        mutableUiState.update { it.copy(isScannerSheetOpen = open) }
    }

    fun updateSignCode(code: String) {
        mutableUiState.update { it.copy(signCodeInput = code, inputHintMessage = null) }
    }

    fun updateLocation(latitude: String, longitude: String, address: String) {
        mutableUiState.update {
            it.copy(
                latitudeInput = latitude,
                longitudeInput = longitude,
                addressInput = address,
                inputHintMessage = null,
            )
        }
    }

    fun applyTargetCourseLocation() {
        val detail = uiState.value.detail ?: return
        val lat = detail.targetLatitudeGd ?: detail.targetLatitude
        val lng = detail.targetLongitudeGd ?: detail.targetLongitude
        if (lat == null || lng == null) {
            mutableUiState.update { it.copy(inputHintMessage = "当前签到未公开指定坐标，请手动输入或使用设备定位") }
            return
        }
        mutableUiState.update {
            it.copy(
                latitudeInput = lat.toString(),
                longitudeInput = lng.toString(),
                addressInput = detail.locationText.orEmpty(),
                inputHintMessage = "已填入活动预设地点坐标",
            )
        }
    }

    fun setPhoto(bytes: ByteArray, fileName: String) {
        mutableUiState.update {
            it.copy(photo = ImageBytesHolder(bytes, fileName), inputHintMessage = null)
        }
    }

    fun updateCaptchaValidate(validate: String) {
        mutableUiState.update { it.copy(captchaValidateInput = validate) }
    }

    fun executeSign(courseName: String) {
        val current = uiState.value
        if (current.isExecuting || current.selectedAccountIds.isEmpty()) {
            return
        }
        val orderedTargetIds = current.availableAccounts
            .filter { it.accountId in current.selectedAccountIds }
            .map { it.accountId }
            .ifEmpty { current.selectedAccountIds.toList() }

        val request = SignExecutionRequest(
            courseId = route.courseId,
            clazzId = route.clazzId,
            activeId = route.activeId,
            enc = current.parsedQrEnc,
            rawQrContent = current.qrRawInput.takeIf { it.isNotBlank() },
            signCode = current.signCodeInput.takeIf { it.isNotBlank() },
            latitude = current.latitudeInput.toDoubleOrNull(),
            longitude = current.longitudeInput.toDoubleOrNull(),
            address = current.addressInput.takeIf { it.isNotBlank() },
            photoBytes = current.photo?.bytes,
            photoFileName = current.photo?.fileName,
            captchaValidate = current.captchaValidateInput.takeIf { it.isNotBlank() },
        )

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isExecuting = true,
                    pendingCaptchaId = null,
                    pendingCaptchaMessage = null,
                    inputHintMessage = null,
                )
            }
            val summary = signCoordinator.executeForAccounts(
                targetAccountIds = orderedTargetIds,
                courseName = courseName,
                activityTitle = current.detail?.title ?: "课堂签到",
                request = request,
                prefetchedDetail = current.detail,
            )
            val captchaHit = summary.reports
                .mapNotNull { it.result as? SignExecutionResult.RequiresCaptcha }
                .firstOrNull()
            val missingInputHit = summary.reports
                .mapNotNull { it.result as? SignExecutionResult.RequiresExtraInput }
                .firstOrNull()

            mutableUiState.update {
                it.copy(
                    isExecuting = false,
                    batchSummary = summary,
                    pendingCaptchaId = captchaHit?.captchaId,
                    pendingCaptchaMessage = captchaHit?.message,
                    inputHintMessage = missingInputHit?.message,
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(route: ChaoxingNavKey.SignExecution): ChaoxingSignExecutionViewModel
    }
}
