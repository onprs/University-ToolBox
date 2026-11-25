package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_LATITUDE_ABS = 90.0
private const val MAX_LONGITUDE_ABS = 180.0

/**
 * 拍照签到图片流式降采样与压缩处理器：
 * - 通过 `inJustDecodeBounds` + `inSampleSize` 杜绝超高像素原图解码引发的 OOM；
 * - 将上传图片限制在合理边长并转为标准 JPEG，兼顾课堂照片清晰度与上传稳定性。
 */
internal object SignPhotoImageProcessor {
    const val MAX_UPLOAD_DIMENSION_PX = 1920
    const val MAX_PREVIEW_DIMENSION_PX = 360
    private const val JPEG_COMPRESS_QUALITY = 85
    private const val SAMPLE_SCALE_STEP = 2

    fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDimensionPx: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || maxDimensionPx <= 0) {
            return 1
        }
        val largestSide = maxOf(srcWidth, srcHeight)
        var sampleSize = 1
        while (largestSide / (sampleSize * SAMPLE_SCALE_STEP) >= maxDimensionPx) {
            sampleSize *= SAMPLE_SCALE_STEP
        }
        return sampleSize
    }

    fun decodeAndCompressFromUri(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = MAX_UPLOAD_DIMENSION_PX,
    ): ByteArray? = runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(rawWidth, rawHeight, maxDimensionPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val scaled = scaleDownIfNeeded(sampled, maxDimensionPx)
        val output = ByteArrayOutputStream()
        val compressedOk = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESS_QUALITY, output)
        if (scaled !== sampled) {
            scaled.recycle()
        }
        sampled.recycle()
        output.toByteArray().takeIf { compressedOk && it.isNotEmpty() }
    }.getOrNull()

    fun decodePreviewBitmap(bytes: ByteArray, maxDimensionPx: Int = MAX_PREVIEW_DIMENSION_PX): Bitmap? = runCatching {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }.getOrNull()

    private fun scaleDownIfNeeded(source: Bitmap, maxDimensionPx: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimensionPx || maxDimensionPx <= 0) {
            return source
        }
        val ratio = maxDimensionPx.toFloat() / longest.toFloat()
        val targetWidth = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}

internal fun validateCoordinateFieldError(rawInput: String, maxAbs: Double, axisLabel: String): String? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null
    val parsed = trimmed.toDoubleOrNull() ?: return "请输入有效的${axisLabel}数字"
    return if (!parsed.isFinite() || abs(parsed) > maxAbs) {
        "${axisLabel}范围应为 -${maxAbs.toInt()} ~ ${maxAbs.toInt()}"
    } else {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingSignExecutionScreen(
    viewModel: ChaoxingSignExecutionViewModel,
    onBack: () -> Unit,
    courseName: String = "",
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permController = rememberLocationPermissionController(context = context, viewModel = viewModel)

    val loadedDetail = state.detail
    LaunchedEffect(loadedDetail?.activeId) {
        if (shouldAutoPrefillDeviceLocation(loadedDetail, state.latitudeInput, context)) {
            viewModel.useCurrentDeviceLocation(jumpInMap = false)
        }
    }

    SignExecutionModalHost(state = state, viewModel = viewModel)

    Box(modifier = Modifier.fillMaxSize()) {
        SignExecutionScaffoldLayer(
            state = state,
            courseName = courseName,
            viewModel = viewModel,
            showLocationSettingsGuide = permController.showSettingsGuide,
            onRequestCurrentLocation = { permController.requestLocation(false) },
            onOpenAppSettings = { openAppDetailSettings(context) },
            onBack = onBack,
        )

        if (state.isMapPickerOpen) {
            SignExecutionMapPickerLauncher(
                state = state,
                viewModel = viewModel,
                isLocationPermanentlyDenied = permController.showSettingsGuide,
                onRequestDeviceLocationInMap = { permController.requestLocation(true) },
                onOpenAppSettings = { openAppDetailSettings(context) },
            )
        }
    }
}

private class LocationPermissionController(
    val showSettingsGuide: Boolean,
    val requestLocation: (jumpInMap: Boolean) -> Unit,
)

@Composable
private fun rememberLocationPermissionController(
    context: Context,
    viewModel: ChaoxingSignExecutionViewModel,
): LocationPermissionController {
    val focusManager = LocalFocusManager.current
    var pendingJumpInMap by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(hasAnyLocationPermission(context)) }
    var isLocationPermanentlyDenied by rememberSaveable { mutableStateOf(false) }

    ObserveResumePermissionSync(
        onResume = {
            val grantedNow = hasAnyLocationPermission(context)
            hasLocationPermission = grantedNow
            if (grantedNow) {
                isLocationPermanentlyDenied = false
            }
        },
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            isLocationPermanentlyDenied = false
            viewModel.useCurrentDeviceLocation(jumpInMap = pendingJumpInMap)
        } else {
            val permanentlyDenied = isLocationPermissionPermanentlyDenied(context)
            isLocationPermanentlyDenied = permanentlyDenied
            viewModel.notifyMapPermissionDenied(isPermanentlyDenied = permanentlyDenied)
        }
    }

    return LocationPermissionController(
        showSettingsGuide = isLocationPermanentlyDenied && !hasLocationPermission,
        requestLocation = { jumpInMap ->
            focusManager.clearFocus()
            pendingJumpInMap = jumpInMap
            if (hasAnyLocationPermission(context)) {
                hasLocationPermission = true
                isLocationPermanentlyDenied = false
                viewModel.useCurrentDeviceLocation(jumpInMap = jumpInMap)
            } else {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        },
    )
}

