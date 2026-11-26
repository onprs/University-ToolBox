package io.github.onprs.universitytoolbox.feature.unlock.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLoadingIndicator
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSizes
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.FullScreenBarcodeScannerDialog
import io.github.onprs.universitytoolbox.core.ui.DevStubBanner
import io.github.onprs.universitytoolbox.core.ui.LoadingStateView
import io.github.onprs.universitytoolbox.core.ui.labelRes
import io.github.onprs.universitytoolbox.feature.unlock.R
import io.github.onprs.universitytoolbox.feature.unlock.domain.UnlockFeedbackKind
import io.github.onprs.universitytoolbox.feature.unlock.domain.UnlockUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private const val COPIED_BADGE_DURATION_MS = 2_000L

/**
 * 授权激活引导与授权管理主路由。
 */
@Composable
fun UnlockRoute(
    modifier: Modifier = Modifier,
    initialActivationCode: String? = null,
    manageMode: Boolean = false,
    onBack: (() -> Unit)? = null,
    onActivated: (() -> Unit)? = null,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionState = rememberUnlockCameraPermissionState(onOpenScanner = viewModel::openScanner)
    var copiedMarkerSeq by remember { mutableLongStateOf(0L) }

    LaunchedEffect(copiedMarkerSeq) {
        if (copiedMarkerSeq > 0L) {
            delay(COPIED_BADGE_DURATION_MS)
            copiedMarkerSeq = 0L
        }
    }

    LaunchedEffect(initialActivationCode, manageMode) {
        viewModel.applyRouteArguments(initialActivationCode, manageMode)
    }

    ObserveUnlockFeedback(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onChallengeCopied = { copiedMarkerSeq = uiState.feedbackSeq.coerceAtLeast(1L) },
        onActivated = onActivated,
        onConsumeFeedback = viewModel::consumeFeedback,
    )

    UnlockScreen(
        uiState = uiState,
        hasCameraPermission = permissionState.hasPermission,
        isPermanentlyDenied = permissionState.isPermanentlyDenied,
        isChallengeCopiedRecently = copiedMarkerSeq > 0L,
        snackbarHostState = snackbarHostState,
        canPopPage = (manageMode && uiState.isUnlocked) || onBack != null,
        onPageBackClick = { onBack?.invoke() ?: backDispatcher?.onBackPressed() },
        onCopyChallenge = {
            if (uiState.deviceChallenge.isNotEmpty()) {
                copyText(context, "DeviceCode", uiState.deviceChallenge)
                viewModel.notifyChallengeCopied()
            }
        },
        onKeyInputChange = viewModel::updateKeyInput,
        onPasteClick = { viewModel.applyPastedText(readClipboardText(context)) },
        onScanClick = permissionState.onScanClick,
        onPermissionActionClick = permissionState.onPermissionGuideAction,
        onCloseScanner = viewModel::closeScanner,
        onToggleTorch = viewModel::toggleTorch,
        onBarcodeScanned = viewModel::onBarcodeScanned,
        onActivateClick = viewModel::submitActivation,
        onStartRebind = viewModel::startRebindMode,
        onCancelRebind = viewModel::cancelRebindMode,
        onRequestClear = viewModel::requestClearLicense,
        onDismissClearConfirm = viewModel::dismissClearConfirm,
        onConfirmClear = viewModel::confirmClearLicense,
        modifier = modifier,
    )
}

private data class UnlockCameraPermissionState(
    val hasPermission: Boolean,
    val isPermanentlyDenied: Boolean,
    val onScanClick: () -> Unit,
    val onPermissionGuideAction: () -> Unit,
)

