package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingPresetLocationEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActiveDetail
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSignType
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.AccountSignReport
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.CoordinateConverter
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.GeoPoint
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
    val context = LocalContext.current
    var pendingJumpInMap by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.useCurrentDeviceLocation(jumpInMap = pendingJumpInMap)
        }
    }
    val requestDeviceLocation: (Boolean) -> Unit = { jumpInMap ->
        pendingJumpInMap = jumpInMap
        if (hasAnyLocationPermission(context)) {
            viewModel.useCurrentDeviceLocation(jumpInMap = jumpInMap)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    SignExecutionSheetsHost(
        state = state,
        viewModel = viewModel,
        onRequestDeviceLocationInMap = { requestDeviceLocation(true) },
    )

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
                onRequestCurrentLocation = { requestDeviceLocation(false) },
                modifier = containerModifier,
            )
        }
    }
}

private fun hasAnyLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun SignExecutionSheetsHost(
    state: ChaoxingSignExecutionUiState,
    viewModel: ChaoxingSignExecutionViewModel,
    onRequestDeviceLocationInMap: () -> Unit,
) {
    if (state.isScannerSheetOpen) {
        QrScannerBottomSheet(
            onBarcodeScanned = viewModel::onBarcodeScanned,
            onDismiss = { viewModel.setScannerSheetOpen(false) },
        )
    }

    if (state.isPresetSheetOpen) {
        PresetLocationsBottomSheet(
            state = state,
            onApplyActivityTarget = viewModel::applyTargetCourseLocation,
            onApplyPreset = viewModel::applyPresetLocation,
            onDeletePreset = viewModel::deletePresetLocation,
            onSaveCurrentAsPreset = { title, lat, lng, addr ->
                viewModel.savePresetLocation(title, lat, lng, addr)
                viewModel.setPresetSheetOpen(false)
            },
            onOpenMapPicker = {
                viewModel.setPresetSheetOpen(false)
                viewModel.setMapPickerOpen(true)
            },
            onDismiss = { viewModel.setPresetSheetOpen(false) },
        )
    }

    if (state.isDeviceInfoDialogOpen) {
        ChaoxingDeviceCodeInfoDialog(
            hideBadge = state.hideDeviceCodeBadge,
            onToggleHideBadge = viewModel::setHideDeviceCodeBadge,
            onDismiss = { viewModel.setDeviceInfoDialogOpen(false) },
        )
    }

    if (state.isMapPickerOpen) {
        SignExecutionMapPickerLauncher(
            state = state,
            viewModel = viewModel,
            onRequestDeviceLocationInMap = onRequestDeviceLocationInMap,
        )
    }
}

@Composable
private fun SignExecutionMapPickerLauncher(
    state: ChaoxingSignExecutionUiState,
    viewModel: ChaoxingSignExecutionViewModel,
    onRequestDeviceLocationInMap: () -> Unit,
) {
    val currentLat = state.latitudeInput.toDoubleOrNull()
    val currentLng = state.longitudeInput.toDoubleOrNull()
    val initialPoint = if (CoordinateConverter.isValidTargetCoordinate(currentLat, currentLng)) {
        GeoPoint(currentLat!!, currentLng!!)
    } else {
        null
    }
    val detail = state.detail
    val targetPoint = if (detail != null && detail.hasTargetCoordinates) {
        GeoPoint(detail.targetLatitude!!, detail.targetLongitude!!)
    } else {
        null
    }
    ChaoxingMapPickerDialog(
        initialBd09Point = initialPoint,
        targetBd09Point = targetPoint,
        targetRangeMeters = detail?.locationRangeMeters,
        requiredLocationHint = detail?.locationText,
        initialAddress = state.addressInput,
        searchQuery = state.mapSearchQuery,
        isSearching = state.isSearchingMapPlaces,
        searchResults = state.mapSearchResults,
        isLocatingCurrent = state.isLocatingCurrent,
        externalJumpPlace = state.mapExternalJumpPlace,
        reverseGeocodedAddress = state.mapReverseGeocodedAddress,
        onSearchQueryChange = viewModel::updateMapSearchQuery,
        onExecuteSearch = viewModel::searchMapPlaces,
        onRequestCurrentLocation = onRequestDeviceLocationInMap,
        onReverseGeocodeCenter = viewModel::reverseGeocodeMapCenter,
        onConfirmSelection = viewModel::confirmMapSelection,
        onSaveAsPresetAndSelect = { title, point, addr ->
            viewModel.savePresetLocation(title, point.latitude, point.longitude, addr)
        },
        onDismiss = { viewModel.setMapPickerOpen(false) },
    )
}