@Composable
private fun SignExecutionScaffoldLayer(
    state: ChaoxingSignExecutionUiState,
    courseName: String,
    viewModel: ChaoxingSignExecutionViewModel,
    showLocationSettingsGuide: Boolean,
    onRequestCurrentLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { SignExecutionTopBar(onBack = onBack) }) { innerPadding ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()

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
                detail = state.detail,
                courseName = courseName,
                viewModel = viewModel,
                showLocationSettingsGuide = showLocationSettingsGuide,
                onRequestCurrentLocation = onRequestCurrentLocation,
                onOpenAppSettings = onOpenAppSettings,
                modifier = containerModifier,
            )
        }
    }
}

@Composable
private fun ObserveResumePermissionSync(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignExecutionTopBar(onBack: () -> Unit) {
    val focusManager = LocalFocusManager.current
    TopAppBar(
        title = { Text(text = stringResource(R.string.chaoxing_sign_execution_title)) },
        navigationIcon = {
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onBack()
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.chaoxing_back),
                )
            }
        },
    )
}

private fun shouldAutoPrefillDeviceLocation(
    detail: ChaoxingActiveDetail?,
    latitudeInput: String,
    context: Context,
): Boolean {
    if (detail == null || detail.hasTargetCoordinates || latitudeInput.isNotBlank()) return false
    val isLocationSign = detail.signType == ChaoxingSignType.LOCATION || detail.requiresLocation
    return isLocationSign && hasAnyLocationPermission(context)
}

private fun hasAnyLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun isLocationPermissionPermanentlyDenied(context: Context): Boolean {
    if (hasAnyLocationPermission(context)) return false
    val activity = context.findActivity() ?: return false
    val showFine = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    val showCoarse = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    return !showFine && !showCoarse
}

