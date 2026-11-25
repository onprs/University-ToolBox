package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLinearProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxSectionHeader
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScannerView
import io.github.onprs.universitytoolbox.core.ui.ErrorStateView
import io.github.onprs.universitytoolbox.core.ui.LoadingStateView
import io.github.onprs.universitytoolbox.core.ui.PermissionGuideCard
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.R
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActiveDetail
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSignType
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.AccountSignReport
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingSignExecutionUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingSignExecutionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingSignExecutionScreen(
    viewModel: ChaoxingSignExecutionViewModel,
    onBack: () -> Unit,
    courseName: String = "",
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isScannerSheetOpen) {
        QrScannerBottomSheet(
            onBarcodeScanned = viewModel::onBarcodeScanned,
            onDismiss = { viewModel.setScannerSheetOpen(false) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.chaoxing_sign_execution_title)) },
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
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when {
            state.isLoadingDetail -> LoadingStateView(modifier = containerModifier)
            state.detail == null -> ErrorStateView(
                title = "无法加载签到详情",
                message = state.loadError,
                onRetry = viewModel::loadDetailAndAccounts,
                modifier = containerModifier,
            )

            else -> SignExecutionBody(
                state = state,
                detail = state.detail!!,
                courseName = courseName,
                viewModel = viewModel,
                modifier = containerModifier,
            )
        }
    }
}

@Composable
private fun SignExecutionBody(
    state: ChaoxingSignExecutionUiState,
    detail: ChaoxingActiveDetail,
    courseName: String,
    viewModel: ChaoxingSignExecutionViewModel,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(ToolboxSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
    ) {
        item { SignDetailHeaderCard(detail = detail) }

        item {
            StrategyInputsSection(
                state = state,
                detail = detail,
                viewModel = viewModel,
            )
        }

        if (state.pendingCaptchaId != null) {
            item {
                CaptchaPendingCard(
                    message = state.pendingCaptchaMessage.orEmpty(),
                    validateInput = state.captchaValidateInput,
                    onValidateChange = viewModel::updateCaptchaValidate,
                )
            }
        }

        if (!state.inputHintMessage.isNullOrBlank()) {
            item {
                Surface(
                    color = ToolboxTheme.extendedColors.warningContainer,
                    shape = RoundedCornerShape(ToolboxRadius.chip),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.inputHintMessage,
                        color = ToolboxTheme.extendedColors.onWarningContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(ToolboxSpacing.small),
                    )
                }
            }
        }

        item {
            MultiAccountChecklistCard(
                accounts = state.availableAccounts,
                selectedIds = state.selectedAccountIds,
                maxSubAccountsQuota = state.maxSubAccountsQuota,
                onToggle = viewModel::toggleAccountSelection,
                onSelectAll = viewModel::selectAllEnabledAccounts,
            )
        }

        item {
            if (state.isExecuting) {
                ToolboxLinearProgressBar(modifier = Modifier.padding(bottom = ToolboxSpacing.small))
            }
            Button(
                onClick = { viewModel.executeSign(courseName = courseName) },
                enabled = !state.isExecuting && state.selectedAccountIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.selectedAccountIds.size > 1) {
                        "提交协同签到（${state.selectedAccountIds.size} 个账号）"
                    } else {
                        "提交签到"
                    },
                )
            }
        }

        state.batchSummary?.let { summary ->
            item { ToolboxSectionHeader(title = "执行结果回执") }
            items(summary.reports, key = { it.accountId }) { report ->
                BatchReportRow(report = report)
            }
        }
    }
}