@Composable
private fun rememberUnlockCameraPermissionState(onOpenScanner: () -> Unit): UnlockCameraPermissionState {
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

    return UnlockCameraPermissionState(
        hasPermission = hasCameraPermission,
        isPermanentlyDenied = isPermanentlyDenied,
        onScanClick = {
            if (hasCameraPermission || isPermanentlyDenied) {
                onOpenScanner()
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        },
        onPermissionGuideAction = {
            if (isPermanentlyDenied) openAppSettings(context) else launcher.launch(Manifest.permission.CAMERA)
        },
    )
}

@Composable
private fun ObserveUnlockFeedback(
    uiState: UnlockUiState,
    snackbarHostState: SnackbarHostState,
    onChallengeCopied: () -> Unit,
    onActivated: (() -> Unit)?,
    onConsumeFeedback: () -> Unit,
) {
    val message = feedbackTextRes(uiState.feedbackKind)?.let { stringResource(it) }
    LaunchedEffect(uiState.feedbackSeq, uiState.feedbackKind) {
        val kind = uiState.feedbackKind ?: return@LaunchedEffect
        val resolvedMessage = message
        onConsumeFeedback()
        if (kind == UnlockFeedbackKind.CHALLENGE_COPIED) {
            onChallengeCopied()
        }
        if (kind == UnlockFeedbackKind.ACTIVATION_SUCCEEDED) {
            onActivated?.invoke()
        }
        if (resolvedMessage != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resolvedMessage)
        }
    }
}