private fun isCameraPermissionPermanentlyDenied(context: Context): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        return false
    }
    val activity = context.findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun openAppDetailSettings(context: Context) {
    runCatching {
        val detailIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(detailIntent)
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@Composable
private fun SignExecutionModalHost(state: ChaoxingSignExecutionUiState, viewModel: ChaoxingSignExecutionViewModel) {
    val focusManager = LocalFocusManager.current
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
                focusManager.clearFocus()
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
}

@Composable
private fun SignExecutionMapPickerLauncher(
    state: ChaoxingSignExecutionUiState,
    viewModel: ChaoxingSignExecutionViewModel,
    isLocationPermanentlyDenied: Boolean,
    onRequestDeviceLocationInMap: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val currentLat = state.latitudeInput.trim().toDoubleOrNull()
    val currentLng = state.longitudeInput.trim().toDoubleOrNull()
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
    val fallbackPoint = viewModel.cachedDeviceLocationPoint
        ?: state.presetLocations.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
    ChaoxingMapPickerDialog(
        initialBd09Point = initialPoint,
        targetBd09Point = targetPoint,
        fallbackBd09Point = fallbackPoint,
        targetRangeMeters = detail?.locationRangeMeters,
        requiredLocationHint = detail?.locationText,
        initialAddress = state.addressInput,
        searchQuery = state.mapSearchQuery,
        isSearching = state.isSearchingMapPlaces,
        hasCompletedSearch = state.hasCompletedMapSearch,
        searchResults = state.mapSearchResults,
        isLocatingCurrent = state.isLocatingCurrent,
        jumpCommand = state.mapJumpCommand,
        reverseGeocodedAddress = state.mapReverseGeocodedAddress,
        statusMessage = state.mapStatusMessage,
        onSearchQueryChange = viewModel::updateMapSearchQuery,
        onExecuteSearch = viewModel::searchMapPlaces,
        onRequestCurrentLocation = onRequestDeviceLocationInMap,
        onReverseGeocodeCenter = viewModel::reverseGeocodeMapCenter,
        onConfirmSelection = viewModel::confirmMapSelection,
        onSaveAsPresetAndSelect = { title, point, addr ->
            viewModel.savePresetLocation(title, point.latitude, point.longitude, addr)
        },
        onDismiss = { viewModel.setMapPickerOpen(false) },
        onDismissSearchResults = viewModel::dismissMapSearchResults,
        isLocationPermanentlyDenied = isLocationPermanentlyDenied,
        onOpenAppSettings = onOpenAppSettings,
    )
}

@Composable
private fun SignExecutionBody(
    state: ChaoxingSignExecutionUiState,
    detail: ChaoxingActiveDetail,
    courseName: String,
    viewModel: ChaoxingSignExecutionViewModel,
    showLocationSettingsGuide: Boolean,
    onRequestCurrentLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
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
                showLocationSettingsGuide = showLocationSettingsGuide,
                onRequestCurrentLocation = onRequestCurrentLocation,
                onOpenAppSettings = onOpenAppSettings,
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
                InputWarningHintBanner(message = state.inputHintMessage)
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
                onExecute = {
                    focusManager.clearFocus()
                    viewModel.executeSign(courseName = courseName)
                },
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
private fun InputWarningHintBanner(message: String) {
    Surface(
        color = ToolboxTheme.extendedColors.warningContainer,
        shape = RoundedCornerShape(ToolboxRadius.chip),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = ToolboxTheme.extendedColors.onWarningContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(ToolboxSpacing.small),
        )
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
    showLocationSettingsGuide: Boolean,
    onRequestCurrentLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
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
                showLocationSettingsGuide = showLocationSettingsGuide,
                onRequestCurrentLocation = onRequestCurrentLocation,
                onOpenAppSettings = onOpenAppSettings,
            )
        }
        if (needsCode) {
            CodeGestureInputSection(state = state, detail = detail, viewModel = viewModel)
        }
    }
}

@Composable
private fun QrSignInputSection(state: ChaoxingSignExecutionUiState, viewModel: ChaoxingSignExecutionViewModel) {
    val focusManager = LocalFocusManager.current
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
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.setScannerSheetOpen(true)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text(text = " 相机扫码")
                }
                FilledTonalButton(
                    onClick = {
                        focusManager.clearFocus()
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
                label = { Text(text = "二维码内容或签到链接") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.parsedQrEnc.isNullOrBlank()) {
                Text(
                    text = "已识别签到二维码校验信息",
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
    val scope = rememberCoroutineScope()
    var isProcessingPhoto by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessingPhoto = true
                val compressed = withContext(Dispatchers.IO) {
                    SignPhotoImageProcessor.decodeAndCompressFromUri(context, uri)
                }
                isProcessingPhoto = false
                if (compressed != null) {
                    viewModel.setPhoto(bytes = compressed, fileName = "sign_photo.jpg")
                } else {
                    viewModel.notifyPhotoLoadFailed()
                }
            }
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(text = "现场照片上传", style = MaterialTheme.typography.titleSmall)
            if (isProcessingPhoto) {
                ToolboxLinearProgressBar()
            }
            val bitmap = remember(state.photo) {
                state.photo?.bytes?.let(SignPhotoImageProcessor::decodePreviewBitmap)
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
                enabled = !isProcessingPhoto,
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
    showLocationSettingsGuide: Boolean,
    onRequestCurrentLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var showQuickSaveDialog by rememberSaveable { mutableStateOf(false) }
    val currentLat = state.latitudeInput.trim().toDoubleOrNull()
    val currentLng = state.longitudeInput.trim().toDoubleOrNull()
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
                onOpenMapPicker = {
                    focusManager.clearFocus()
                    viewModel.setMapPickerOpen(true)
                },
                onOpenPresets = {
                    focusManager.clearFocus()
                    viewModel.setPresetSheetOpen(true)
                },
                onLocateCurrent = onRequestCurrentLocation,
                onSaveCurrent = {
                    focusManager.clearFocus()
                    showQuickSaveDialog = true
                },
            )

            if (state.isLocatingCurrent) {
                ToolboxLinearProgressBar()
            }

            if (showLocationSettingsGuide) {
                PermissionGuideCard(
                    title = "定位权限已关闭",
                    rationale = "如需获取当前设备实时坐标，请前往系统设置开启位置信息权限；也可直接使用地图选点或预设坐标。",
                    isPermanentlyDenied = true,
                    onRequestPermission = onOpenAppSettings,
                )
            }

            LocationCoordinateFields(
                latitudeInput = state.latitudeInput,
                longitudeInput = state.longitudeInput,
                addressInput = state.addressInput,
                onUpdateLocation = viewModel::updateLocation,
            )
        }
    }
}