@Composable
private fun SignDetailHeaderCard(detail: ChaoxingActiveDetail) {
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
                Text(text = detail.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ToolboxStatusBadge(
                    text = detail.signType.displayLabel(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            val tags = buildList {
                if (detail.requiresPhoto) add("需现场照片")
                if (detail.requiresLocation) add("限定位置")
                if (detail.dynamicQr) add("动态刷新二维码(${detail.qrRefreshSeconds}s)")
                if (detail.requiresCaptcha) add("触发行为验证")
                if (detail.preventCheat) add("开启防作弊")
            }
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StrategyInputsSection(
    state: ChaoxingSignExecutionUiState,
    detail: ChaoxingActiveDetail,
    viewModel: ChaoxingSignExecutionViewModel,
) {
    when {
        detail.requiresPhoto -> PhotoSignInputSection(state = state, viewModel = viewModel)
        detail.signType == ChaoxingSignType.QR_CODE -> QrSignInputSection(state = state, viewModel = viewModel)
        detail.signType == ChaoxingSignType.LOCATION || detail.requiresLocation ->
            LocationSignInputSection(state = state, detail = detail, viewModel = viewModel)

        detail.signType == ChaoxingSignType.GESTURE || detail.signType == ChaoxingSignType.SIGN_CODE ->
            CodeGestureInputSection(state = state, detail = detail, viewModel = viewModel)

        else -> OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (detail.isSignOut) "普通签退无需额外参数，可直接提交。" else "普通签到无需额外参数，点击下方即可提交。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(ToolboxSpacing.medium),
            )
        }
    }
}

@Composable
private fun QrSignInputSection(state: ChaoxingSignExecutionUiState, viewModel: ChaoxingSignExecutionViewModel) {
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.scanQrFromImageUri(uri)
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(text = "二维码签到参数", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
                FilledTonalButton(
                    onClick = { viewModel.setScannerSheetOpen(true) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text(text = " 相机扫码")
                }
                FilledTonalButton(
                    onClick = {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Text(text = " 相册识图")
                }
            }
            OutlinedTextField(
                value = state.qrRawInput,
                onValueChange = viewModel::updateQrRawInput,
                label = { Text(text = "二维码链接或 SIGNIN 参数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.parsedQrEnc?.let { enc ->
                Text(
                    text = "已解析动态校验串：${enc.take(12)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PhotoSignInputSection(state: ChaoxingSignExecutionUiState, viewModel: ChaoxingSignExecutionViewModel) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                viewModel.setPhoto(bytes = bytes, fileName = "sign_photo.jpg")
            }
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(text = "现场照片上传", style = MaterialTheme.typography.titleSmall)
            val bitmap = remember(state.photo) {
                state.photo?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "待上传照片",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(ToolboxRadius.chip)),
                )
            }
            FilledTonalButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null)
                Text(text = if (bitmap == null) " 选择签到照片" else " 更换照片")
            }
        }
    }
}

@Composable
private fun LocationSignInputSection(
    state: ChaoxingSignExecutionUiState,
    detail: ChaoxingActiveDetail,
    viewModel: ChaoxingSignExecutionViewModel,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(text = "位置坐标", style = MaterialTheme.typography.titleSmall)
            detail.locationText?.let { loc ->
                val radiusText = detail.locationRangeMeters?.let { "（允许半径 ${it}m）" }.orEmpty()
                Text(
                    text = "要求地点：$loc$radiusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = viewModel::applyTargetCourseLocation) {
                Icon(imageVector = Icons.Outlined.MyLocation, contentDescription = null)
                Text(text = " 填入预设签到坐标")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
                OutlinedTextField(
                    value = state.latitudeInput,
                    onValueChange = { viewModel.updateLocation(it, state.longitudeInput, state.addressInput) },
                    label = { Text(text = "纬度") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.longitudeInput,
                    onValueChange = { viewModel.updateLocation(state.latitudeInput, it, state.addressInput) },
                    label = { Text(text = "经度") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.addressInput,
                onValueChange = { viewModel.updateLocation(state.latitudeInput, state.longitudeInput, it) },
                label = { Text(text = "位置描述") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CodeGestureInputSection(
    state: ChaoxingSignExecutionUiState,
    detail: ChaoxingActiveDetail,
    viewModel: ChaoxingSignExecutionViewModel,
) {
    val label = if (detail.signType == ChaoxingSignType.GESTURE) "手势序号（如 1235789）" else "数字签到码"
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(text = detail.signType.displayLabel(), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = state.signCodeInput,
                onValueChange = viewModel::updateSignCode,
                label = { Text(text = label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CaptchaPendingCard(message: String, validateInput: String, onValidateChange: (String) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(
                text = "需要完成行为验证码",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(text = message, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = validateInput,
                onValueChange = onValidateChange,
                label = { Text(text = "验证码校验凭据 (validate)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MultiAccountChecklistCard(
    accounts: List<ChaoxingAccountEntity>,
    selectedIds: Set<String>,
    maxSubAccountsQuota: Int,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
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
                    text = "参与执行账号（子账号上限 $maxSubAccountsQuota）",
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onSelectAll) { Text(text = "全选") }
            }
            accounts.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = account.accountId in selectedIds,
                            onCheckedChange = { onToggle(account.accountId) },
                        )
                        Text(
                            text = "${account.displayName}（${account.maskedAccount}）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    ToolboxStatusBadge(
                        text = if (account.isPrimary) "主账号" else "子账号",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchReportRow(report: AccountSignReport) {
    val isGood = report.result is SignExecutionResult.Success || report.result is SignExecutionResult.AlreadySigned
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = report.displayName.ifBlank { report.maskedAccount },
                    style = MaterialTheme.typography.titleSmall,
                )
                ToolboxStatusBadge(
                    text = if (isGood) "已完成" else "需关注",
                    containerColor = if (isGood) {
                        ToolboxTheme.extendedColors.successContainer
                    } else {
                        ToolboxTheme.extendedColors.warningContainer
                    },
                    contentColor = if (isGood) {
                        ToolboxTheme.extendedColors.onSuccessContainer
                    } else {
                        ToolboxTheme.extendedColors.onWarningContainer
                    },
                )
            }
            Text(text = report.result.displayStatusText(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScannerBottomSheet(onBarcodeScanned: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
        ) {
            Text(text = "扫描课堂签到二维码", style = MaterialTheme.typography.titleMedium)
            if (hasCameraPermission) {
                BarcodeScannerView(
                    onBarcodeDetected = { result -> onBarcodeScanned(result.rawValue) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(ToolboxRadius.card)),
                )
            } else {
                PermissionGuideCard(
                    title = "需要相机权限",
                    rationale = "用于实时扫描课堂投影展示的超星签到二维码。",
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}
