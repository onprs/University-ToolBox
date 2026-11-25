package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLinearProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxSectionHeader
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.ui.DevStubBanner
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.R
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSchoolOption
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingAccountsUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingLoginTab
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingAccountsUiEvent
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingAccountsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingAccountsScreen(
    viewModel: ChaoxingAccountsViewModel,
    onBack: () -> Unit,
    initialSharePayload: String? = null,
    onFirstAccountBound: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteAccount = state.accounts.firstOrNull { it.accountId == pendingDeleteAccountId }

    LifecycleStartEffect(viewModel) {
        viewModel.setForegroundActive(true)
        onStopOrDispose {
            viewModel.setForegroundActive(false)
        }
    }

    LaunchedEffect(initialSharePayload) {
        if (!initialSharePayload.isNullOrBlank()) {
            viewModel.consumeInitialSharePayload(initialSharePayload)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChaoxingAccountsUiEvent.FirstAccountBound -> onFirstAccountBound(event.accountId)
            }
        }
    }

    AccountsDialogsHost(
        state = state,
        viewModel = viewModel,
        pendingDeleteAccount = pendingDeleteAccount,
        onDismissDelete = { pendingDeleteAccountId = null },
    )

    Scaffold(
        topBar = {
            AccountsTopAppBar(
                onBack = onBack,
                onOpenDeviceInfo = { viewModel.setDeviceInfoDialogOpen(true) },
            )
        },
    ) { innerPadding ->
        AccountsContentList(
            state = state,
            viewModel = viewModel,
            onRequestDelete = { pendingDeleteAccountId = it.accountId },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsTopAppBar(onBack: () -> Unit, onOpenDeviceInfo: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.chaoxing_accounts_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.chaoxing_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenDeviceInfo) {
                Icon(
                    imageVector = Icons.Outlined.PhonelinkSetup,
                    contentDescription = "设备码说明",
                )
            }
        },
    )
}

@Composable
private fun AccountsDialogsHost(
    state: ChaoxingAccountsUiState,
    viewModel: ChaoxingAccountsViewModel,
    pendingDeleteAccount: ChaoxingAccountEntity?,
    onDismissDelete: () -> Unit,
) {
    val context = LocalContext.current

    pendingDeleteAccount?.let { target ->
        UnbindAccountConfirmDialog(
            target = target,
            hasOtherAccounts = state.accounts.size > 1,
            onConfirm = {
                viewModel.removeAccount(target.accountId)
                onDismissDelete()
            },
            onDismiss = onDismissDelete,
        )
    }

    if (state.isDeviceInfoDialogOpen) {
        ChaoxingDeviceCodeInfoDialog(
            hideBadge = state.hideDeviceCodeBadge,
            onToggleHideBadge = viewModel::setHideDeviceCodeBadge,
            onDismiss = { viewModel.setDeviceInfoDialogOpen(false) },
        )
    }

    state.activeShareBundle?.let { bundle ->
        ChaoxingShareAccountDialog(
            bundle = bundle,
            onCopyLink = { shareUrl ->
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("账号绑定链接", shareUrl))
                viewModel.notifyShareLinkCopied()
            },
            onDismiss = viewModel::dismissShareDialog,
        )
    }

    if (state.isImportScannerOpen) {
        ChaoxingImportQrScannerSheet(
            onBarcodeScanned = viewModel::onImportBarcodeScanned,
            onDismiss = { viewModel.setImportScannerOpen(false) },
        )
    }
}