@Composable
private fun LocationCoordinateFields(
    latitudeInput: String,
    longitudeInput: String,
    addressInput: String,
    onUpdateLocation: (String, String, String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val latError = validateCoordinateFieldError(latitudeInput, MAX_LATITUDE_ABS, "纬度")
    val lngError = validateCoordinateFieldError(longitudeInput, MAX_LONGITUDE_ABS, "经度")

    Row(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        OutlinedTextField(
            value = latitudeInput,
            onValueChange = { onUpdateLocation(it, longitudeInput, addressInput) },
            label = { Text(text = "纬度") },
            isError = latError != null,
            supportingText = latError?.let { msg -> { Text(text = msg) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = longitudeInput,
            onValueChange = { onUpdateLocation(latitudeInput, it, addressInput) },
            label = { Text(text = "经度") },
            isError = lngError != null,
            supportingText = lngError?.let { msg -> { Text(text = msg) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = addressInput,
        onValueChange = { onUpdateLocation(latitudeInput, longitudeInput, it) },
        label = { Text(text = "位置描述") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
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
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    val currentLat = state.latitudeInput.trim().toDoubleOrNull()
    val currentLng = state.longitudeInput.trim().toDoubleOrNull()
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
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(
                start = ToolboxSpacing.medium,
                end = ToolboxSpacing.medium,
                top = ToolboxSpacing.small,
                bottom = ToolboxSpacing.medium,
            ),
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
    val focusManager = LocalFocusManager.current
    val isGesture = detail.signType == ChaoxingSignType.GESTURE
    val label = if (isGesture) "手势序号（如 1235789）" else "数字签到码"
    val keyboardType = if (isGesture) KeyboardType.Number else KeyboardType.Text

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
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CaptchaPendingCard(message: String, validateInput: String, onValidateChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(
                text = "需要完成安全验证",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(text = message, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = validateInput,
                onValueChange = onValidateChange,
                label = { Text(text = "安全验证凭据") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
    var isCameraPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var hasHandledScan by remember { mutableStateOf(false) }

    ObserveResumePermissionSync(
        onResume = {
            val grantedNow = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            hasCameraPermission = grantedNow
            if (grantedNow) {
                isCameraPermanentlyDenied = false
            }
        },
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        isCameraPermanentlyDenied = if (granted) false else isCameraPermissionPermanentlyDenied(context)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
        ) {
            Text(text = "扫描课堂签到二维码", style = MaterialTheme.typography.titleMedium)
            if (hasCameraPermission) {
                BarcodeScannerView(
                    onBarcodeDetected = { result ->
                        if (!hasHandledScan && result.rawValue.isNotBlank()) {
                            hasHandledScan = true
                            onBarcodeScanned(result.rawValue)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(ToolboxRadius.card)),
                )
            } else {
                val rationaleText = if (isCameraPermanentlyDenied) {
                    "相机权限已被关闭，请前往系统设置开启相机权限以扫描课堂签到二维码。"
                } else {
                    "用于实时扫描课堂投影展示的超星签到二维码。"
                }
                PermissionGuideCard(
                    title = "需要相机权限",
                    rationale = rationaleText,
                    isPermanentlyDenied = isCameraPermanentlyDenied,
                    onRequestPermission = {
                        if (isCameraPermanentlyDenied) {
                            openAppDetailSettings(context)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                )
            }
        }
    }
}