@Composable
internal fun UnlockScreen(
    uiState: UnlockUiState,
    hasCameraPermission: Boolean,
    isPermanentlyDenied: Boolean,
    isChallengeCopiedRecently: Boolean,
    snackbarHostState: SnackbarHostState,
    canPopPage: Boolean,
    onPageBackClick: () -> Unit,
    onCopyChallenge: () -> Unit,
    onKeyInputChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onScanClick: () -> Unit,
    onPermissionActionClick: () -> Unit,
    onCloseScanner: () -> Unit,
    onToggleTorch: () -> Unit,
    onBarcodeScanned: (BarcodeScanResult) -> Unit,
    onActivateClick: () -> Unit,
    onStartRebind: () -> Unit,
    onCancelRebind: () -> Unit,
    onRequestClear: () -> Unit,
    onDismissClearConfirm: () -> Unit,
    onConfirmClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interceptSubStateBack = uiState.isScannerOpen || (uiState.isUnlocked && uiState.isRebinding)
    BackHandler(enabled = interceptSubStateBack) {
        if (uiState.isScannerOpen) {
            onCloseScanner()
        } else {
            onCancelRebind()
        }
    }

    val showBackNavigation = interceptSubStateBack || canPopPage
    val handleTopBack = {
        when {
            uiState.isScannerOpen -> onCloseScanner()
            uiState.isUnlocked && uiState.isRebinding -> onCancelRebind()
            else -> onPageBackClick()
        }
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
            UnlockTopBar(
                isUnlocked = uiState.isUnlocked,
                isRebinding = uiState.isRebinding,
                showBackNavigation = showBackNavigation,
                onBackClick = handleTopBack,
            )
        },
    ) { innerPadding ->
        if (uiState.isInitializing) {
            LoadingStateView(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        UnlockBodyContent(
            uiState = uiState,
            isChallengeCopiedRecently = isChallengeCopiedRecently,
            onCopyChallenge = onCopyChallenge,
            onKeyInputChange = onKeyInputChange,
            onPasteClick = onPasteClick,
            onScanClick = onScanClick,
            onCloseScanner = onCloseScanner,
            onActivateClick = onActivateClick,
            onStartRebind = onStartRebind,
            onCancelRebind = onCancelRebind,
            onRequestClear = onRequestClear,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        )
    }

    if (uiState.isScannerOpen) {
        FullScreenBarcodeScannerDialog(
            title = stringResource(R.string.feature_unlock_scan_qr),
            isScanningEnabled = !uiState.isSubmitting,
            externalTorchEnabled = uiState.torchEnabled,
            onExternalToggleTorch = onToggleTorch,
            externalHasCameraPermission = hasCameraPermission,
            externalIsPermanentlyDenied = isPermanentlyDenied,
            onExternalPermissionAction = onPermissionActionClick,
            onBarcodeDetected = onBarcodeScanned,
            onDismiss = onCloseScanner,
        )
    }

    if (uiState.isClearConfirmOpen) {
        ClearLicenseConfirmDialog(
            onDismiss = onDismissClearConfirm,
            onConfirm = onConfirmClear,
        )
    }
}

@Composable
private fun UnlockBodyContent(
    uiState: UnlockUiState,
    isChallengeCopiedRecently: Boolean,
    onCopyChallenge: () -> Unit,
    onKeyInputChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onScanClick: () -> Unit,
    onCloseScanner: () -> Unit,
    onActivateClick: () -> Unit,
    onStartRebind: () -> Unit,
    onCancelRebind: () -> Unit,
    onRequestClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLicenseState = when {
        uiState.isInitializing -> LicenseState.Initializing
        uiState.entitlement != null && uiState.lockReason == null -> LicenseState.Unlocked(uiState.entitlement)
        else -> LicenseState.Locked(
            reason = uiState.lockReason ?: LockReason.NOT_ACTIVATED,
            deviceChallenge = uiState.deviceChallenge,
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(ToolboxSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
    ) {
        DevStubBanner(licenseState = currentLicenseState)

        if (uiState.entitlement != null && !uiState.isRebinding && uiState.lockReason == null) {
            UnlockedEntitlementCard(
                entitlement = uiState.entitlement,
                deviceChallenge = uiState.deviceChallenge,
                isCopiedRecently = isChallengeCopiedRecently,
                onCopyChallenge = onCopyChallenge,
                onStartRebind = onStartRebind,
                onRequestClear = onRequestClear,
            )
        } else {
            uiState.lockReason?.let { reason ->
                LockReasonBannerCard(reason = reason)
            }
            DeviceChallengeCard(
                deviceChallenge = uiState.deviceChallenge,
                isCopiedRecently = isChallengeCopiedRecently,
                onCopyChallenge = onCopyChallenge,
            )
            ActivationFormCard(
                uiState = uiState,
                onKeyInputChange = onKeyInputChange,
                onPasteClick = onPasteClick,
                onScanClick = onScanClick,
                onCloseScanner = onCloseScanner,
                onActivateClick = onActivateClick,
                onCancelRebind = if (uiState.isRebinding) onCancelRebind else null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockTopBar(
    isUnlocked: Boolean,
    isRebinding: Boolean,
    showBackNavigation: Boolean,
    onBackClick: () -> Unit,
) {
    val titleRes = when {
        isUnlocked && isRebinding -> R.string.feature_unlock_rebind_title
        isUnlocked -> R.string.feature_unlock_manage_title
        else -> R.string.feature_unlock_title
    }
    TopAppBar(
        title = { Text(text = stringResource(titleRes)) },
        navigationIcon = {
            if (showBackNavigation) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.feature_unlock_back),
                    )
                }
            }
        },
    )
}

@Composable
private fun ClearLicenseConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.feature_unlock_clear_confirm_title)) },
        text = { Text(text = stringResource(R.string.feature_unlock_clear_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.feature_unlock_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.feature_unlock_cancel))
            }
        },
    )
}

