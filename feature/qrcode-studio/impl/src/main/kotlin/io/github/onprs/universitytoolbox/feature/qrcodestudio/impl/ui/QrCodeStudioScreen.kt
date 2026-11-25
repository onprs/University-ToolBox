package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLoadingIndicator
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxSectionHeader
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSizes
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScannerView
import io.github.onprs.universitytoolbox.core.ui.EmptyStateView
import io.github.onprs.universitytoolbox.core.ui.PermissionGuideCard
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.R
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.InspectedBarcodePayload
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.ParsedField
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.QrFieldKey
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.QrPayloadCategory
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.SecurityNoticeKind
import kotlinx.coroutines.launch

private const val MIN_MASK_DOTS = 6
private const val MAX_MASK_DOTS = 12

/**
 * 条码/二维码检视工作台路由入口。
 */
@Composable
fun QrCodeStudioRoute(modifier: Modifier = Modifier, viewModel: QrCodeStudioViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionState = rememberQrCameraPermissionState(onOpenScanner = viewModel::openScanner)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.scanPickedImage {
                SafeImageBitmapDecoder.decodeSampledBitmap(context.contentResolver, uri)
            }
        }
    }

    ObserveStudioNotices(uiState = uiState, snackbarHostState = snackbarHostState, viewModel = viewModel)

    QrCodeStudioScreen(
        uiState = uiState,
        hasCameraPermission = permissionState.hasPermission,
        isPermanentlyDenied = permissionState.isPermanentlyDenied,
        snackbarHostState = snackbarHostState,
        onBackClick = { backDispatcher?.onBackPressed() },
        onScanClick = permissionState.onScanClick,
        onPermissionActionClick = permissionState.onPermissionGuideAction,
        onCloseScanner = viewModel::closeScanner,
        onToggleTorch = viewModel::toggleTorch,
        onLiveBarcodeDetected = viewModel::onLiveBarcodeDetected,
        onPickImageClick = {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onManualInputChange = viewModel::updateManualInput,
        onPasteManualClick = { viewModel.applyPastedManualInput(readClipboardText(context)) },
        onInspectManual = viewModel::inspectManualInput,
        onSelectHistory = viewModel::selectHistoryItem,
        onRemoveHistoryItem = viewModel::removeHistoryItem,
        onToggleFieldVisibility = viewModel::toggleFieldVisibility,
        onCopyValue = { label, value, isSensitive ->
            copyToClipboard(context, label, value, isSensitive)
            val message = resources.getString(R.string.feature_qrcodestudio_copied, label)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        },
        onRequestClearHistory = { viewModel.setClearHistoryConfirmOpen(true) },
        onDismissClearHistory = { viewModel.setClearHistoryConfirmOpen(false) },
        onConfirmClearHistory = viewModel::confirmClearHistory,
        modifier = modifier,
    )
}

private data class QrCameraPermissionState(
    val hasPermission: Boolean,
    val isPermanentlyDenied: Boolean,
    val onScanClick: () -> Unit,
    val onPermissionGuideAction: () -> Unit,
)

@Composable
private fun rememberQrCameraPermissionState(onOpenScanner: () -> Unit): QrCameraPermissionState {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(checkCameraGranted(context)) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = checkCameraGranted(context)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        hasRequestedPermission = true
        onOpenScanner()
    }

    val isPermanentlyDenied = !hasCameraPermission &&
        hasRequestedPermission &&
        !shouldShowCameraRationale(context)

    return QrCameraPermissionState(
        hasPermission = hasCameraPermission,
        isPermanentlyDenied = isPermanentlyDenied,
        onScanClick = {
            if (hasCameraPermission) onOpenScanner() else launcher.launch(Manifest.permission.CAMERA)
        },
        onPermissionGuideAction = {
            if (isPermanentlyDenied) openPackageSettings(context) else launcher.launch(Manifest.permission.CAMERA)
        },
    )
}

