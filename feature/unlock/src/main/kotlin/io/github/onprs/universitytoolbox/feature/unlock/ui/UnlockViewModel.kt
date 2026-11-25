package io.github.onprs.universitytoolbox.feature.unlock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.feature.unlock.domain.UnlockDeepLinkParser
import io.github.onprs.universitytoolbox.feature.unlock.domain.UnlockFeedbackKind
import io.github.onprs.universitytoolbox.feature.unlock.domain.UnlockUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 授权激活、扫码解锁、Deep Link 激活与授权重绑/清除交互控制器。
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val licenseEngine: LicenseEngine,
    private val metadataRepository: ToolboxMetadataRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = mutableUiState.asStateFlow()

    private var lastHandledDeepLink: String? = null

    init {
        observeLicenseState()
    }

    private fun observeLicenseState() {
        viewModelScope.launch {
            licenseEngine.state.collect { licenseState ->
                when (licenseState) {
                    LicenseState.Initializing -> {
                        mutableUiState.update { it.copy(isInitializing = true) }
                    }
                    is LicenseState.Locked -> {
                        mutableUiState.update {
                            it.copy(
                                isInitializing = false,
                                lockReason = licenseState.reason,
                                deviceChallenge = licenseState.deviceChallenge,
                                entitlement = null,
                                isRebinding = false,
                            )
                        }
                    }
                    is LicenseState.Unlocked -> {
                        mutableUiState.update {
                            it.copy(
                                isInitializing = false,
                                lockReason = null,
                                entitlement = licenseState.entitlement,
                            )
                        }
                        refreshDeviceChallengeIfEmpty()
                    }
                }
            }
        }
    }

    private fun refreshDeviceChallengeIfEmpty() {
        if (mutableUiState.value.deviceChallenge.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            val challenge = runCatching { licenseEngine.getDeviceChallenge() }.getOrDefault("")
            if (challenge.isNotEmpty()) {
                mutableUiState.update { it.copy(deviceChallenge = challenge) }
            }
        }
    }

    fun applyRouteArguments(initialActivationCode: String?, manageMode: Boolean) {
        mutableUiState.update { it.copy(manageMode = manageMode) }
        val raw = initialActivationCode?.trim().orEmpty()
        if (raw.isEmpty() || raw == lastHandledDeepLink) {
            return
        }
        lastHandledDeepLink = raw
        val extracted = UnlockDeepLinkParser.extractActivationCode(raw)
        if (!extracted.isNullOrEmpty()) {
            mutableUiState.update {
                it.copy(
                    keyInput = extracted,
                    isRebinding = it.isUnlocked,
                    errorDetail = null,
                )
            }
            submitActivation()
        }
    }

    fun updateKeyInput(value: String) {
        mutableUiState.update { it.copy(keyInput = value, errorDetail = null) }
    }

    fun applyPastedText(clipboardText: String?) {
        val extracted = UnlockDeepLinkParser.extractActivationCode(clipboardText)
        val nextValue = extracted ?: clipboardText?.trim().orEmpty()
        if (nextValue.isNotEmpty()) {
            mutableUiState.update { it.copy(keyInput = nextValue, errorDetail = null) }
        }
    }

    fun notifyChallengeCopied() {
        mutableUiState.update { it.copy(feedbackKind = UnlockFeedbackKind.CHALLENGE_COPIED) }
    }

    fun openScanner() {
        mutableUiState.update { it.copy(isScannerOpen = true, errorDetail = null) }
    }

    fun closeScanner() {
        mutableUiState.update { it.copy(isScannerOpen = false, torchEnabled = false) }
    }

    fun toggleTorch() {
        mutableUiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun onBarcodeScanned(result: BarcodeScanResult) {
        val extracted = UnlockDeepLinkParser.extractActivationCode(result.rawValue)
        if (extracted.isNullOrEmpty()) {
            mutableUiState.update {
                it.copy(
                    isScannerOpen = false,
                    torchEnabled = false,
                    feedbackKind = UnlockFeedbackKind.SCAN_FAILED,
                )
            }
            return
        }
        mutableUiState.update {
            it.copy(
                isScannerOpen = false,
                torchEnabled = false,
                keyInput = extracted,
                errorDetail = null,
            )
        }
        submitActivation()
    }

    fun startRebindMode() {
        refreshDeviceChallengeIfEmpty()
        mutableUiState.update { it.copy(isRebinding = true, errorDetail = null) }
    }

    fun cancelRebindMode() {
        mutableUiState.update {
            it.copy(
                isRebinding = false,
                isScannerOpen = false,
                torchEnabled = false,
                keyInput = "",
                errorDetail = null,
            )
        }
    }

    fun submitActivation() {
        val candidate = UnlockDeepLinkParser.extractActivationCode(mutableUiState.value.keyInput)
        if (candidate.isNullOrEmpty()) {
            mutableUiState.update {
                it.copy(feedbackKind = UnlockFeedbackKind.ACTIVATION_FAILED_EMPTY)
            }
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorDetail = null) }
            val result = licenseEngine.activate(candidate)
            result.fold(
                onSuccess = { entitlement ->
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            keyInput = "",
                            isRebinding = false,
                            entitlement = entitlement,
                            lockReason = null,
                            feedbackKind = UnlockFeedbackKind.ACTIVATION_SUCCEEDED,
                            errorDetail = null,
                        )
                    }
                    metadataRepository.logAuditEvent(
                        category = "LICENSE",
                        action = "ACTIVATE",
                        status = "SUCCESS",
                        summary = "License activated (${entitlement.tier.name}, ${entitlement.serialId})",
                    )
                },
                onFailure = { throwable ->
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            feedbackKind = UnlockFeedbackKind.ACTIVATION_FAILED_INVALID,
                            errorDetail = throwable.message,
                        )
                    }
                    metadataRepository.logAuditEvent(
                        category = "LICENSE",
                        action = "ACTIVATE",
                        status = "FAILED",
                        summary = "Activation rejected",
                    )
                },
            )
        }
    }

    fun requestClearLicense() {
        mutableUiState.update { it.copy(isClearConfirmOpen = true) }
    }

    fun dismissClearConfirm() {
        mutableUiState.update { it.copy(isClearConfirmOpen = false) }
    }

    fun confirmClearLicense() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isClearing = true, isClearConfirmOpen = false) }
            licenseEngine.clearLicense()
            metadataRepository.logAuditEvent(
                category = "LICENSE",
                action = "CLEAR",
                status = "SUCCESS",
                summary = "Local license cleared by user",
            )
            mutableUiState.update {
                it.copy(
                    isClearing = false,
                    isRebinding = false,
                    keyInput = "",
                    feedbackKind = UnlockFeedbackKind.LICENSE_CLEARED,
                )
            }
        }
    }

    fun consumeFeedback() {
        mutableUiState.update { it.copy(feedbackKind = null) }
    }
}