@Composable
private fun LockReasonBannerCard(reason: LockReason) {
    val isWarning = reason == LockReason.NOT_ACTIVATED
    val container = if (isWarning) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isWarning) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val (badgeRes, textRes) = when (reason) {
        LockReason.NOT_ACTIVATED ->
            R.string.feature_unlock_badge_not_activated to R.string.feature_unlock_reason_not_activated
        LockReason.EXPIRED ->
            R.string.feature_unlock_badge_expired to R.string.feature_unlock_reason_expired
        LockReason.CLOCK_ROLLBACK_DETECTED ->
            R.string.feature_unlock_badge_clock_rollback to R.string.feature_unlock_reason_clock_rollback
        LockReason.REVOKED ->
            R.string.feature_unlock_badge_revoked to R.string.feature_unlock_reason_revoked
        LockReason.INVALID_SIGNATURE ->
            R.string.feature_unlock_badge_invalid_signature to R.string.feature_unlock_reason_invalid_signature
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolboxStatusBadge(
                text = stringResource(badgeRes),
                containerColor = container,
                contentColor = contentColor,
            )
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DeviceChallengeCard(deviceChallenge: String, isCopiedRecently: Boolean, onCopyChallenge: () -> Unit) {
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
                Text(
                    text = stringResource(R.string.feature_unlock_device_code_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(
                    onClick = onCopyChallenge,
                    enabled = deviceChallenge.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = if (isCopiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                    )
                    Text(
                        text = stringResource(
                            if (isCopiedRecently) {
                                R.string.feature_unlock_copied_short
                            } else {
                                R.string.feature_unlock_copy_code
                            },
                        ),
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = deviceChallenge.ifEmpty { "-" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.feature_unlock_device_code_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivationFormCard(
    uiState: UnlockUiState,
    onKeyInputChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onScanClick: () -> Unit,
    onCloseScanner: () -> Unit,
    onActivateClick: () -> Unit,
    onCancelRebind: (() -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSubmitEnabled = !uiState.isSubmitting && uiState.keyInput.isNotBlank()
    val dismissKeyboardAndSubmit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (isSubmitEnabled) {
            onActivateClick()
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ActivationKeyInputField(
                keyInput = uiState.keyInput,
                isSubmitting = uiState.isSubmitting,
                hasActivationError = uiState.hasActivationError,
                onKeyInputChange = onKeyInputChange,
                onDoneAction = dismissKeyboardAndSubmit,
            )

            PasteAndScanActionRow(
                isScannerOpen = uiState.isScannerOpen,
                onPasteClick = onPasteClick,
                onScanToggleClick = if (uiState.isScannerOpen) onCloseScanner else onScanClick,
            )

            Spacer(modifier = Modifier.height(ToolboxSpacing.extraSmall))

            ActivationSubmitRow(
                isSubmitting = uiState.isSubmitting,
                isSubmitEnabled = isSubmitEnabled,
                onActivateClick = dismissKeyboardAndSubmit,
                onCancelRebind = onCancelRebind,
            )
        }
    }
}

@Composable
private fun ActivationKeyInputField(
    keyInput: String,
    isSubmitting: Boolean,
    hasActivationError: Boolean,
    onKeyInputChange: (String) -> Unit,
    onDoneAction: () -> Unit,
) {
    OutlinedTextField(
        value = keyInput,
        onValueChange = onKeyInputChange,
        label = { Text(text = stringResource(R.string.feature_unlock_key_input_label)) },
        placeholder = { Text(text = stringResource(R.string.feature_unlock_key_input_hint)) },
        trailingIcon = if (keyInput.isNotEmpty() && !isSubmitting) {
            {
                IconButton(onClick = { onKeyInputChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.feature_unlock_clear_input),
                    )
                }
            }
        } else {
            null
        },
        maxLines = 4,
        isError = hasActivationError,
        supportingText = if (hasActivationError) {
            { Text(text = stringResource(R.string.feature_unlock_key_input_error)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDoneAction() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasteAndScanActionRow(isScannerOpen: Boolean, onPasteClick: () -> Unit, onScanToggleClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        OutlinedButton(
            onClick = onPasteClick,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentPaste,
                contentDescription = null,
                modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
            )
            Text(text = stringResource(R.string.feature_unlock_paste))
        }
        OutlinedButton(
            onClick = onScanToggleClick,
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
                        R.string.feature_unlock_close_scanner
                    } else {
                        R.string.feature_unlock_scan_qr
                    },
                ),
            )
        }
    }
}

@Composable
private fun ActivationSubmitRow(
    isSubmitting: Boolean,
    isSubmitEnabled: Boolean,
    onActivateClick: () -> Unit,
    onCancelRebind: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        if (onCancelRebind != null) {
            OutlinedButton(
                onClick = onCancelRebind,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.feature_unlock_cancel))
            }
        }
        Button(
            onClick = onActivateClick,
            enabled = isSubmitEnabled,
            modifier = Modifier.weight(1f),
        ) {
            if (isSubmitting) {
                ToolboxLoadingIndicator(
                    size = ToolboxSizes.iconSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                )
                Text(text = stringResource(R.string.feature_unlock_activate))
            }
        }
    }
}

@Composable
private fun UnlockedEntitlementCard(
    entitlement: LicenseEntitlement,
    deviceChallenge: String,
    isCopiedRecently: Boolean,
    onCopyChallenge: () -> Unit,
    onStartRebind: () -> Unit,
    onRequestClear: () -> Unit,
) {
    val tierLabel = stringResource(entitlement.tier.labelRes())
    val scopesLabel = if (LicenseEntitlement.WILDCARD_SCOPE in entitlement.grantedScopes) {
        stringResource(R.string.feature_unlock_scope_all)
    } else {
        entitlement.grantedScopes.sorted().joinToString(", ")
    }
    val expiryLabel = entitlement.expiresAtEpochSec?.let { epochSec ->
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()))
    } ?: stringResource(R.string.feature_unlock_detail_permanent)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = tierLabel,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                ToolboxStatusBadge(
                    text = entitlement.serialId,
                    containerColor = ToolboxTheme.extendedColors.successContainer,
                    contentColor = ToolboxTheme.extendedColors.onSuccessContainer,
                )
            }

            EntitlementDetailRow(
                label = stringResource(R.string.feature_unlock_detail_quota),
                value = entitlement.maxSubAccounts.toString(),
            )
            EntitlementDetailRow(
                label = stringResource(R.string.feature_unlock_detail_expiry),
                value = expiryLabel,
            )
            EntitlementDetailRow(
                label = stringResource(R.string.feature_unlock_detail_scopes),
                value = scopesLabel,
            )

            if (deviceChallenge.isNotEmpty()) {
                UnlockedDeviceChallengeBox(
                    deviceChallenge = deviceChallenge,
                    isCopiedRecently = isCopiedRecently,
                    onCopyChallenge = onCopyChallenge,
                )
            }

            UnlockedActionsRow(
                onStartRebind = onStartRebind,
                onRequestClear = onRequestClear,
            )
        }
    }
}