@Composable
private fun ObserveStudioNotices(
    uiState: QrCodeStudioUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: QrCodeStudioViewModel,
) {
    val noBarcodeText = stringResource(R.string.feature_qrcodestudio_notice_no_barcode)
    val scanFailedText = stringResource(R.string.feature_qrcodestudio_notice_image_failed)

    LaunchedEffect(uiState.noticeMessage) {
        val message = when (uiState.noticeMessage) {
            StudioNoticeMessage.NO_BARCODE_IN_IMAGE -> noBarcodeText
            StudioNoticeMessage.IMAGE_SCAN_FAILED -> scanFailedText
            null -> return@LaunchedEffect
        }
        viewModel.consumeNoticeMessage()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
    }
}

@Composable
internal fun QrCodeStudioScreen(
    uiState: QrCodeStudioUiState,
    hasCameraPermission: Boolean,
    isPermanentlyDenied: Boolean,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onPermissionActionClick: () -> Unit,
    onCloseScanner: () -> Unit,
    onToggleTorch: () -> Unit,
    onLiveBarcodeDetected: (BarcodeScanResult) -> Unit,
    onPickImageClick: () -> Unit,
    onManualInputChange: (String) -> Unit,
    onPasteManualClick: () -> Unit,
    onInspectManual: () -> Unit,
    onSelectHistory: (InspectedBarcodePayload) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onToggleFieldVisibility: (String) -> Unit,
    onCopyValue: (String, String, Boolean) -> Unit,
    onRequestClearHistory: () -> Unit,
    onDismissClearHistory: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = uiState.isScannerOpen) {
        onCloseScanner()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            )
        },
        topBar = {
            QrStudioTopBar(
                hasHistory = uiState.history.isNotEmpty(),
                onBackClick = {
                    if (uiState.isScannerOpen) onCloseScanner() else onBackClick()
                },
                onClearHistory = onRequestClearHistory,
            )
        },
    ) { innerPadding ->
        QrStudioContentList(
            uiState = uiState,
            innerPadding = innerPadding,
            hasCameraPermission = hasCameraPermission,
            isPermanentlyDenied = isPermanentlyDenied,
            onScanClick = onScanClick,
            onPermissionActionClick = onPermissionActionClick,
            onCloseScanner = onCloseScanner,
            onToggleTorch = onToggleTorch,
            onLiveBarcodeDetected = onLiveBarcodeDetected,
            onPickImageClick = onPickImageClick,
            onManualInputChange = onManualInputChange,
            onPasteManualClick = onPasteManualClick,
            onInspectManual = onInspectManual,
            onSelectHistory = onSelectHistory,
            onRemoveHistoryItem = onRemoveHistoryItem,
            onToggleFieldVisibility = onToggleFieldVisibility,
            onCopyValue = onCopyValue,
        )
    }

    if (uiState.isClearHistoryConfirmOpen) {
        ClearHistoryConfirmDialog(
            onDismiss = onDismissClearHistory,
            onConfirm = onConfirmClearHistory,
        )
    }
}

