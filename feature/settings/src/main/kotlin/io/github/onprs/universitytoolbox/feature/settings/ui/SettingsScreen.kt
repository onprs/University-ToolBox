package io.github.onprs.universitytoolbox.feature.settings.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.datastore.AppLanguage
import io.github.onprs.universitytoolbox.core.datastore.ThemeMode
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLinearProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxSectionHeader
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxWavyProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.ui.DevStubBanner
import io.github.onprs.universitytoolbox.core.ui.labelRes
import io.github.onprs.universitytoolbox.core.update.UpdateDownloadState
import io.github.onprs.universitytoolbox.feature.settings.R
import io.github.onprs.universitytoolbox.feature.settings.domain.PermissionKind
import io.github.onprs.universitytoolbox.feature.settings.domain.PermissionStatusItem
import io.github.onprs.universitytoolbox.feature.settings.domain.UpdateAssessment

/**
 * 全局设置中心主路由。
 */
@Composable
fun SettingsRoute(
    onOpenUnlockManage: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshRuntimeStatus()
        onPauseOrDispose {}
    }

    val onRequestPermissionAction = rememberPermissionActionHandler(
        context = context,
        onRefreshStatus = viewModel::refreshRuntimeStatus,
    )

    val resolvedSnackbarText = snackbarText(uiState.snackbarMessage)
    LaunchedEffect(uiState.snackbarMessage) {
        if (uiState.snackbarMessage != null && resolvedSnackbarText != null) {
            snackbarHostState.showSnackbar(resolvedSnackbarText)
            viewModel.consumeSnackbarMessage()
        }
    }

    SettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = {
            if (onBack != null) {
                onBack()
            } else {
                backDispatcher?.onBackPressed()
            }
        },
        onOpenUnlockManage = onOpenUnlockManage,
        onSelectThemeMode = viewModel::setThemeMode,
        onToggleDynamicColor = viewModel::setDynamicColorEnabled,
        onSelectLanguage = viewModel::setAppLanguage,
        onRefreshPermissions = viewModel::refreshRuntimeStatus,
        onRequestPermissionAction = onRequestPermissionAction,
        onManifestUrlChange = viewModel::updateManifestUrl,
        onCheckUpdate = viewModel::checkForUpdates,
        onDownloadAndInstall = viewModel::downloadAndInstallUpdate,
        onInstallReadyApk = viewModel::installDownloadedApk,
        onOpenInstallPermissionSettings = { openInstallUnknownAppsSettings(context) },
        onClearCache = viewModel::clearCache,
        onOpenDiagnosticPreview = viewModel::openDiagnosticPreview,
        onCloseDiagnosticPreview = viewModel::closeDiagnosticPreview,
        onExportLogs = { shareRedactedReport(context, uiState.diagnosticReportPreview) },
        onClearLogs = viewModel::clearDiagnosticLogs,
        modifier = modifier,
    )
}

private data class PendingPermissionRequest(
    val kind: PermissionKind,
    val permissions: List<String>,
    val hadRationaleBefore: Boolean,
)

@Composable
private fun rememberPermissionActionHandler(context: Context, onRefreshStatus: () -> Unit): (PermissionKind) -> Unit {
    val activity = remember(context) { context.findActivity() }
    var pendingRequest by remember { mutableStateOf<PendingPermissionRequest?>(null) }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onRefreshStatus()
        if (!granted) {
            handlePermissionDeniedFallback(context, activity, pendingRequest)
        }
        pendingRequest = null
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        onRefreshStatus()
        if (results.values.none { it }) {
            handlePermissionDeniedFallback(context, activity, pendingRequest)
        }
        pendingRequest = null
    }

    return { kind ->
        dispatchPermissionAction(
            kind = kind,
            context = context,
            activity = activity,
            onTrackPending = { pendingRequest = it },
            launchSingle = singlePermissionLauncher::launch,
            launchMultiple = multiplePermissionsLauncher::launch,
        )
    }
}

