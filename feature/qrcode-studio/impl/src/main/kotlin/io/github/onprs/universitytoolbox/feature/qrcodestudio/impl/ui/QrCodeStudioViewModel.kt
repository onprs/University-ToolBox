package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QR_CODE_STUDIO_TOOL_ID
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.InspectedBarcodePayload
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.QrContentInspector
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 条码检视工作台轻提示分类。
 */
enum class StudioNoticeMessage {
    NO_BARCODE_IN_IMAGE,
    IMAGE_SCAN_FAILED,
}

/**
 * 条码/二维码检视工作台状态模型。
 */
data class QrCodeStudioUiState(
    val isScannerOpen: Boolean = false,
    val torchEnabled: Boolean = false,
    val isDecodingImage: Boolean = false,
    val manualInput: String = "",
    val selectedPayload: InspectedBarcodePayload? = null,
    val history: List<InspectedBarcodePayload> = emptyList(),
    val revealedFieldIds: Set<String> = emptySet(),
    val noticeMessage: StudioNoticeMessage? = null,
)

/**
 * 条码/二维码扫描检视工作台 ViewModel。
 */
@HiltViewModel
class QrCodeStudioViewModel @Inject constructor(
    private val barcodeScanner: BarcodeScanner,
    private val metadataRepository: ToolboxMetadataRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(QrCodeStudioUiState())
    val uiState: StateFlow<QrCodeStudioUiState> = mutableUiState.asStateFlow()

    fun openScanner() {
        mutableUiState.update { it.copy(isScannerOpen = true, noticeMessage = null) }
    }

    fun closeScanner() {
        mutableUiState.update { it.copy(isScannerOpen = false, torchEnabled = false) }
    }

    fun toggleTorch() {
        mutableUiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun updateManualInput(value: String) {
        mutableUiState.update { it.copy(manualInput = value, noticeMessage = null) }
    }

    fun inspectManualInput() {
        val raw = mutableUiState.value.manualInput
        if (raw.isBlank()) {
            return
        }
        recordInspection(rawContent = raw, formatLabel = DEFAULT_MANUAL_FORMAT)
        mutableUiState.update { it.copy(manualInput = "") }
    }

    fun onLiveBarcodeDetected(result: BarcodeScanResult) {
        recordInspection(rawContent = result.rawValue, formatLabel = result.format.name)
        mutableUiState.update { it.copy(isScannerOpen = false, torchEnabled = false) }
    }

    fun scanImageUri(uri: Uri) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isDecodingImage = true, noticeMessage = null) }
            when (val result = barcodeScanner.scanImageUri(uri)) {
                is AppResult.Success -> {
                    val first = result.data.firstOrNull()
                    if (first != null) {
                        recordInspection(rawContent = first.rawValue, formatLabel = first.format.name)
                    } else {
                        mutableUiState.update { it.copy(noticeMessage = StudioNoticeMessage.NO_BARCODE_IN_IMAGE) }
                    }
                }
                is AppResult.Failure -> {
                    mutableUiState.update { it.copy(noticeMessage = StudioNoticeMessage.IMAGE_SCAN_FAILED) }
                }
            }
            mutableUiState.update { it.copy(isDecodingImage = false) }
        }
    }

    fun selectHistoryItem(payload: InspectedBarcodePayload) {
        mutableUiState.update { it.copy(selectedPayload = payload, revealedFieldIds = emptySet()) }
    }

    fun toggleFieldVisibility(fieldId: String) {
        mutableUiState.update { current ->
            val next = if (fieldId in current.revealedFieldIds) {
                current.revealedFieldIds - fieldId
            } else {
                current.revealedFieldIds + fieldId
            }
            current.copy(revealedFieldIds = next)
        }
    }

    fun clearHistory() {
        mutableUiState.update {
            it.copy(
                selectedPayload = null,
                history = emptyList(),
                revealedFieldIds = emptySet(),
            )
        }
    }

    fun consumeNoticeMessage() {
        mutableUiState.update { it.copy(noticeMessage = null) }
    }

    private fun recordInspection(rawContent: String, formatLabel: String) {
        val nowMs = timeProvider.currentTimeMillis()
        val payload = QrContentInspector.inspect(
            rawContent = rawContent,
            formatLabel = formatLabel,
            id = UUID.randomUUID().toString(),
            scannedAtEpochMs = nowMs,
        )
        mutableUiState.update { current ->
            val deduplicated = current.history.filterNot {
                it.rawContent == payload.rawContent && it.formatLabel == payload.formatLabel
            }
            val nextHistory = (listOf(payload) + deduplicated).take(MAX_HISTORY_ITEMS)
            current.copy(
                selectedPayload = payload,
                history = nextHistory,
                revealedFieldIds = emptySet(),
                noticeMessage = null,
            )
        }
        viewModelScope.launch {
            metadataRepository.recordToolUsage(QR_CODE_STUDIO_TOOL_ID)
            metadataRepository.logAuditEvent(
                category = "SCANNER",
                action = "INSPECT_BARCODE",
                status = "SUCCESS",
                summary = "Inspected ${payload.category.name} (${payload.formatLabel})",
                toolId = QR_CODE_STUDIO_TOOL_ID,
            )
        }
    }

    private companion object {
        const val MAX_HISTORY_ITEMS = 50
        const val DEFAULT_MANUAL_FORMAT = "TEXT"
    }
}