@Composable
private fun QrStudioContentList(
    uiState: QrCodeStudioUiState,
    innerPadding: PaddingValues,
    hasCameraPermission: Boolean,
    isPermanentlyDenied: Boolean,
    onScanClick: () -> Unit,
    onPermissionActionClick: () -> Unit,
    onCloseScanner: () -> Unit,
    onToggleTorch: () -> Unit,
    onLiveBarcodeDetected: (BarcodeScanResult) -> Unit,
    onPickImageClick: () -> Unit,
    onManualInputChange: (String) -> Unit,
    onPasteManualClick: () -> Unit,
    onInspectManual: () -> Unit,
    onSelectHistory: (InspectedBarcodePayload) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onToggleFieldVisibility: (String) -> Unit,
    onCopyValue: (String, String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding(),
        contentPadding = PaddingValues(ToolboxSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
    ) {
        item(key = "actions") {
            ScannerControlCard(
                uiState = uiState,
                hasCameraPermission = hasCameraPermission,
                isPermanentlyDenied = isPermanentlyDenied,
                onScanClick = onScanClick,
                onPermissionActionClick = onPermissionActionClick,
                onCloseScanner = onCloseScanner,
                onToggleTorch = onToggleTorch,
                onLiveBarcodeDetected = onLiveBarcodeDetected,
                onPickImageClick = onPickImageClick,
                onManualInputChange = onManualInputChange,
                onPasteManualClick = onPasteManualClick,
                onInspectManual = onInspectManual,
            )
        }

        val selected = uiState.selectedPayload
        if (selected != null) {
            item(key = "selected_detail_${selected.id}") {
                InspectedPayloadCard(
                    payload = selected,
                    revealedFieldIds = uiState.revealedFieldIds,
                    onToggleFieldVisibility = onToggleFieldVisibility,
                    onCopyValue = onCopyValue,
                )
            }
        } else {
            item(key = "empty") {
                EmptyStateView(
                    title = stringResource(R.string.feature_qrcodestudio_empty_title),
                    description = stringResource(R.string.feature_qrcodestudio_empty_desc),
                    icon = Icons.Outlined.QrCode2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }
        }

        if (uiState.history.isNotEmpty()) {
            item(key = "history_header") {
                ToolboxSectionHeader(
                    title = stringResource(R.string.feature_qrcodestudio_history_title),
                )
            }
            items(items = uiState.history, key = { it.id }) { historyItem ->
                HistorySummaryRow(
                    item = historyItem,
                    isSelected = historyItem.id == selected?.id,
                    onClick = { onSelectHistory(historyItem) },
                    onRemove = { onRemoveHistoryItem(historyItem.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrStudioTopBar(hasHistory: Boolean, onBackClick: () -> Unit, onClearHistory: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.feature_qrcodestudio_screen_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.feature_qrcodestudio_back),
                )
            }
        },
        actions = {
            if (hasHistory) {
                IconButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.feature_qrcodestudio_clear_history),
                    )
                }
            }
        },
    )
}

@Composable
private fun ClearHistoryConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.feature_qrcodestudio_clear_confirm_title)) },
        text = { Text(text = stringResource(R.string.feature_qrcodestudio_clear_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.feature_qrcodestudio_confirm_clear),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.feature_qrcodestudio_cancel))
            }
        },
    )
}

@Composable
private fun ScannerControlCard(
    uiState: QrCodeStudioUiState,
    hasCameraPermission: Boolean,
    isPermanentlyDenied: Boolean,
    onScanClick: () -> Unit,
    onPermissionActionClick: () -> Unit,
    onCloseScanner: () -> Unit,
    onToggleTorch: () -> Unit,
    onLiveBarcodeDetected: (BarcodeScanResult) -> Unit,
    onPickImageClick: () -> Unit,
    onManualInputChange: (String) -> Unit,
    onPasteManualClick: () -> Unit,
    onInspectManual: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ScannerPrimaryActionRow(
                isScannerOpen = uiState.isScannerOpen,
                isDecodingImage = uiState.isDecodingImage,
                onScanOrCloseClick = if (uiState.isScannerOpen) onCloseScanner else onScanClick,
                onPickImageClick = onPickImageClick,
            )

            if (uiState.isScannerOpen) {
                LiveScannerSection(
                    hasCameraPermission = hasCameraPermission,
                    isPermanentlyDenied = isPermanentlyDenied,
                    torchEnabled = uiState.torchEnabled,
                    onLiveBarcodeDetected = onLiveBarcodeDetected,
                    onToggleTorch = onToggleTorch,
                    onPermissionActionClick = onPermissionActionClick,
                )
            }

            ManualInspectFormSection(
                manualInput = uiState.manualInput,
                onManualInputChange = onManualInputChange,
                onPasteClick = onPasteManualClick,
                onInspectClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onInspectManual()
                },
            )
        }
    }
}

@Composable
private fun ScannerPrimaryActionRow(
    isScannerOpen: Boolean,
    isDecodingImage: Boolean,
    onScanOrCloseClick: () -> Unit,
    onPickImageClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        Button(
            onClick = onScanOrCloseClick,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = if (isScannerOpen) Icons.Outlined.Close else Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
            )
            Text(
                text = stringResource(
                    if (isScannerOpen) {
                        R.string.feature_qrcodestudio_close_scanner
                    } else {
                        R.string.feature_qrcodestudio_scan_action
                    },
                ),
            )
        }
        FilledTonalButton(
            onClick = onPickImageClick,
            enabled = !isDecodingImage,
            modifier = Modifier.weight(1f),
        ) {
            if (isDecodingImage) {
                ToolboxLoadingIndicator(
                    size = ToolboxSizes.iconSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ImageSearch,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                )
                Text(text = stringResource(R.string.feature_qrcodestudio_pick_image))
            }
        }
    }
}