@Composable
private fun UnlockedDeviceChallengeBox(
    deviceChallenge: String,
    isCopiedRecently: Boolean,
    onCopyChallenge: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.mediumSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feature_unlock_device_code_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = deviceChallenge,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            IconButton(onClick = onCopyChallenge) {
                Icon(
                    imageVector = if (isCopiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(
                        if (isCopiedRecently) {
                            R.string.feature_unlock_copied_short
                        } else {
                            R.string.feature_unlock_copy_code
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun UnlockedActionsRow(onStartRebind: () -> Unit, onRequestClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        OutlinedButton(
            onClick = onStartRebind,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.feature_unlock_rebind_action))
        }
        Button(
            onClick = onRequestClear,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
            )
            Text(text = stringResource(R.string.feature_unlock_clear_action))
        }
    }
}

@Composable
private fun EntitlementDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun feedbackTextRes(kind: UnlockFeedbackKind?): Int? = when (kind) {
    UnlockFeedbackKind.CHALLENGE_COPIED -> R.string.feature_unlock_feedback_copied
    UnlockFeedbackKind.CLIPBOARD_EMPTY -> R.string.feature_unlock_feedback_clipboard_empty
    UnlockFeedbackKind.ACTIVATION_SUCCEEDED -> R.string.feature_unlock_feedback_activated
    UnlockFeedbackKind.ACTIVATION_FAILED_EMPTY -> R.string.feature_unlock_feedback_empty
    UnlockFeedbackKind.ACTIVATION_FAILED_INVALID -> R.string.feature_unlock_feedback_invalid
    UnlockFeedbackKind.LICENSE_CLEARED -> R.string.feature_unlock_feedback_cleared
    UnlockFeedbackKind.SCAN_FAILED -> R.string.feature_unlock_feedback_scan_failed
    UnlockFeedbackKind.SCAN_CANCELLED, null -> null
}

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

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