@Composable
private fun AccountsContentList(
    state: ChaoxingAccountsUiState,
    viewModel: ChaoxingAccountsViewModel,
    onRequestDelete: (ChaoxingAccountEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(ToolboxSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
    ) {
        item { DevStubBanner(licenseState = state.licenseState) }
        item { QuotaOverviewCard(state = state) }

        if (state.accounts.isNotEmpty()) {
            item {
                ToolboxSectionHeader(
                    title = "已绑定账号",
                    subtitle = "开启开关的账号将自动参与多账号批量签到",
                )
            }
            items(state.accounts, key = { it.accountId }) { account ->
                BoundAccountCard(
                    account = account,
                    hideDeviceCodeBadge = state.hideDeviceCodeBadge,
                    isRepairing = state.repairingAccountId == account.accountId,
                    onToggleEnabled = { enabled -> viewModel.toggleAccountEnabled(account.accountId, enabled) },
                    onShowDeviceInfo = { viewModel.setDeviceInfoDialogOpen(true) },
                    onShareBinding = { viewModel.requestShareAccount(account.accountId) },
                    onRepairSession = { viewModel.repairAccountSession(account.accountId) },
                    onPromote = { viewModel.promoteToPrimary(account.accountId) },
                    onRemove = { onRequestDelete(account) },
                )
            }
        }

        item {
            ToolboxSectionHeader(
                title = "绑定学习通账号",
                subtitle = if (state.canBindMore) {
                    "支持密码登录、客户端扫码及账号绑定二维码/链接导入"
                } else {
                    "已达当前子账号配额上限，导入已有账号仍可同步更新统一设备码"
                },
            )
        }

        item {
            BindAccountPanel(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun UnbindAccountConfirmDialog(
    target: ChaoxingAccountEntity,
    hasOtherAccounts: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = if (target.isPrimary && hasOtherAccounts) {
        "确定移除主账号 ${target.displayName}（${target.maskedAccount}）及其本地缓存会话与课程吗？移除后将自动把下一账号设为主账号。"
    } else {
        "确定移除 ${target.displayName}（${target.maskedAccount}）及其本地缓存会话与课程吗？"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "解绑账号") },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = "确认解绑")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        },
    )
}

@Composable
private fun QuotaOverviewCard(state: ChaoxingAccountsUiState) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall)) {
                Text(text = "子账号配额使用情况", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "主账号 1 席 + 子账号 ${state.currentSubAccounts} / ${state.maxSubAccounts}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ToolboxStatusBadge(
                text = if (state.canBindMore) "可继续绑定" else "已达上限",
                containerColor = if (state.canBindMore) {
                    ToolboxTheme.extendedColors.successContainer
                } else {
                    ToolboxTheme.extendedColors.warningContainer
                },
                contentColor = if (state.canBindMore) {
                    ToolboxTheme.extendedColors.onSuccessContainer
                } else {
                    ToolboxTheme.extendedColors.onWarningContainer
                },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun BoundAccountCard(
    account: ChaoxingAccountEntity,
    hideDeviceCodeBadge: Boolean,
    isRepairing: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onShowDeviceInfo: () -> Unit,
    onShareBinding: () -> Unit,
    onRepairSession: () -> Unit,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatarThumbnail(
                    avatarUrl = account.avatarUrl,
                    displayName = account.displayName,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = account.displayName, style = MaterialTheme.typography.titleSmall)
                        ToolboxStatusBadge(
                            text = if (account.isPrimary) "主账号" else "子账号",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (!hideDeviceCodeBadge) {
                            DeviceCodeStatusIconButton(
                                isUnified = account.isUnifiedDeviceCode,
                                onClick = onShowDeviceInfo,
                            )
                        }
                    }
                    Text(
                        text = listOf(account.maskedAccount, account.schoolName)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = account.isEnabled, onCheckedChange = onToggleEnabled)
            }

            BoundAccountActionsRow(
                account = account,
                isRepairing = isRepairing,
                onShareBinding = onShareBinding,
                onRepairSession = onRepairSession,
                onPromote = onPromote,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun BoundAccountActionsRow(
    account: ChaoxingAccountEntity,
    isRepairing: Boolean,
    onShareBinding: () -> Unit,
    onRepairSession: () -> Unit,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
) {
    val isActive = account.sessionStatus == ChaoxingAccountManager.SESSION_ACTIVE
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolboxStatusBadge(
            text = if (isActive) "会话有效" else "需重新登录",
            containerColor = if (isActive) {
                ToolboxTheme.extendedColors.successContainer
            } else {
                ToolboxTheme.extendedColors.warningContainer
            },
            contentColor = if (isActive) {
                ToolboxTheme.extendedColors.onSuccessContainer
            } else {
                ToolboxTheme.extendedColors.onWarningContainer
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isActive && !account.encryptedPasswordCiphertext.isNullOrBlank()) {
                TextButton(onClick = onRepairSession, enabled = !isRepairing) {
                    Text(text = if (isRepairing) "重连中…" else "刷新会话")
                }
            }
            TextButton(onClick = onShareBinding) { Text(text = "分享绑定") }
            if (!account.isPrimary) {
                TextButton(onClick = onPromote) { Text(text = "设为主账号") }
            }
            IconButton(onClick = onRemove) {
                Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = "解绑")
            }
        }
    }
}

@Composable
private fun BindAccountPanel(state: ChaoxingAccountsUiState, viewModel: ChaoxingAccountsViewModel) {
    val focusManager = LocalFocusManager.current
    val selectLoginTab: (ChaoxingLoginTab) -> Unit = { tab ->
        focusManager.clearFocus()
        viewModel.selectTab(tab)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
        ) {
            PrimaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
                Tab(
                    selected = state.activeTab == ChaoxingLoginTab.PASSWORD,
                    onClick = { selectLoginTab(ChaoxingLoginTab.PASSWORD) },
                    text = { Text(text = "密码登录") },
                )
                Tab(
                    selected = state.activeTab == ChaoxingLoginTab.QR_CODE,
                    onClick = { selectLoginTab(ChaoxingLoginTab.QR_CODE) },
                    text = { Text(text = "学习通扫码") },
                )
                Tab(
                    selected = state.activeTab == ChaoxingLoginTab.IMPORT_SHARED,
                    onClick = { selectLoginTab(ChaoxingLoginTab.IMPORT_SHARED) },
                    text = { Text(text = "绑定导入") },
                )
            }

            if (state.isSubmittingLogin) {
                ToolboxLinearProgressBar()
            }

            when (state.activeTab) {
                ChaoxingLoginTab.PASSWORD -> PasswordLoginForm(state = state, viewModel = viewModel)
                ChaoxingLoginTab.QR_CODE -> QrLoginForm(state = state, viewModel = viewModel)
                ChaoxingLoginTab.IMPORT_SHARED -> ImportSharedAccountForm(state = state, viewModel = viewModel)
            }

            FeedbackBanner(message = state.feedbackMessage, isError = state.isErrorFeedback)
        }
    }
}