@Composable
private fun ManualInspectFormSection(
    manualInput: String,
    onManualInputChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onInspectClick: () -> Unit,
) {
    OutlinedTextField(
        value = manualInput,
        onValueChange = onManualInputChange,
        label = { Text(text = stringResource(R.string.feature_qrcodestudio_paste_hint)) },
        trailingIcon = if (manualInput.isNotEmpty()) {
            {
                IconButton(onClick = { onManualInputChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.feature_qrcodestudio_clear_input),
                    )
                }
            }
        } else {
            null
        },
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small, Alignment.End),
    ) {
        TextButton(onClick = onPasteClick) {
            Icon(
                imageVector = Icons.Outlined.ContentPaste,
                contentDescription = null,
                modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
            )
            Text(text = stringResource(R.string.feature_qrcodestudio_paste_action))
        }
        OutlinedButton(
            onClick = onInspectClick,
            enabled = manualInput.isNotBlank(),
        ) {
            Text(text = stringResource(R.string.feature_qrcodestudio_inspect_button))
        }
    }
}

@Composable
private fun LiveScannerSection(
    hasCameraPermission: Boolean,
    isPermanentlyDenied: Boolean,
    torchEnabled: Boolean,
    onLiveBarcodeDetected: (BarcodeScanResult) -> Unit,
    onToggleTorch: () -> Unit,
    onPermissionActionClick: () -> Unit,
) {
    if (!hasCameraPermission) {
        PermissionGuideCard(
            title = stringResource(R.string.feature_qrcodestudio_camera_perm_title),
            rationale = stringResource(R.string.feature_qrcodestudio_camera_perm_desc),
            onRequestPermission = onPermissionActionClick,
            isPermanentlyDenied = isPermanentlyDenied,
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(MaterialTheme.shapes.medium),
    ) {
        BarcodeScannerView(
            onBarcodeDetected = onLiveBarcodeDetected,
            isScanningEnabled = true,
            torchEnabled = torchEnabled,
            modifier = Modifier.fillMaxSize(),
        )
        FilledTonalIconButton(
            onClick = onToggleTorch,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(ToolboxSpacing.small),
        ) {
            Icon(
                imageVector = if (torchEnabled) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                contentDescription = stringResource(R.string.feature_qrcodestudio_toggle_torch),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectedPayloadCard(
    payload: InspectedBarcodePayload,
    revealedFieldIds: Set<String>,
    onToggleFieldVisibility: (String) -> Unit,
    onCopyValue: (String, String, Boolean) -> Unit,
) {
    val rawLabel = stringResource(R.string.feature_qrcodestudio_field_text)
    val hasSensitiveContent = payload.fields.any { it.isSensitive }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolboxStatusBadge(
                    text = categoryLabel(payload.category),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(onClick = { onCopyValue(rawLabel, payload.rawContent, hasSensitiveContent) }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                    )
                    Text(text = stringResource(R.string.feature_qrcodestudio_copy_raw))
                }
            }

            if (payload.summaryTitle.isNotEmpty()) {
                Text(
                    text = payload.summaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (payload.securityNotices.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
                ) {
                    payload.securityNotices.forEach { notice ->
                        SecurityNoticeBadge(notice = notice)
                    }
                }
            }

            payload.fields.forEachIndexed { index, field ->
                val fieldId = "${payload.id}_${field.key.name}_$index"
                val isRevealed = fieldId in revealedFieldIds
                val label = field.customLabel ?: fieldLabel(field.key)
                val formattedValue = formatFieldValue(field)
                val displayValue = if (field.isSensitive && !isRevealed) {
                    maskSensitiveField(formattedValue)
                } else {
                    formattedValue
                }
                FieldValueRow(
                    label = label,
                    value = displayValue,
                    isSensitive = field.isSensitive,
                    isRevealed = isRevealed,
                    onToggleReveal = { onToggleFieldVisibility(fieldId) },
                    onCopy = { onCopyValue(label, field.value, field.isSensitive) },
                )
            }
        }
    }
}

@Composable
private fun formatFieldValue(field: ParsedField): String = when (field.key) {
    QrFieldKey.CAMPUS_ACTION -> when (field.value) {
        "attendance_qr" -> stringResource(R.string.feature_qrcodestudio_campus_qr)
        "attendance_token" -> stringResource(R.string.feature_qrcodestudio_campus_token)
        else -> field.value
    }
    QrFieldKey.WIFI_HIDDEN -> when (field.value.lowercase()) {
        "true" -> stringResource(R.string.feature_qrcodestudio_wifi_hidden_yes)
        "false" -> stringResource(R.string.feature_qrcodestudio_wifi_hidden_no)
        else -> field.value
    }
    else -> field.value
}

private fun maskSensitiveField(secret: String): String {
    val dotCount = secret.length.coerceIn(MIN_MASK_DOTS, MAX_MASK_DOTS)
    return "•".repeat(dotCount)
}

@Composable
private fun SecurityNoticeBadge(notice: SecurityNoticeKind) {
    val (container, content) = when (notice) {
        SecurityNoticeKind.SAFE_HTTPS ->
            ToolboxTheme.extendedColors.successContainer to ToolboxTheme.extendedColors.onSuccessContainer
        SecurityNoticeKind.INSECURE_HTTP, SecurityNoticeKind.IP_HOST_DETECTED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        SecurityNoticeKind.SENSITIVE_QUERY_PARAM,
        SecurityNoticeKind.CREDENTIAL_PROTECTED,
        SecurityNoticeKind.RAW_PAYLOAD_EMPTY,
        -> ToolboxTheme.extendedColors.warningContainer to ToolboxTheme.extendedColors.onWarningContainer
    }
    ToolboxStatusBadge(
        text = noticeLabel(notice),
        containerColor = container,
        contentColor = content,
    )
}

@Composable
private fun FieldValueRow(
    label: String,
    value: String,
    isSensitive: Boolean,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolboxSpacing.mediumSmall, vertical = ToolboxSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (isSensitive) {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        imageVector = if (isRevealed) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (isRevealed) {
                                R.string.feature_qrcodestudio_hide_secret
                            } else {
                                R.string.feature_qrcodestudio_show_secret
                            },
                        ),
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.feature_qrcodestudio_copy_field),
                )
            }
        }
    }
}

