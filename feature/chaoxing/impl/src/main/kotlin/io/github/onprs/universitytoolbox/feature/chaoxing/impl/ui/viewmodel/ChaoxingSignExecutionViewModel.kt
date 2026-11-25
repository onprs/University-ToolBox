package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingDeviceSettingsRepository
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingPresetLocationDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingPresetLocationEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.InMemoryChaoxingDeviceSettingsRepository
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrParser
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.MultiAccountSignCoordinator
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.ChaoxingLocationService
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.CoordinateConverter
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.GeoPoint
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionRequest
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingSignExecutionUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ImageBytesHolder
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.MapJumpCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChaoxingSignExecutionViewModel.Factory::class)
@Suppress("TooManyFunctions", "LongParameterList")
class ChaoxingSignExecutionViewModel @AssistedInject constructor(
    @Assisted private val route: ChaoxingNavKey.SignExecution,
    private val accountDao: ChaoxingAccountDao,
    private val presetLocationDao: ChaoxingPresetLocationDao,
    private val remoteDataSource: ChaoxingRemoteDataSource,
    private val signCoordinator: MultiAccountSignCoordinator,
    private val licenseCoordinator: LicenseCoordinator,
    private val barcodeScanner: BarcodeScanner,
    private val locationService: ChaoxingLocationService,
    private val timeProvider: TimeProvider,
    private val deviceSettingsRepository: ChaoxingDeviceSettingsRepository,
) : ViewModel() {
    @Suppress("LongParameterList")
    constructor(
        route: ChaoxingNavKey.SignExecution,
        accountDao: ChaoxingAccountDao,
        presetLocationDao: ChaoxingPresetLocationDao,
        remoteDataSource: ChaoxingRemoteDataSource,
        signCoordinator: MultiAccountSignCoordinator,
        licenseCoordinator: LicenseCoordinator,
        barcodeScanner: BarcodeScanner,
        locationService: ChaoxingLocationService,
        timeProvider: TimeProvider,
    ) : this(
        route = route,
        accountDao = accountDao,
        presetLocationDao = presetLocationDao,
        remoteDataSource = remoteDataSource,
        signCoordinator = signCoordinator,
        licenseCoordinator = licenseCoordinator,
        barcodeScanner = barcodeScanner,
        locationService = locationService,
        timeProvider = timeProvider,
        deviceSettingsRepository = InMemoryChaoxingDeviceSettingsRepository(),
    )

    private val mutableUiState = MutableStateFlow(
        ChaoxingSignExecutionUiState(
            accountId = route.accountId,
            courseId = route.courseId,
            clazzId = route.clazzId,
            activeId = route.activeId,
            selectedAccountIds = setOf(route.accountId),
        ),
    )
    val uiState: StateFlow<ChaoxingSignExecutionUiState> = mutableUiState.asStateFlow()

    private var mapSearchJob: Job? = null
    private var reverseGeocodeJob: Job? = null
    private var nextJumpSequence: Long = 0L

    val cachedDeviceLocationPoint: GeoPoint?
        get() = locationService.lastKnownPlace?.bd09Point

    init {
        observePresetLocations()
        observeDeviceBadgeSetting()
        loadDetailAndAccounts()
    }

    private fun observePresetLocations() {
        viewModelScope.launch {
            presetLocationDao.observeAll().collect { presets ->
                mutableUiState.update { it.copy(presetLocations = presets) }
            }
        }
    }

    private fun observeDeviceBadgeSetting() {
        viewModelScope.launch {
            deviceSettingsRepository.hideDeviceCodeBadge.collect { hide ->
                mutableUiState.update { it.copy(hideDeviceCodeBadge = hide) }
            }
        }
    }

    fun setDeviceInfoDialogOpen(open: Boolean) {
        mutableUiState.update { it.copy(isDeviceInfoDialogOpen = open) }
    }

    fun setHideDeviceCodeBadge(hide: Boolean) {
        viewModelScope.launch {
            deviceSettingsRepository.setHideDeviceCodeBadge(hide)
        }
    }

    fun loadDetailAndAccounts() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoadingDetail = true, loadError = null) }
            val accounts = accountDao.getAll().filter { it.isEnabled }
            val maxSub = licenseCoordinator.maxSubAccounts()
            when (val detailRes = remoteDataSource.fetchActiveDetail(route.accountId, route.activeId)) {
                is AppResult.Success -> {
                    val detail = detailRes.data
                    val initialLat = detail.targetLatitude?.let(CoordinateConverter::formatCoordinate).orEmpty()
                    val initialLng = detail.targetLongitude?.let(CoordinateConverter::formatCoordinate).orEmpty()
                    mutableUiState.update { current ->
                        current.copy(
                            detail = detail,
                            isLoadingDetail = false,
                            availableAccounts = accounts,
                            maxSubAccountsQuota = maxSub,
                            latitudeInput = current.latitudeInput.ifBlank { initialLat },
                            longitudeInput = current.longitudeInput.ifBlank { initialLng },
                            addressInput = current.addressInput.ifBlank { detail.locationText.orEmpty() },
                        )
                    }
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        loadError = detailRes.error.message,
                        availableAccounts = accounts,
                        maxSubAccountsQuota = maxSub,
                    )
                }
            }
        }
    }

    fun toggleAccountSelection(accountId: String) {
        mutableUiState.update { current ->
            val next = if (accountId in current.selectedAccountIds) {
                current.selectedAccountIds - accountId
            } else {
                current.selectedAccountIds + accountId
            }
            current.copy(selectedAccountIds = next)
        }
    }

    fun selectAllEnabledAccounts() {
        mutableUiState.update { current ->
            current.copy(selectedAccountIds = current.availableAccounts.map { it.accountId }.toSet())
        }
    }

    fun updateQrRawInput(raw: String) {
        val parsed = ChaoxingQrParser.parse(raw)
        mutableUiState.update {
            it.copy(
                qrRawInput = raw,
                parsedQrEnc = parsed?.enc,
                inputHintMessage = null,
            )
        }
    }

    fun onBarcodeScanned(rawValue: String) {
        val parsed = ChaoxingQrParser.parse(rawValue)
        mutableUiState.update {
            it.copy(
                qrRawInput = rawValue,
                parsedQrEnc = parsed?.enc,
                isScannerSheetOpen = false,
                inputHintMessage = if (parsed != null) "已解析签到二维码校验参数" else "已扫码，请确认二维码内容",
            )
        }
    }

    fun scanQrFromImageUri(uri: Uri) {
        viewModelScope.launch {
            when (val res = barcodeScanner.scanImageUri(uri)) {
                is AppResult.Success -> {
                    val first = res.data.firstOrNull()
                    if (first != null) {
                        onBarcodeScanned(first.rawValue)
                    } else {
                        mutableUiState.update { it.copy(inputHintMessage = "未在所选图片中识别到有效二维码") }
                    }
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(inputHintMessage = res.error.message)
                }
            }
        }
    }

    fun setScannerSheetOpen(open: Boolean) {
        mutableUiState.update { it.copy(isScannerSheetOpen = open) }
    }

    fun setPresetSheetOpen(open: Boolean) {
        mutableUiState.update { it.copy(isPresetSheetOpen = open) }
    }

    fun setMapPickerOpen(open: Boolean) {
        if (!open) {
            mapSearchJob?.cancel()
            reverseGeocodeJob?.cancel()
        }
        mutableUiState.update {
            it.copy(
                isMapPickerOpen = open,
                mapSearchQuery = if (open) it.mapSearchQuery else "",
                hasCompletedMapSearch = if (open) it.hasCompletedMapSearch else false,
                mapSearchResults = if (open) it.mapSearchResults else emptyList(),
                mapJumpCommand = if (open) it.mapJumpCommand else null,
                mapReverseGeocodedAddress = if (open) it.mapReverseGeocodedAddress else null,
                mapStatusMessage = null,
            )
        }
    }

    fun notifyMapPermissionDenied() {
        mutableUiState.update {
            it.copy(
                mapStatusMessage = "尚未授予定位权限，请在系统设置中授权位置信息或直接搜索地点",
                inputHintMessage = "尚未授予定位权限",
            )
        }
    }

    fun updateSignCode(code: String) {
        mutableUiState.update { it.copy(signCodeInput = code, inputHintMessage = null) }
    }

    fun updateLocation(latitude: String, longitude: String, address: String) {
        mutableUiState.update {
            it.copy(
                latitudeInput = latitude,
                longitudeInput = longitude,
                addressInput = address,
                inputHintMessage = null,
            )
        }
    }

    fun applyTargetCourseLocation() {
        val detail = uiState.value.detail ?: return
        val lat = detail.targetLatitude
        val lng = detail.targetLongitude
        if (lat == null || lng == null) {
            setPresetSheetOpen(true)
            return
        }
        mutableUiState.update {
            it.copy(
                latitudeInput = CoordinateConverter.formatCoordinate(lat),
                longitudeInput = CoordinateConverter.formatCoordinate(lng),
                addressInput = detail.locationText.orEmpty().ifBlank { it.addressInput },
                isPresetSheetOpen = false,
                inputHintMessage = "已填入本次签到要求地点坐标",
            )
        }
    }

    fun applyPresetLocation(preset: ChaoxingPresetLocationEntity) {
        mutableUiState.update {
            it.copy(
                latitudeInput = CoordinateConverter.formatCoordinate(preset.latitude),
                longitudeInput = CoordinateConverter.formatCoordinate(preset.longitude),
                addressInput = preset.address.ifBlank { preset.title },
                isPresetSheetOpen = false,
                inputHintMessage = "已填入预设地点：${preset.title}",
            )
        }
    }

    fun savePresetLocation(title: String, latitude: Double, longitude: Double, address: String) {
        val normalizedTitle = title.trim().ifBlank { address.trim().ifBlank { "常用签到地点" } }
        val normalizedAddress = address.trim().ifBlank { normalizedTitle }
        viewModelScope.launch {
            presetLocationDao.insert(
                ChaoxingPresetLocationEntity(
                    title = normalizedTitle,
                    address = normalizedAddress,
                    latitude = latitude,
                    longitude = longitude,
                    createdAtMs = timeProvider.currentTimeMillis(),
                ),
            )
            mutableUiState.update {
                it.copy(
                    latitudeInput = CoordinateConverter.formatCoordinate(latitude),
                    longitudeInput = CoordinateConverter.formatCoordinate(longitude),
                    addressInput = normalizedAddress,
                    isMapPickerOpen = false,
                    inputHintMessage = "已保存预设地点：$normalizedTitle",
                )
            }
        }
    }

    fun deletePresetLocation(id: Long) {
        viewModelScope.launch {
            presetLocationDao.deleteById(id)
        }
    }

    fun confirmMapSelection(bd09Point: GeoPoint, address: String) {
        mutableUiState.update { current ->
            val resolvedAddr = address.ifBlank {
                current.detail?.locationText.orEmpty().ifBlank { current.addressInput }
            }
            current.copy(
                latitudeInput = CoordinateConverter.formatCoordinate(bd09Point.latitude),
                longitudeInput = CoordinateConverter.formatCoordinate(bd09Point.longitude),
                addressInput = resolvedAddr,
                isMapPickerOpen = false,
                inputHintMessage = "已从地图选定签到位置",
            )
        }
    }

    fun useCurrentDeviceLocation(jumpInMap: Boolean = false) {
        if (uiState.value.isLocatingCurrent) {
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLocatingCurrent = true,
                    inputHintMessage = null,
                    mapStatusMessage = null,
                )
            }
            when (val res = locationService.getCurrentLocation()) {
                is AppResult.Success -> {
                    val place = res.data
                    val jumpCmd = if (jumpInMap || uiState.value.isMapPickerOpen) {
                        MapJumpCommand(sequence = ++nextJumpSequence, place = place)
                    } else {
                        null
                    }
                    mutableUiState.update { current ->
                        val nextAddr = place.address.ifBlank {
                            current.addressInput.ifBlank { current.detail?.locationText.orEmpty() }
                        }
                        current.copy(
                            isLocatingCurrent = false,
                            latitudeInput = CoordinateConverter.formatCoordinate(place.bd09Point.latitude),
                            longitudeInput = CoordinateConverter.formatCoordinate(place.bd09Point.longitude),
                            addressInput = nextAddr,
                            mapJumpCommand = jumpCmd ?: current.mapJumpCommand,
                            mapStatusMessage = null,
                            inputHintMessage = if (jumpInMap) current.inputHintMessage else "已填入当前设备定位坐标",
                        )
                    }
                }

                is AppResult.Failure -> mutableUiState.update { current ->
                    val errMsg = res.error.message
                    val showInMap = jumpInMap || current.isMapPickerOpen
                    current.copy(
                        isLocatingCurrent = false,
                        mapStatusMessage = if (showInMap) errMsg else current.mapStatusMessage,
                        inputHintMessage = errMsg,
                    )
                }
            }
        }
    }

    fun updateMapSearchQuery(query: String, proximityGcj: GeoPoint? = null) {
        mapSearchJob?.cancel()
        val trimmed = query.trim()
        mutableUiState.update {
            it.copy(
                mapSearchQuery = query,
                hasCompletedMapSearch = false,
                mapStatusMessage = null,
                mapSearchResults = if (trimmed.isEmpty()) emptyList() else it.mapSearchResults,
                isSearchingMapPlaces = false,
            )
        }
        if (trimmed.length >= MIN_AUTO_SEARCH_LEN) {
            mapSearchJob = viewModelScope.launch {
                delay(MAP_SEARCH_DEBOUNCE_MS)
                performMapSearch(keyword = trimmed, proximityGcj = proximityGcj, autoJumpFirst = false)
            }
        }
    }

    fun searchMapPlaces(query: String, proximityGcj: GeoPoint? = null, autoJumpFirst: Boolean = false) {
        val keyword = query.trim()
        mapSearchJob?.cancel()
        if (keyword.isEmpty()) {
            mutableUiState.update {
                it.copy(
                    mapSearchResults = emptyList(),
                    isSearchingMapPlaces = false,
                    hasCompletedMapSearch = false,
                )
            }
            return
        }
        mapSearchJob = viewModelScope.launch {
            performMapSearch(keyword = keyword, proximityGcj = proximityGcj, autoJumpFirst = autoJumpFirst)
        }
    }

    private suspend fun performMapSearch(keyword: String, proximityGcj: GeoPoint?, autoJumpFirst: Boolean) {
        mutableUiState.update {
            it.copy(
                isSearchingMapPlaces = true,
                hasCompletedMapSearch = false,
                mapStatusMessage = null,
            )
        }
        when (val res = locationService.searchPlaces(keyword, proximityGcj)) {
            is AppResult.Success -> {
                val places = res.data
                val shouldJump = (autoJumpFirst && places.isNotEmpty()) || places.size == 1
                val jumpCmd = if (shouldJump && places.isNotEmpty()) {
                    MapJumpCommand(sequence = ++nextJumpSequence, place = places.first())
                } else {
                    null
                }
                val visibleCandidates = if (places.size == 1 && shouldJump) emptyList() else places
                mutableUiState.update { current ->
                    current.copy(
                        isSearchingMapPlaces = false,
                        hasCompletedMapSearch = true,
                        mapSearchResults = visibleCandidates,
                        mapJumpCommand = jumpCmd ?: current.mapJumpCommand,
                    )
                }
            }

            is AppResult.Failure -> mutableUiState.update {
                it.copy(
                    isSearchingMapPlaces = false,
                    hasCompletedMapSearch = true,
                    mapSearchResults = emptyList(),
                    mapStatusMessage = res.error.message,
                )
            }
        }
    }

    fun reverseGeocodeMapCenter(gcjLatitude: Double, gcjLongitude: Double) {
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = viewModelScope.launch {
            val address = locationService.reverseGeocodeGcj02(gcjLatitude, gcjLongitude)
            if (!address.isNullOrBlank()) {
                mutableUiState.update { it.copy(mapReverseGeocodedAddress = address) }
            }
        }
    }

    fun setPhoto(bytes: ByteArray, fileName: String) {
        mutableUiState.update {
            it.copy(photo = ImageBytesHolder(bytes, fileName), inputHintMessage = null)
        }
    }

    fun updateCaptchaValidate(validate: String) {
        mutableUiState.update { it.copy(captchaValidateInput = validate) }
    }

    fun executeSign(courseName: String = route.courseName) {
        val current = uiState.value
        val resolvedCourseName = courseName.ifBlank { route.courseName }
        if (current.isExecuting || current.selectedAccountIds.isEmpty()) {
            return
        }
        val orderedTargetIds = current.availableAccounts
            .filter { it.accountId in current.selectedAccountIds }
            .map { it.accountId }
            .ifEmpty { current.selectedAccountIds.toList() }

        val request = SignExecutionRequest(
            courseId = route.courseId,
            clazzId = route.clazzId,
            activeId = route.activeId,
            enc = current.parsedQrEnc,
            rawQrContent = current.qrRawInput.takeIf { it.isNotBlank() },
            signCode = current.signCodeInput.takeIf { it.isNotBlank() },
            latitude = current.latitudeInput.toDoubleOrNull(),
            longitude = current.longitudeInput.toDoubleOrNull(),
            address = current.addressInput.takeIf { it.isNotBlank() },
            photoBytes = current.photo?.bytes,
            photoFileName = current.photo?.fileName,
            captchaValidate = current.captchaValidateInput.takeIf { it.isNotBlank() },
        )

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isExecuting = true,
                    pendingCaptchaId = null,
                    pendingCaptchaMessage = null,
                    inputHintMessage = null,
                )
            }
            val summary = signCoordinator.executeForAccounts(
                targetAccountIds = orderedTargetIds,
                courseName = resolvedCourseName,
                activityTitle = current.detail?.title ?: "课堂签到",
                request = request,
                prefetchedDetail = current.detail,
            )
            val captchaHit = summary.reports
                .mapNotNull { it.result as? SignExecutionResult.RequiresCaptcha }
                .firstOrNull()
            val missingInputHit = summary.reports
                .mapNotNull { it.result as? SignExecutionResult.RequiresExtraInput }
                .firstOrNull()

            mutableUiState.update {
                it.copy(
                    isExecuting = false,
                    batchSummary = summary,
                    pendingCaptchaId = captchaHit?.captchaId,
                    pendingCaptchaMessage = captchaHit?.message,
                    inputHintMessage = missingInputHit?.message,
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(route: ChaoxingNavKey.SignExecution): ChaoxingSignExecutionViewModel
    }

    companion object {
        private const val MIN_AUTO_SEARCH_LEN = 2
        private const val MAP_SEARCH_DEBOUNCE_MS = 380L
    }
}