@Composable
private fun FeedbackBanner(message: String?, isError: Boolean) {
    if (message.isNullOrBlank()) {
        return
    }
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            ToolboxTheme.extendedColors.successContainer
        },
        shape = RoundedCornerShape(ToolboxRadius.chip),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                ToolboxTheme.extendedColors.onSuccessContainer
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(ToolboxSpacing.small),
        )
    }
}

@Composable
private fun ImportSharedAccountForm(state: ChaoxingAccountsUiState, viewModel: ChaoxingAccountsViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val captchaFocusRequester = remember { FocusRequester() }
    val hasCaptchaField = !state.captchaPromptMessage.isNullOrBlank()
    val canSubmit = !state.isSubmittingLogin && state.importShareInput.isNotBlank()
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.scanImportQrFromUri(uri)
        }
    }
    val submitImport: () -> Unit = {
        focusManager.clearFocus()
        if (canSubmit) {
            viewModel.submitSharedImport()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        Text(
            text = "扫描机主在本应用「分享绑定」中生成的账号绑定二维码，或粘贴导入链接，可一键登录并同步统一设备码。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ImportScannerActionRow(
            onOpenCameraScanner = {
                focusManager.clearFocus()
                viewModel.setImportScannerOpen(true)
            },
            onOpenGalleryPicker = {
                focusManager.clearFocus()
                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        ImportShareLinkTextField(
            value = state.importShareInput,
            hasCaptchaField = hasCaptchaField,
            onValueChange = viewModel::updateImportShareInput,
            onPasteFromClipboard = {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                val text = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isNotBlank()) {
                    viewModel.updateImportShareInput(text)
                }
            },
            onNext = { captchaFocusRequester.requestFocus() },
            onDone = submitImport,
        )
        if (hasCaptchaField) {
            CaptchaValidateTextField(
                value = state.captchaValidateInput,
                onValueChange = viewModel::updateCaptchaValidate,
                focusRequester = captchaFocusRequester,
                onDone = submitImport,
            )
        }
        Button(
            onClick = submitImport,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "导入并绑定设备码")
        }
    }
}

@Composable
private fun ImportScannerActionRow(onOpenCameraScanner: () -> Unit, onOpenGalleryPicker: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        FilledTonalButton(
            onClick = onOpenCameraScanner,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = " 相机扫码")
        }
        FilledTonalButton(
            onClick = onOpenGalleryPicker,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = " 相册识图")
        }
    }
}