private fun dispatchPermissionAction(
    kind: PermissionKind,
    context: Context,
    activity: Activity?,
    onTrackPending: (PendingPermissionRequest) -> Unit,
    launchSingle: (String) -> Unit,
    launchMultiple: (Array<String>) -> Unit,
) {
    when (kind) {
        PermissionKind.CAMERA -> {
            val request = createPendingRequest(kind, listOf(Manifest.permission.CAMERA), activity)
            onTrackPending(request)
            launchSingle(Manifest.permission.CAMERA)
        }
        PermissionKind.LOCATION -> {
            val perms = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            onTrackPending(createPendingRequest(kind, perms, activity))
            launchMultiple(perms.toTypedArray())
        }
        PermissionKind.NOTIFICATIONS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val perm = Manifest.permission.POST_NOTIFICATIONS
                onTrackPending(createPendingRequest(kind, listOf(perm), activity))
                launchSingle(perm)
            } else {
                openNotificationSettings(context)
            }
        }
        PermissionKind.INSTALL_UNKNOWN_APPS -> openInstallUnknownAppsSettings(context)
    }
}

private fun createPendingRequest(
    kind: PermissionKind,
    permissions: List<String>,
    activity: Activity?,
): PendingPermissionRequest = PendingPermissionRequest(
    kind = kind,
    permissions = permissions,
    hadRationaleBefore = hasAnyPermissionRationale(activity, permissions),
)

private fun handlePermissionDeniedFallback(context: Context, activity: Activity?, request: PendingPermissionRequest?) {
    if (request == null) {
        return
    }
    val hasRationaleAfter = hasAnyPermissionRationale(activity, request.permissions)
    if (!request.hadRationaleBefore && !hasRationaleAfter) {
        if (request.kind == PermissionKind.NOTIFICATIONS) {
            openNotificationSettings(context)
        } else {
            openAppDetailsSettings(context)
        }
    }
}

private fun hasAnyPermissionRationale(activity: Activity?, permissions: List<String>): Boolean {
    if (activity == null) {
        return false
    }
    return permissions.any { perm ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onOpenUnlockManage: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onRefreshPermissions: () -> Unit,
    onRequestPermissionAction: (PermissionKind) -> Unit,
    onManifestUrlChange: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onInstallReadyApk: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onClearCache: () -> Unit,
    onOpenDiagnosticPreview: () -> Unit,
    onCloseDiagnosticPreview: () -> Unit,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = { SettingsTopBar(onBackClick = onBackClick) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
        ) {
            DevStubBanner(licenseState = uiState.licenseState)
            LicenseStatusSection(uiState = uiState, onOpenUnlockManage = onOpenUnlockManage)
            AppearanceAndLanguageSection(
                uiState = uiState,
                onSelectThemeMode = onSelectThemeMode,
                onToggleDynamicColor = onToggleDynamicColor,
                onSelectLanguage = onSelectLanguage,
            )
            PermissionsBoardSection(
                permissions = uiState.permissions,
                onRefresh = onRefreshPermissions,
                onRequestPermissionAction = onRequestPermissionAction,
            )
            UpdatesAndStorageSection(
                uiState = uiState,
                onManifestUrlChange = onManifestUrlChange,
                onCheckUpdate = onCheckUpdate,
                onDownloadAndInstall = onDownloadAndInstall,
                onInstallReadyApk = onInstallReadyApk,
                onOpenInstallPermissionSettings = onOpenInstallPermissionSettings,
                onClearCache = onClearCache,
            )
            DiagnosticsSection(
                onPreviewLogs = onOpenDiagnosticPreview,
                onExportLogs = onExportLogs,
                onClearLogs = onClearLogs,
            )
        }
    }

    if (uiState.isDiagnosticDialogOpen) {
        DiagnosticLogDialog(
            reportPreview = uiState.diagnosticReportPreview,
            onDismiss = onCloseDiagnosticPreview,
            onExportLogs = onExportLogs,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.feature_settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.feature_settings_back),
                )
            }
        },
    )
}

@Composable
private fun DiagnosticLogDialog(reportPreview: String, onDismiss: () -> Unit, onExportLogs: () -> Unit) {
    val configuration = LocalConfiguration.current
    val maxContentHeight = (configuration.screenHeightDp * 0.45f).dp.coerceIn(120.dp, 360.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.feature_settings_preview_logs)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = reportPreview,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExportLogs) {
                Text(text = stringResource(R.string.feature_settings_export_logs))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.feature_settings_close))
            }
        },
    )
}