@Composable
private fun HistorySummaryRow(
    item: InspectedBarcodePayload,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ToolboxSpacing.mediumSmall, top = ToolboxSpacing.small, bottom = ToolboxSpacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.summaryTitle.ifEmpty { item.formatLabel },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${categoryLabel(item.category)} · ${item.formatLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                ToolboxStatusBadge(
                    text = stringResource(R.string.feature_qrcodestudio_selected_badge),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.feature_qrcodestudio_delete_item),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun categoryLabel(category: QrPayloadCategory): String = when (category) {
    QrPayloadCategory.URL -> stringResource(R.string.feature_qrcodestudio_cat_url)
    QrPayloadCategory.WIFI -> stringResource(R.string.feature_qrcodestudio_cat_wifi)
    QrPayloadCategory.CONTACT -> stringResource(R.string.feature_qrcodestudio_cat_contact)
    QrPayloadCategory.EMAIL -> stringResource(R.string.feature_qrcodestudio_cat_email)
    QrPayloadCategory.PHONE -> stringResource(R.string.feature_qrcodestudio_cat_phone)
    QrPayloadCategory.SMS -> stringResource(R.string.feature_qrcodestudio_cat_sms)
    QrPayloadCategory.GEO -> stringResource(R.string.feature_qrcodestudio_cat_geo)
    QrPayloadCategory.JSON -> stringResource(R.string.feature_qrcodestudio_cat_json)
    QrPayloadCategory.CAMPUS_SIGN -> stringResource(R.string.feature_qrcodestudio_cat_campus)
    QrPayloadCategory.PLAIN_TEXT -> stringResource(R.string.feature_qrcodestudio_cat_text)
}

@Composable
private fun noticeLabel(notice: SecurityNoticeKind): String = when (notice) {
    SecurityNoticeKind.SAFE_HTTPS -> stringResource(R.string.feature_qrcodestudio_notice_safe_https)
    SecurityNoticeKind.INSECURE_HTTP -> stringResource(R.string.feature_qrcodestudio_notice_insecure_http)
    SecurityNoticeKind.IP_HOST_DETECTED -> stringResource(R.string.feature_qrcodestudio_notice_ip_host)
    SecurityNoticeKind.SENSITIVE_QUERY_PARAM -> stringResource(R.string.feature_qrcodestudio_notice_sensitive_query)
    SecurityNoticeKind.CREDENTIAL_PROTECTED -> stringResource(R.string.feature_qrcodestudio_notice_credential)
    SecurityNoticeKind.RAW_PAYLOAD_EMPTY -> stringResource(R.string.feature_qrcodestudio_notice_empty)
}

private val qrFieldLabelMap: Map<QrFieldKey, Int> = mapOf(
    QrFieldKey.FORMAT to R.string.feature_qrcodestudio_field_format,
    QrFieldKey.RAW_LENGTH to R.string.feature_qrcodestudio_field_length,
    QrFieldKey.SCHEME to R.string.feature_qrcodestudio_field_scheme,
    QrFieldKey.HOST to R.string.feature_qrcodestudio_field_host,
    QrFieldKey.PORT to R.string.feature_qrcodestudio_field_port,
    QrFieldKey.PATH to R.string.feature_qrcodestudio_field_path,
    QrFieldKey.QUERY_PARAM to R.string.feature_qrcodestudio_field_text,
    QrFieldKey.WIFI_SSID to R.string.feature_qrcodestudio_field_wifi_ssid,
    QrFieldKey.WIFI_ENCRYPTION to R.string.feature_qrcodestudio_field_wifi_enc,
    QrFieldKey.WIFI_PASSWORD to R.string.feature_qrcodestudio_field_wifi_pass,
    QrFieldKey.WIFI_HIDDEN to R.string.feature_qrcodestudio_field_wifi_hidden,
    QrFieldKey.CONTACT_NAME to R.string.feature_qrcodestudio_field_contact_name,
    QrFieldKey.CONTACT_PHONE to R.string.feature_qrcodestudio_field_contact_phone,
    QrFieldKey.CONTACT_EMAIL to R.string.feature_qrcodestudio_field_contact_email,
    QrFieldKey.CONTACT_ORG to R.string.feature_qrcodestudio_field_contact_org,
    QrFieldKey.EMAIL_ADDRESS to R.string.feature_qrcodestudio_field_email_addr,
    QrFieldKey.EMAIL_SUBJECT to R.string.feature_qrcodestudio_field_email_subj,
    QrFieldKey.PHONE_NUMBER to R.string.feature_qrcodestudio_field_phone,
    QrFieldKey.SMS_RECIPIENT to R.string.feature_qrcodestudio_field_sms_to,
    QrFieldKey.SMS_BODY to R.string.feature_qrcodestudio_field_sms_body,
    QrFieldKey.LATITUDE to R.string.feature_qrcodestudio_field_lat,
    QrFieldKey.LONGITUDE to R.string.feature_qrcodestudio_field_lng,
    QrFieldKey.JSON_KEYS to R.string.feature_qrcodestudio_field_json_keys,
    QrFieldKey.CAMPUS_ACTION to R.string.feature_qrcodestudio_field_campus,
    QrFieldKey.TEXT_PREVIEW to R.string.feature_qrcodestudio_field_text,
)

@Composable
private fun fieldLabel(key: QrFieldKey): String =
    stringResource(qrFieldLabelMap[key] ?: R.string.feature_qrcodestudio_field_text)

private fun checkCameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun shouldShowCameraRationale(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun copyToClipboard(context: Context, label: String, value: String, isSensitive: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(label, value)
    if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

private fun openPackageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
