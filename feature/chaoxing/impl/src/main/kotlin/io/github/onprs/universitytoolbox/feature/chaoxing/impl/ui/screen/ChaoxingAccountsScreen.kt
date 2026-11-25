package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingAccountsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingAccountsScreen(viewModel: ChaoxingAccountsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteAccount by remember { mutableStateOf<ChaoxingAccountEntity?>(null) }

    pendingDeleteAccount?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAccount = null },
            title = { Text(text = "解绑账号") },
            text = { Text(text = "确定移除 ${target.displayName}（${target.maskedAccount}）及其本地缓存会话吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeAccount(target.accountId)
                        pendingDeleteAccount = null
                    },
                ) { Text(text = "解绑") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAccount = null }) { Text(text = "取消") }
            },
        )
    }

    Scaffold(
        topBar = {
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
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
        ) {
            item { DevStubBanner(licenseState = state.licenseState) }
            item { QuotaOverviewCard(state = state) }

            if (state.accounts.isNotEmpty()) {
                item {
                    ToolboxSectionHeader(
                        title = "已绑定账号",
                        subtitle = "开启参与开关后将在多账号签到中按限流队列依次执行",
                    )
                }
                items(state.accounts, key = { it.accountId }) { account ->
                    BoundAccountCard(
                        account = account,
                        onToggleEnabled = { enabled -> viewModel.toggleAccountEnabled(account.accountId, enabled) },
                        onPromote = { viewModel.promoteToPrimary(account.accountId) },
                        onRemove = { pendingDeleteAccount = account },
                    )
                }
            }

            item {
                ToolboxSectionHeader(
                    title = "绑定学习通账号",
                    subtitle = if (state.canBindMore) "支持手机号密码或官方客户端扫码登录" else "已达当前子账号配额上限",
                )
            }

            item {
                BindAccountPanel(state = state, viewModel = viewModel)
            }
        }
    }
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

@Composable
private fun BoundAccountCard(
    account: ChaoxingAccountEntity,
    onToggleEnabled: (Boolean) -> Unit,
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
                AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = account.displayName,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = account.displayName, style = MaterialTheme.typography.titleSmall)
                        ToolboxStatusBadge(
                            text = if (account.isPrimary) "主账号" else "子账号",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isActive = account.sessionStatus == ChaoxingAccountManager.SESSION_ACTIVE
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
                Row(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
                    if (!account.isPrimary) {
                        TextButton(onClick = onPromote) { Text(text = "设为主账号") }
                    }
                    IconButton(onClick = onRemove) {
                        Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = "解绑")
                    }
                }
            }
        }
    }
}

@Composable
private fun BindAccountPanel(state: ChaoxingAccountsUiState, viewModel: ChaoxingAccountsViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
        ) {
            PrimaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
                Tab(
                    selected = state.activeTab == ChaoxingLoginTab.PASSWORD,
                    onClick = { viewModel.selectTab(ChaoxingLoginTab.PASSWORD) },
                    text = { Text(text = "密码登录") },
                )
                Tab(
                    selected = state.activeTab == ChaoxingLoginTab.QR_CODE,
                    onClick = { viewModel.selectTab(ChaoxingLoginTab.QR_CODE) },
                    text = { Text(text = "扫码登录") },
                )
            }

            if (state.isSubmittingLogin) {
                ToolboxLinearProgressBar()
            }

            when (state.activeTab) {
                ChaoxingLoginTab.PASSWORD -> PasswordLoginForm(state = state, viewModel = viewModel)
                ChaoxingLoginTab.QR_CODE -> QrLoginForm(state = state, viewModel = viewModel)
            }

            if (!state.feedbackMessage.isNullOrBlank()) {
                Surface(
                    color = if (state.isErrorFeedback) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        ToolboxTheme.extendedColors.successContainer
                    },
                    shape = RoundedCornerShape(ToolboxRadius.chip),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.feedbackMessage,
                        color = if (state.isErrorFeedback) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            ToolboxTheme.extendedColors.onSuccessContainer
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(ToolboxSpacing.small),
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordLoginForm(state: ChaoxingAccountsUiState, viewModel: ChaoxingAccountsViewModel) {
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        OutlinedTextField(
            value = state.schoolKeyword,
            onValueChange = viewModel::updateSchoolKeyword,
            label = { Text(text = "学校/机构（手机号通用登录可留空）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SchoolSuggestionsList(
            suggestions = state.schoolSuggestions,
            selected = state.selectedSchool,
            onSelect = viewModel::selectSchool,
        )
        OutlinedTextField(
            value = state.accountInput,
            onValueChange = viewModel::updateAccountInput,
            label = { Text(text = if (state.selectedSchool == null) "手机号 / 超星号 / 邮箱" else "学号 / 工号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.passwordInput,
            onValueChange = viewModel::updatePasswordInput,
            label = { Text(text = "登录密码") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.captchaPromptMessage.isNullOrBlank()) {
            OutlinedTextField(
                value = state.captchaValidateInput,
                onValueChange = viewModel::updateCaptchaValidate,
                label = { Text(text = "行为验证码校验凭据 (validate)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = viewModel::submitPasswordLogin,
            enabled = !state.isSubmittingLogin && state.accountInput.isNotBlank() && state.passwordInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "安全登录并加密保存")
        }
    }
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