@Composable
private fun LicenseStatusSection(uiState: SettingsUiState, onOpenUnlockManage: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ToolboxSectionHeader(title = stringResource(R.string.feature_settings_section_license))
            val summaryText = uiState.entitlement?.let { entitlement ->
                stringResource(
                    R.string.feature_settings_license_tier,
                    stringResource(entitlement.tier.labelRes()),
                )
            } ?: stringResource(R.string.feature_settings_license_locked)
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onOpenUnlockManage,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                )
                Text(text = stringResource(R.string.feature_settings_license_manage))
            }
        }
    }
}

@Composable
private fun AppearanceAndLanguageSection(
    uiState: SettingsUiState,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ToolboxSectionHeader(title = stringResource(R.string.feature_settings_section_appearance))
            ThemeSelectorRow(
                selectedMode = uiState.userPreferences.themeMode,
                onSelectThemeMode = onSelectThemeMode,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feature_settings_dynamic_color),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.feature_settings_dynamic_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.userPreferences.useDynamicColor,
                    onCheckedChange = onToggleDynamicColor,
                )
            }
            LanguageSelectorRow(
                selectedLanguage = uiState.userPreferences.appLanguage,
                onSelectLanguage = onSelectLanguage,
            )
        }
    }
}

@Composable
private fun ThemeSelectorRow(selectedMode: ThemeMode, onSelectThemeMode: (ThemeMode) -> Unit) {
    Text(
        text = stringResource(R.string.feature_settings_theme_label),
        style = MaterialTheme.typography.labelLarge,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onSelectThemeMode(mode) },
                label = {
                    Text(
                        text = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.feature_settings_theme_system
                                ThemeMode.LIGHT -> R.string.feature_settings_theme_light
                                ThemeMode.DARK -> R.string.feature_settings_theme_dark
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun LanguageSelectorRow(selectedLanguage: AppLanguage, onSelectLanguage: (AppLanguage) -> Unit) {
    Text(
        text = stringResource(R.string.feature_settings_language_label),
        style = MaterialTheme.typography.labelLarge,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        AppLanguage.entries.forEach { language ->
            FilterChip(
                selected = selectedLanguage == language,
                onClick = { onSelectLanguage(language) },
                label = {
                    Text(
                        text = stringResource(
                            when (language) {
                                AppLanguage.SYSTEM -> R.string.feature_settings_lang_system
                                AppLanguage.ZH -> R.string.feature_settings_lang_zh
                                AppLanguage.EN -> R.string.feature_settings_lang_en
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionsBoardSection(
    permissions: List<PermissionStatusItem>,
    onRefresh: () -> Unit,
    onRequestPermissionAction: (PermissionKind) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ToolboxSectionHeader(
                title = stringResource(R.string.feature_settings_section_permissions),
                trailingContent = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.feature_settings_perm_refresh),
                        )
                    }
                },
            )
            permissions.forEach { item ->
                PermissionStatusRow(
                    item = item,
                    onActionClick = { onRequestPermissionAction(item.kind) },
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(item: PermissionStatusItem, onActionClick: () -> Unit) {
    val labelRes = when (item.kind) {
        PermissionKind.CAMERA -> R.string.feature_settings_perm_camera
        PermissionKind.LOCATION -> R.string.feature_settings_perm_location
        PermissionKind.NOTIFICATIONS -> R.string.feature_settings_perm_notifications
        PermissionKind.INSTALL_UNKNOWN_APPS -> R.string.feature_settings_perm_install
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolboxSpacing.mediumSmall, vertical = ToolboxSpacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ToolboxStatusBadge(
                    text = stringResource(
                        if (item.isGranted) {
                            R.string.feature_settings_perm_granted
                        } else {
                            R.string.feature_settings_perm_denied
                        },
                    ),
                    containerColor = if (item.isGranted) {
                        ToolboxTheme.extendedColors.successContainer
                    } else {
                        ToolboxTheme.extendedColors.warningContainer
                    },
                    contentColor = if (item.isGranted) {
                        ToolboxTheme.extendedColors.onSuccessContainer
                    } else {
                        ToolboxTheme.extendedColors.onWarningContainer
                    },
                )
            }
            if (!item.isGranted) {
                TextButton(onClick = onActionClick) {
                    Text(text = stringResource(R.string.feature_settings_perm_open_settings))
                }
            }
        }
    }
}

@Composable
private fun UpdatesAndStorageSection(
    uiState: SettingsUiState,
    onManifestUrlChange: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onInstallReadyApk: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onClearCache: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    val isCacheBusy = uiState.downloadState is UpdateDownloadState.Downloading ||
        uiState.downloadState is UpdateDownloadState.Verifying

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ToolboxSectionHeader(title = stringResource(R.string.feature_settings_section_updates))

            Text(
                text = stringResource(
                    R.string.feature_settings_current_version,
                    uiState.versionInfo.versionName,
                    uiState.versionInfo.versionCode,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.manifestUrlInput,
                onValueChange = onManifestUrlChange,
                label = { Text(text = stringResource(R.string.feature_settings_manifest_url_hint)) },
                trailingIcon = {
                    if (uiState.manifestUrlInput != SettingsUiState.DEFAULT_MANIFEST_URL) {
                        IconButton(
                            onClick = { onManifestUrlChange(SettingsUiState.DEFAULT_MANIFEST_URL) },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.feature_settings_reset_manifest_url),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            UpdateAssessmentBanner(
                assessment = uiState.updateAssessment,
                hasManifest = uiState.latestManifest != null,
            )

            DownloadStateProgressArea(
                downloadState = uiState.downloadState,
                onInstallReadyApk = onInstallReadyApk,
                onOpenInstallPermissionSettings = onOpenInstallPermissionSettings,
            )

            UpdateActionsRow(
                uiState = uiState,
                onCheckUpdate = {
                    dismissKeyboard()
                    onCheckUpdate()
                },
                onDownloadAndInstall = {
                    dismissKeyboard()
                    onDownloadAndInstall()
                },
            )

            CacheCleanupCard(
                cacheSizeFormatted = uiState.cacheSizeFormatted,
                enabled = !isCacheBusy,
                onClearCache = onClearCache,
            )
        }
    }
}

@Composable
private fun UpdateActionsRow(uiState: SettingsUiState, onCheckUpdate: () -> Unit, onDownloadAndInstall: () -> Unit) {
    val showDownloadButton = uiState.latestManifest != null &&
        uiState.updateAssessment !is UpdateAssessment.UpToDate &&
        uiState.downloadState !is UpdateDownloadState.ReadyToInstall &&
        uiState.downloadState !is UpdateDownloadState.Downloading &&
        uiState.downloadState !is UpdateDownloadState.Installing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        OutlinedButton(
            onClick = onCheckUpdate,
            enabled = !uiState.isCheckingUpdate,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
            )
            Text(
                text = stringResource(
                    if (uiState.isCheckingUpdate) {
                        R.string.feature_settings_checking_update
                    } else {
                        R.string.feature_settings_check_update
                    },
                ),
            )
        }

        if (showDownloadButton) {
            Button(
                onClick = onDownloadAndInstall,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.feature_settings_download_install))
            }
        }
    }
}

@Composable
private fun CacheCleanupCard(cacheSizeFormatted: String, enabled: Boolean, onClearCache: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.mediumSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_settings_cache_size, cacheSizeFormatted),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onClearCache,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                )
                Text(text = stringResource(R.string.feature_settings_clear_cache))
            }
        }
    }
}

@Composable
private fun UpdateAssessmentBanner(assessment: UpdateAssessment, hasManifest: Boolean) {
    if (!hasManifest) {
        return
    }
    when (assessment) {
        UpdateAssessment.UpToDate -> {
            ToolboxStatusBadge(
                text = stringResource(R.string.feature_settings_up_to_date),
                containerColor = ToolboxTheme.extendedColors.successContainer,
                contentColor = ToolboxTheme.extendedColors.onSuccessContainer,
            )
        }
        is UpdateAssessment.OptionalUpdate -> {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(ToolboxSpacing.mediumSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
                ) {
                    Text(
                        text = stringResource(
                            R.string.feature_settings_optional_update,
                            assessment.latestVersionName,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (assessment.changelog.isNotBlank()) {
                        Text(
                            text = assessment.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        is UpdateAssessment.ForceUpdateRequired -> {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(ToolboxSpacing.mediumSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
                ) {
                    Text(
                        text = stringResource(
                            R.string.feature_settings_force_update,
                            assessment.minSupportedVersionCode,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (assessment.changelog.isNotBlank()) {
                        Text(
                            text = assessment.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStateProgressArea(
    downloadState: UpdateDownloadState,
    onInstallReadyApk: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
) {
    when (downloadState) {
        UpdateDownloadState.Idle -> Unit
        is UpdateDownloadState.Downloading -> {
            val total = downloadState.totalBytes
            if (total != null && total > 0L) {
                val fraction = (downloadState.bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val percent = (fraction * 100f).toInt()
                Text(
                    text = stringResource(R.string.feature_settings_downloading, percent),
                    style = MaterialTheme.typography.bodySmall,
                )
                ToolboxLinearProgressBar(progress = fraction)
            } else {
                Text(
                    text = stringResource(R.string.feature_settings_downloading_indeterminate),
                    style = MaterialTheme.typography.bodySmall,
                )
                ToolboxWavyProgressBar(progress = null)
            }
        }
        is UpdateDownloadState.Verifying -> {
            Text(
                text = stringResource(R.string.feature_settings_verifying_update),
                style = MaterialTheme.typography.bodySmall,
            )
            ToolboxWavyProgressBar(progress = null)
        }
        is UpdateDownloadState.ReadyToInstall -> {
            Button(
                onClick = onInstallReadyApk,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.feature_settings_install_now))
            }
        }
        UpdateDownloadState.Installing -> {
            Text(
                text = stringResource(R.string.feature_settings_installing_update),
                style = MaterialTheme.typography.bodySmall,
            )
            ToolboxLinearProgressBar()
        }
        is UpdateDownloadState.InstallPendingUserAction -> {
            downloadState.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        UpdateDownloadState.InstallSucceeded -> Unit
        is UpdateDownloadState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall)) {
                Text(
                    text = stringResource(R.string.feature_settings_update_failed, downloadState.error.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (downloadState.error is DomainError.Unsupported) {
                    TextButton(onClick = onOpenInstallPermissionSettings) {
                        Text(text = stringResource(R.string.feature_settings_perm_open_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(onPreviewLogs: () -> Unit, onExportLogs: () -> Unit, onClearLogs: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            ToolboxSectionHeader(title = stringResource(R.string.feature_settings_section_diagnostics))
            OutlinedButton(
                onClick = onPreviewLogs,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                )
                Text(text = stringResource(R.string.feature_settings_preview_logs))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            ) {
                FilledTonalButton(
                    onClick = onExportLogs,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                    )
                    Text(text = stringResource(R.string.feature_settings_export_logs))
                }
                TextButton(
                    onClick = onClearLogs,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.feature_settings_clear_logs))
                }
            }
        }
    }
}

@Composable
private fun snackbarText(message: SettingsSnackbarMessage?): String? = when (message) {
    is SettingsSnackbarMessage.CacheCleared ->
        stringResource(R.string.feature_settings_cache_cleared, message.reclaimedSizeFormatted)
    SettingsSnackbarMessage.LogsCleared ->
        stringResource(R.string.feature_settings_logs_cleared)
    is SettingsSnackbarMessage.UpdateError ->
        stringResource(R.string.feature_settings_update_failed, message.detail)
    SettingsSnackbarMessage.InstallPermissionRequired ->
        stringResource(R.string.feature_settings_perm_install_required)
    null -> null
}

private fun shareRedactedReport(context: Context, report: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, report)
    }
    val chooser = Intent.createChooser(shareIntent, null).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching {
        context.startActivity(chooser)
    }
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    launchSettingsSafely(context, intent)
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    launchSettingsSafely(context, intent)
}

private fun openInstallUnknownAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.fromParts("package", context.packageName, null)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    launchSettingsSafely(context, intent)
}

private fun launchSettingsSafely(context: Context, primaryIntent: Intent) {
    runCatching {
        context.startActivity(primaryIntent)
    }.onFailure {
        runCatching {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(fallback)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