@Composable
private fun ImportShareLinkTextField(
    value: String,
    hasCaptchaField: Boolean,
    onValueChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "账号绑定链接") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            autoCorrectEnabled = false,
            imeAction = if (hasCaptchaField) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
            onDone = { onDone() },
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "清空链接")
                    }
                }
                TextButton(onClick = onPasteFromClipboard) {
                    Text(text = "粘贴")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordLoginForm(state: ChaoxingAccountsUiState, viewModel: ChaoxingAccountsViewModel) {
    val focusManager = LocalFocusManager.current
    val accountFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val captchaFocusRequester = remember { FocusRequester() }
    var showPassword by remember { mutableStateOf(false) }

    val hasCaptchaField = !state.captchaPromptMessage.isNullOrBlank()
    val canSubmit = !state.isSubmittingLogin && state.accountInput.isNotBlank() && state.passwordInput.isNotBlank()
    val submitLogin: () -> Unit = {
        focusManager.clearFocus()
        if (canSubmit) {
            viewModel.submitPasswordLogin()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        SchoolSelectionSection(
            schoolKeyword = state.schoolKeyword,
            selectedSchool = state.selectedSchool,
            suggestions = state.schoolSuggestions,
            onKeywordChange = viewModel::updateSchoolKeyword,
            onSelectSchool = { option ->
                viewModel.selectSchool(option)
                if (option != null) {
                    accountFocusRequester.requestFocus()
                }
            },
            onAdvanceToAccount = { accountFocusRequester.requestFocus() },
        )
        AccountLoginCredentialsFields(
            accountInput = state.accountInput,
            passwordInput = state.passwordInput,
            isSchoolLogin = state.selectedSchool != null,
            showPassword = showPassword,
            hasCaptchaField = hasCaptchaField,
            accountFocusRequester = accountFocusRequester,
            passwordFocusRequester = passwordFocusRequester,
            onAccountChange = viewModel::updateAccountInput,
            onPasswordChange = viewModel::updatePasswordInput,
            onToggleShowPassword = { showPassword = !showPassword },
            onAdvanceToCaptcha = { captchaFocusRequester.requestFocus() },
            onSubmit = submitLogin,
        )
        if (hasCaptchaField) {
            CaptchaValidateTextField(
                value = state.captchaValidateInput,
                onValueChange = viewModel::updateCaptchaValidate,
                focusRequester = captchaFocusRequester,
                onDone = submitLogin,
            )
        }
        Button(
            onClick = submitLogin,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "安全登录并加密保存")
        }
    }
}

@Composable
private fun SchoolSelectionSection(
    schoolKeyword: String,
    selectedSchool: ChaoxingSchoolOption?,
    suggestions: List<ChaoxingSchoolOption>,
    onKeywordChange: (String) -> Unit,
    onSelectSchool: (ChaoxingSchoolOption?) -> Unit,
    onAdvanceToAccount: () -> Unit,
) {
    OutlinedTextField(
        value = schoolKeyword,
        onValueChange = onKeywordChange,
        label = { Text(text = "学校/机构（手机号通用登录可留空）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onAdvanceToAccount() }),
        trailingIcon = {
            if (schoolKeyword.isNotEmpty() || selectedSchool != null) {
                IconButton(onClick = { onSelectSchool(null) }) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "清除所选学校")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    SchoolSuggestionsList(
        suggestions = suggestions,
        selected = selectedSchool,
        onSelect = onSelectSchool,
    )
}

@Suppress("LongParameterList")
@Composable
private fun AccountLoginCredentialsFields(
    accountInput: String,
    passwordInput: String,
    isSchoolLogin: Boolean,
    showPassword: Boolean,
    hasCaptchaField: Boolean,
    accountFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleShowPassword: () -> Unit,
    onAdvanceToCaptcha: () -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = accountInput,
        onValueChange = onAccountChange,
        label = { Text(text = if (!isSchoolLogin) "手机号 / 超星号 / 邮箱" else "学号 / 工号") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (!isSchoolLogin) KeyboardType.Email else KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
        trailingIcon = {
            if (accountInput.isNotEmpty()) {
                IconButton(onClick = { onAccountChange("") }) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "清空账号")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(accountFocusRequester),
    )
    OutlinedTextField(
        value = passwordInput,
        onValueChange = onPasswordChange,
        label = { Text(text = "登录密码") },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = if (hasCaptchaField) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onAdvanceToCaptcha() },
            onDone = { onSubmit() },
        ),
        trailingIcon = {
            IconButton(onClick = onToggleShowPassword) {
                Icon(
                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(passwordFocusRequester),
    )
}

@Composable
private fun CaptchaValidateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "安全验证凭据") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Composable
private fun SchoolSuggestionsList(
    suggestions: List<ChaoxingSchoolOption>,
    selected: ChaoxingSchoolOption?,
    onSelect: (ChaoxingSchoolOption?) -> Unit,
) {
    if (selected != null) {
        TextButton(onClick = { onSelect(null) }) {
            Text(text = "已选机构：${selected.name}（点击切回手机号登录）")
        }
        return
    }
    if (suggestions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall)) {
            suggestions.take(4).forEach { option ->
                FilledTonalButton(
                    onClick = { onSelect(option) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = option.name)
                }
            }
        }
    }
}

@Composable
private fun QrLoginForm(state: ChaoxingAccountsUiState, viewModel: ChaoxingAccountsViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        val bitmap = remember(state.qrImage) {
            state.qrImage?.bytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "登录二维码",
                modifier = Modifier.size(180.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = state.qrStatusText.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = viewModel::refreshQrSession) {
            Text(text = "刷新登录二维码")
        }
    }
}

@Composable
private fun AccountAvatarThumbnail(avatarUrl: String, displayName: String) {
    val fallback: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val initial = displayName.trim().firstOrNull()?.toString()
            if (!initial.isNullOrBlank()) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    val avatarModifier = Modifier
        .size(44.dp)
        .clip(CircleShape)

    if (avatarUrl.isBlank()) {
        Box(modifier = avatarModifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = avatarUrl,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier,
            loading = { fallback() },
            error = { fallback() },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}