@Composable
private fun SignExecutionBody(
    state: ChaoxingSignExecutionUiState,
    detail: ChaoxingActiveDetail,
    courseName: String,
    viewModel: ChaoxingSignExecutionViewModel,
    onRequestCurrentLocation: () -> Unit,
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
                onRequestCurrentLocation = onRequestCurrentLocation,
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
                hideDeviceCodeBadge = state.hideDeviceCodeBadge,
                onToggle = viewModel::toggleAccountSelection,
                onSelectAll = viewModel::selectAllEnabledAccounts,
                onShowDeviceInfo = { viewModel.setDeviceInfoDialogOpen(true) },
            )
        }

        item {
            SubmitSignButtonSection(
                isExecuting = state.isExecuting,
                selectedCount = state.selectedAccountIds.size,
                onExecute = { viewModel.executeSign(courseName = courseName) },
            )
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
private fun SubmitSignButtonSection(isExecuting: Boolean, selectedCount: Int, onExecute: () -> Unit) {
    if (isExecuting) {
        ToolboxLinearProgressBar(modifier = Modifier.padding(bottom = ToolboxSpacing.small))
    }
    Button(
        onClick = onExecute,
        enabled = !isExecuting && selectedCount > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (selectedCount > 1) {
                "提交协同签到（$selectedCount 个账号）"
            } else {
                "提交签到"
            },
        )
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
    onRequestCurrentLocation: () -> Unit,
) {
    val needsPhoto = detail.requiresPhoto
    val needsQr = detail.signType == ChaoxingSignType.QR_CODE
    val needsLocation = detail.signType == ChaoxingSignType.LOCATION || detail.requiresLocation
    val needsCode = detail.signType == ChaoxingSignType.GESTURE || detail.signType == ChaoxingSignType.SIGN_CODE
    val hasCustomStrategy = listOf(needsPhoto, needsQr, needsLocation, needsCode).any { it }

    if (!hasCustomStrategy) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (detail.isSignOut) "普通签退无需额外参数，可直接提交。" else "普通签到无需额外参数，点击下方即可提交。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(ToolboxSpacing.medium),
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium)) {
        if (needsPhoto) {
            PhotoSignInputSection(state = state, viewModel = viewModel)
        }
        if (needsQr) {
            QrSignInputSection(state = state, viewModel = viewModel)
        }
        if (needsLocation) {
            LocationSignInputSection(
                state = state,
                detail = detail,
                viewModel = viewModel,
                onRequestCurrentLocation = onRequestCurrentLocation,
            )
        }
        if (needsCode) {
            CodeGestureInputSection(state = state, detail = detail, viewModel = viewModel)
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
    onRequestCurrentLocation: () -> Unit,
) {
    var showQuickSaveDialog by remember { mutableStateOf(false) }
    val currentLat = state.latitudeInput.toDoubleOrNull()
    val currentLng = state.longitudeInput.toDoubleOrNull()
    val canSaveCurrent = CoordinateConverter.isValidTargetCoordinate(currentLat, currentLng)

    if (showQuickSaveDialog && canSaveCurrent) {
        SavePresetNameDialog(
            initialTitle = state.addressInput.trim().ifBlank { detail.locationText.orEmpty() },
            onConfirm = { title ->
                showQuickSaveDialog = false
                viewModel.savePresetLocation(title, currentLat!!, currentLng!!, state.addressInput)
            },
            onDismiss = { showQuickSaveDialog = false },
        )
    }

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

            LocationQuickActionRow(
                presetCount = state.presetLocations.size,
                isLocating = state.isLocatingCurrent,
                canSaveCurrent = canSaveCurrent,
                onOpenMapPicker = { viewModel.setMapPickerOpen(true) },
                onOpenPresets = { viewModel.setPresetSheetOpen(true) },
                onLocateCurrent = onRequestCurrentLocation,
                onSaveCurrent = { showQuickSaveDialog = true },
            )

            if (state.isLocatingCurrent) {
                ToolboxLinearProgressBar()
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
private fun LocationQuickActionRow(
    presetCount: Int,
    isLocating: Boolean,
    canSaveCurrent: Boolean,
    onOpenMapPicker: () -> Unit,
    onOpenPresets: () -> Unit,
    onLocateCurrent: () -> Unit,
    onSaveCurrent: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            FilledTonalButton(onClick = onOpenMapPicker, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = " 地图选点")
            }
            FilledTonalButton(onClick = onOpenPresets, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Outlined.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp))
                val suffix = if (presetCount > 0) " ($presetCount)" else ""
                Text(text = " 预设签到坐标$suffix")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            OutlinedButton(
                onClick = onLocateCurrent,
                enabled = !isLocating,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = " 当前定位")
            }
            if (canSaveCurrent) {
                OutlinedButton(onClick = onSaveCurrent, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = " 保存当前为预设")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetLocationsBottomSheet(
    state: ChaoxingSignExecutionUiState,
    onApplyActivityTarget: () -> Unit,
    onApplyPreset: (ChaoxingPresetLocationEntity) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onSaveCurrentAsPreset: (title: String, latitude: Double, longitude: Double, address: String) -> Unit,
    onOpenMapPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    val currentLat = state.latitudeInput.toDoubleOrNull()
    val currentLng = state.longitudeInput.toDoubleOrNull()
    val canSaveCurrent = CoordinateConverter.isValidTargetCoordinate(currentLat, currentLng)
    val detail = state.detail

    if (showSaveDialog && canSaveCurrent) {
        SavePresetNameDialog(
            initialTitle = state.addressInput.trim().ifBlank { detail?.locationText.orEmpty() },
            onConfirm = { title ->
                showSaveDialog = false
                onSaveCurrentAsPreset(title, currentLat!!, currentLng!!, state.addressInput)
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        PresetSheetBody(
            detail = detail,
            presets = state.presetLocations,
            canSaveCurrent = canSaveCurrent,
            onApplyActivityTarget = onApplyActivityTarget,
            onApplyPreset = onApplyPreset,
            onDeletePreset = onDeletePreset,
            onRequestSaveCurrent = { showSaveDialog = true },
            onOpenMapPicker = onOpenMapPicker,
        )
    }
}

@Composable
private fun PresetSheetBody(
    detail: ChaoxingActiveDetail?,
    presets: List<ChaoxingPresetLocationEntity>,
    canSaveCurrent: Boolean,
    onApplyActivityTarget: () -> Unit,
    onApplyPreset: (ChaoxingPresetLocationEntity) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onRequestSaveCurrent: () -> Unit,
    onOpenMapPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "预设签到坐标", style = MaterialTheme.typography.titleMedium)
            if (canSaveCurrent) {
                TextButton(onClick = onRequestSaveCurrent) { Text(text = "将当前坐标存为预设") }
            }
        }

        if (detail != null && detail.hasTargetCoordinates) {
            ActivityTargetPresetCard(detail = detail, onApply = onApplyActivityTarget)
        }

        if (presets.isEmpty()) {
            EmptyPresetsGuideCard(onOpenMapPicker = onOpenMapPicker)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            ) {
                items(presets, key = { it.id }) { preset ->
                    SavedPresetLocationCard(
                        preset = preset,
                        onSelect = { onApplyPreset(preset) },
                        onDelete = { onDeletePreset(preset.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityTargetPresetCard(detail: ChaoxingActiveDetail, onApply: () -> Unit) {
    ElevatedCard(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "本次签到指定地点", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = detail.locationText.orEmpty().ifBlank {
                        "${CoordinateConverter.formatCoordinate(detail.targetLatitude!!)}, " +
                            CoordinateConverter.formatCoordinate(detail.targetLongitude!!)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ToolboxStatusBadge(
                text = "活动坐标",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SavedPresetLocationCard(preset: ChaoxingPresetLocationEntity, onSelect: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = preset.address.takeIf { it.isNotBlank() && it != preset.title }
                    ?: "${CoordinateConverter.formatCoordinate(preset.latitude)}, " +
                    CoordinateConverter.formatCoordinate(preset.longitude)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = "删除预设")
            }
        }
    }
}

@Composable
private fun EmptyPresetsGuideCard(onOpenMapPicker: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(
                text = "暂无已保存的预设签到地点",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "可通过地图手动选择教学楼、操场等常用上课位置并存为预设，后续签到即可一键填入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenMapPicker, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = " 打开地图选点并添加预设")
            }
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
    hideDeviceCodeBadge: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onShowDeviceInfo: () -> Unit,
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = account.accountId in selectedIds,
                            onCheckedChange = { onToggle(account.accountId) },
                        )
                        Text(
                            text = "${account.displayName}（${account.maskedAccount}）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!hideDeviceCodeBadge) {
                            DeviceCodeStatusIconButton(
                                isUnified = account.isUnifiedDeviceCode,
                                onClick = onShowDeviceInfo,
                            )
                        }
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
