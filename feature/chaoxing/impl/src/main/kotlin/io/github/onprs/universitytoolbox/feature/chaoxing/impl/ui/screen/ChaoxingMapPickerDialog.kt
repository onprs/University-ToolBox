package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLinearProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.CoordinateConverter
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.GeoPoint
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.ResolvedPlace
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.MapJumpCommand
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FALLBACK_CHINA_CENTER_LAT = 35.86166
private const val FALLBACK_CHINA_CENTER_LON = 104.195397
private const val FALLBACK_OVERVIEW_ZOOM = 5.0
private const val REVERSE_GEOCODE_DEBOUNCE_MS = 450L
private const val TILE_CACHE_MAX_ENTRIES = 160
private const val MAX_PARENT_FALLBACK_STEPS = 3
private const val MAP_OVERLAY_Z_INDEX = 10f
private const val COMPACT_VIEWPORT_HEIGHT_DP = 520f
private const val ULTRA_COMPACT_VIEWPORT_HEIGHT_DP = 320f
private const val DOUBLE_TAP_SLOP_DP = 42f

private val STANDARD_MAP_BG_COLOR = Color(0xFFF3F1EC)
private val SATELLITE_MAP_BG_COLOR = Color(0xFF18222B)

/**
 * 内存 LRU 瓦片位图缓存：支持跨缩放层级保留父瓦片以消除缩放闪烁与黑底撕裂。
 */
private class TileBitmapCache(private val maxEntries: Int = TILE_CACHE_MAX_ENTRIES) {
    private val map = object : LinkedHashMap<String, ImageBitmap>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > maxEntries
    }
    private val inFlightUrls = HashSet<String>()
    var revision by mutableIntStateOf(0)
        private set

    @Synchronized
    fun get(url: String): ImageBitmap? = map[url]

    @Synchronized
    fun markInFlightIfNeeded(url: String): Boolean {
        if (map.containsKey(url) || !inFlightUrls.add(url)) {
            return false
        }
        return true
    }

    @Synchronized
    fun completeLoad(url: String, bitmap: ImageBitmap?) {
        inFlightUrls.remove(url)
        if (bitmap != null) {
            map[url] = bitmap
            revision++
        }
    }
}

/**
 * 地图选点视口状态持有者（支持横竖屏旋转与配置变更恢复，并防止异步逆地理编码覆盖用户手动编辑内容）。
 */
internal class MapViewportHolder(
    initialCenterGcj: GeoPoint,
    initialConfirmed: Boolean,
    initialAddress: String,
    initialZoom: Double = if (initialConfirmed) SlippyMapProjection.DEFAULT_CAMPUS_ZOOM else FALLBACK_OVERVIEW_ZOOM,
    initialSatelliteMode: Boolean = false,
    initialShowSavePrompt: Boolean = false,
    initialHasUserMovedMap: Boolean = false,
    initialAddressManuallyEdited: Boolean = false,
) {
    var centerGcj by mutableStateOf(initialCenterGcj)
    var zoom by mutableDoubleStateOf(initialZoom)
    var hasConfirmedLocation by mutableStateOf(initialConfirmed)
    var satelliteMode by mutableStateOf(initialSatelliteMode)
    var addressText by mutableStateOf(initialAddress)
    var showSavePresetPrompt by mutableStateOf(initialShowSavePrompt)
    var hasUserMovedMap by mutableStateOf(initialHasUserMovedMap)
    var isAddressManuallyEdited by mutableStateOf(initialAddressManuallyEdited)

    fun applyPlace(place: ResolvedPlace, fallbackTitle: Boolean = false) {
        centerGcj = place.gcj02Point
        zoom = SlippyMapProjection.DEFAULT_CAMPUS_ZOOM
        hasConfirmedLocation = true
        isAddressManuallyEdited = false
        val resolvedAddr = if (fallbackTitle) {
            place.address.ifBlank { place.title }
        } else {
            place.address
        }
        if (resolvedAddr.isNotBlank()) {
            addressText = resolvedAddr
            hasUserMovedMap = false
        } else {
            hasUserMovedMap = true
        }
    }

    fun updateCenterAndZoomByUser(nextCenter: GeoPoint, nextZoom: Double) {
        val centerChanged = nextCenter != centerGcj
        centerGcj = nextCenter
        zoom = nextZoom
        hasConfirmedLocation = true
        if (centerChanged) {
            hasUserMovedMap = true
            isAddressManuallyEdited = false
        }
    }

    fun onManualAddressInput(text: String) {
        addressText = text
        isAddressManuallyEdited = true
    }

    fun applyReverseGeocodedAddressIfEligible(resolved: String, isAddressFieldFocused: Boolean) {
        if (!isAddressManuallyEdited && !isAddressFieldFocused && resolved.isNotBlank()) {
            addressText = resolved
        }
    }

    companion object {
        private const val IDX_LAT = 0
        private const val IDX_LNG = 1
        private const val IDX_ZOOM = 2
        private const val IDX_CONFIRMED = 3
        private const val IDX_SATELLITE = 4
        private const val IDX_ADDRESS = 5
        private const val IDX_SAVE_PROMPT = 6
        private const val IDX_MOVED = 7
        private const val IDX_EDITED = 8

        val Saver: Saver<MapViewportHolder, Any> = listSaver(
            save = { holder ->
                listOf(
                    holder.centerGcj.latitude,
                    holder.centerGcj.longitude,
                    holder.zoom,
                    holder.hasConfirmedLocation,
                    holder.satelliteMode,
                    holder.addressText,
                    holder.showSavePresetPrompt,
                    holder.hasUserMovedMap,
                    holder.isAddressManuallyEdited,
                )
            },
            restore = { saved ->
                MapViewportHolder(
                    initialCenterGcj = GeoPoint(
                        latitude = saved[IDX_LAT] as Double,
                        longitude = saved[IDX_LNG] as Double,
                    ),
                    initialConfirmed = saved[IDX_CONFIRMED] as Boolean,
                    initialAddress = saved[IDX_ADDRESS] as String,
                    initialZoom = saved[IDX_ZOOM] as Double,
                    initialSatelliteMode = saved[IDX_SATELLITE] as Boolean,
                    initialShowSavePrompt = saved[IDX_SAVE_PROMPT] as Boolean,
                    initialHasUserMovedMap = saved[IDX_MOVED] as Boolean,
                    initialAddressManuallyEdited = saved[IDX_EDITED] as Boolean,
                )
            },
        )
    }
}

/**
 * 地图覆盖层在软键盘弹起或小屏环境下的自适应折叠策略。
 */
internal data class MapOverlayCollapseSpec(
    val collapseTopSearchControls: Boolean,
    val collapseBottomConfirmControls: Boolean,
    val ultraCompact: Boolean,
)

internal fun resolveMapOverlayCollapseSpec(
    availableHeightDp: Float,
    isImeOpen: Boolean,
    isSearchFocused: Boolean,
    isAddressFocused: Boolean,
): MapOverlayCollapseSpec {
    val constrained = isImeOpen || availableHeightDp < COMPACT_VIEWPORT_HEIGHT_DP
    val ultraCompact = availableHeightDp < ULTRA_COMPACT_VIEWPORT_HEIGHT_DP
    return when {
        isAddressFocused && constrained -> MapOverlayCollapseSpec(
            collapseTopSearchControls = true,
            collapseBottomConfirmControls = false,
            ultraCompact = ultraCompact,
        )

        isSearchFocused && constrained -> MapOverlayCollapseSpec(
            collapseTopSearchControls = false,
            collapseBottomConfirmControls = true,
            ultraCompact = ultraCompact,
        )

        else -> MapOverlayCollapseSpec(
            collapseTopSearchControls = false,
            collapseBottomConfirmControls = false,
            ultraCompact = ultraCompact,
        )
    }
}

/**
 * 全屏交互式地图手动选点覆盖层（挂载于宿主 Activity 窗口层级内）：
 * - 直接复用宿主窗口 `enableEdgeToEdge()` 与 `adjustResize`，并使用 `navigationBars.union(ime)` 消除双重底部间距；
 * - 针对顶部搜索与底部位置描述焦点动态折叠非焦点区域，防止软键盘弹起时地图或确认面板被挤压截断；
 * - 统一协调多指缩放与单指点击/双击手势，消除双指松手瞬间选点漂移与点击误触。
 */
@Composable
internal fun ChaoxingMapPickerDialog(
    initialBd09Point: GeoPoint?,
    targetBd09Point: GeoPoint?,
    fallbackBd09Point: GeoPoint?,
    targetRangeMeters: Int?,
    requiredLocationHint: String?,
    initialAddress: String,
    searchQuery: String,
    isSearching: Boolean,
    hasCompletedSearch: Boolean,
    searchResults: List<ResolvedPlace>,
    isLocatingCurrent: Boolean,
    jumpCommand: MapJumpCommand?,
    reverseGeocodedAddress: String?,
    statusMessage: String?,
    onSearchQueryChange: (String, GeoPoint?) -> Unit,
    onExecuteSearch: (String, GeoPoint?, Boolean) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onReverseGeocodeCenter: (Double, Double) -> Unit,
    onConfirmSelection: (bd09Point: GeoPoint, address: String) -> Unit,
    onSaveAsPresetAndSelect: (title: String, bd09Point: GeoPoint, address: String) -> Unit,
    onDismiss: () -> Unit,
    onDismissSearchResults: () -> Unit = { onSearchQueryChange("", null) },
    isLocationPermanentlyDenied: Boolean = false,
    onOpenAppSettings: (() -> Unit)? = null,
) {
    val viewport = rememberSaveable(saver = MapViewportHolder.Saver) {
        val start = resolveInitialCenterSpec(initialBd09Point, targetBd09Point, fallbackBd09Point)
        MapViewportHolder(start.first, start.second, initialAddress)
    }

    var isSearchFocused by remember { mutableStateOf(false) }
    var isAddressFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    MapOverlayBackHandlers(
        isSavePromptOpen = viewport.showSavePresetPrompt,
        searchResults = searchResults,
        onDismissSearchResults = {
            focusManager.clearFocus()
            onDismissSearchResults()
        },
        onDismissMap = {
            focusManager.clearFocus()
            onDismiss()
        },
    )

    MapPickerSideEffects(
        viewport = viewport,
        isAddressFocused = isAddressFocused,
        jumpCommand = jumpCommand,
        reverseGeocodedAddress = reverseGeocodedAddress,
        autoLocateOnOpen = initialBd09Point == null && targetBd09Point == null,
        autoSearchKeyword = requiredLocationHint?.takeIf { initialBd09Point == null && targetBd09Point == null },
        onRequestCurrentLocation = onRequestCurrentLocation,
        onReverseGeocodeCenter = onReverseGeocodeCenter,
        onQuickLocateKeyword = { keyword ->
            onExecuteSearch(keyword, viewport.centerGcj.takeIf { viewport.hasConfirmedLocation }, true)
        },
    )

    MapPickerOverlaySurface(
        viewport = viewport,
        targetBd09 = targetBd09Point,
        targetRangeMeters = targetRangeMeters,
        requiredLocationHint = requiredLocationHint,
        searchQuery = searchQuery,
        isSearching = isSearching,
        hasCompletedSearch = hasCompletedSearch,
        searchResults = searchResults,
        isLocatingCurrent = isLocatingCurrent,
        statusMessage = statusMessage,
        isSearchFocused = isSearchFocused,
        isAddressFocused = isAddressFocused,
        isLocationPermanentlyDenied = isLocationPermanentlyDenied,
        onSearchFocusChange = { isSearchFocused = it },
        onAddressFocusChange = { isAddressFocused = it },
        onSearchQueryChange = { q ->
            onSearchQueryChange(q, viewport.centerGcj.takeIf { viewport.hasConfirmedLocation })
        },
        onExecuteSearch = { q, jumpFirst ->
            onExecuteSearch(q, viewport.centerGcj.takeIf { viewport.hasConfirmedLocation }, jumpFirst)
        },
        onDismissSearchResults = {
            focusManager.clearFocus()
            onDismissSearchResults()
        },
        onConfirmSelection = onConfirmSelection,
        onSaveAsPresetAndSelect = onSaveAsPresetAndSelect,
        onRequestCurrentLocation = onRequestCurrentLocation,
        onOpenAppSettings = onOpenAppSettings,
        onDismiss = {
            focusManager.clearFocus()
            onDismiss()
        },
    )
}

@Composable
private fun MapOverlayBackHandlers(
    isSavePromptOpen: Boolean,
    searchResults: List<ResolvedPlace>,
    onDismissSearchResults: () -> Unit,
    onDismissMap: () -> Unit,
) {
    var dismissedCurrentResults by remember(searchResults) { mutableStateOf(false) }
    val currentResults by rememberUpdatedState(searchResults)
    val currentOnDismissSearch by rememberUpdatedState(onDismissSearchResults)
    val currentOnDismissMap by rememberUpdatedState(onDismissMap)

    BackHandler(enabled = !isSavePromptOpen) {
        if (currentResults.isNotEmpty() && !dismissedCurrentResults) {
            dismissedCurrentResults = true
            currentOnDismissSearch()
        } else {
            currentOnDismissMap()
        }
    }
}

private fun resolveInitialCenterSpec(
    initialBd09: GeoPoint?,
    targetBd09: GeoPoint?,
    fallbackBd09: GeoPoint?,
): Pair<GeoPoint, Boolean> = when {
    initialBd09 != null -> CoordinateConverter.bd09ToGcj02(initialBd09.latitude, initialBd09.longitude) to true
    targetBd09 != null -> CoordinateConverter.bd09ToGcj02(targetBd09.latitude, targetBd09.longitude) to true
    fallbackBd09 != null -> CoordinateConverter.bd09ToGcj02(fallbackBd09.latitude, fallbackBd09.longitude) to true
    else -> GeoPoint(latitude = FALLBACK_CHINA_CENTER_LAT, longitude = FALLBACK_CHINA_CENTER_LON) to false
}

@Composable
private fun MapPickerSideEffects(
    viewport: MapViewportHolder,
    isAddressFocused: Boolean,
    jumpCommand: MapJumpCommand?,
    reverseGeocodedAddress: String?,
    autoLocateOnOpen: Boolean,
    autoSearchKeyword: String?,
    onRequestCurrentLocation: () -> Unit,
    onReverseGeocodeCenter: (Double, Double) -> Unit,
    onQuickLocateKeyword: (String) -> Unit,
) {
    val currentAddressFocused by rememberUpdatedState(isAddressFocused)
    LaunchedEffect(jumpCommand?.sequence) {
        if (jumpCommand != null) {
            viewport.applyPlace(jumpCommand.place)
        }
    }
    LaunchedEffect(reverseGeocodedAddress) {
        if (!reverseGeocodedAddress.isNullOrBlank()) {
            viewport.applyReverseGeocodedAddressIfEligible(reverseGeocodedAddress, currentAddressFocused)
        }
    }
    val shouldReverseGeocode = viewport.hasConfirmedLocation &&
        (viewport.hasUserMovedMap || viewport.addressText.isBlank())
    LaunchedEffect(viewport.centerGcj, shouldReverseGeocode) {
        if (shouldReverseGeocode) {
            delay(REVERSE_GEOCODE_DEBOUNCE_MS)
            onReverseGeocodeCenter(viewport.centerGcj.latitude, viewport.centerGcj.longitude)
        }
    }
    LaunchedEffect(Unit) {
        if (!autoSearchKeyword.isNullOrBlank()) {
            onQuickLocateKeyword(autoSearchKeyword)
        } else if (autoLocateOnOpen && !viewport.hasConfirmedLocation) {
            onRequestCurrentLocation()
        }
    }
}

@Composable
private fun MapPickerOverlaySurface(
    viewport: MapViewportHolder,
    targetBd09: GeoPoint?,
    targetRangeMeters: Int?,
    requiredLocationHint: String?,
    searchQuery: String,
    isSearching: Boolean,
    hasCompletedSearch: Boolean,
    searchResults: List<ResolvedPlace>,
    isLocatingCurrent: Boolean,
    statusMessage: String?,
    isSearchFocused: Boolean,
    isAddressFocused: Boolean,
    isLocationPermanentlyDenied: Boolean,
    onSearchFocusChange: (Boolean) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String, Boolean) -> Unit,
    onDismissSearchResults: () -> Unit,
    onConfirmSelection: (GeoPoint, String) -> Unit,
    onSaveAsPresetAndSelect: (String, GeoPoint, String) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onOpenAppSettings: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val isImeOpen = WindowInsets.ime.getBottom(density) > 0
    val currentBd09 = remember(viewport.centerGcj) {
        CoordinateConverter.gcj02ToBd09(viewport.centerGcj.latitude, viewport.centerGcj.longitude)
    }

    if (viewport.showSavePresetPrompt && viewport.hasConfirmedLocation) {
        SavePresetNameDialog(
            initialTitle = viewport.addressText.trim().ifBlank { requiredLocationHint.orEmpty() },
            onConfirm = { title ->
                viewport.showSavePresetPrompt = false
                onSaveAsPresetAndSelect(title, currentBd09, viewport.addressText.trim())
            },
            onDismiss = { viewport.showSavePresetPrompt = false },
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(MAP_OVERLAY_Z_INDEX),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            val collapseSpec = resolveMapOverlayCollapseSpec(
                availableHeightDp = maxHeight.value,
                isImeOpen = isImeOpen,
                isSearchFocused = isSearchFocused,
                isAddressFocused = isAddressFocused,
            )
            MapPickerOverlayColumns(
                viewport = viewport,
                currentBd09 = currentBd09,
                targetBd09 = targetBd09,
                targetRangeMeters = targetRangeMeters,
                requiredLocationHint = requiredLocationHint,
                searchQuery = searchQuery,
                isSearching = isSearching,
                hasCompletedSearch = hasCompletedSearch,
                searchResults = searchResults,
                isLocatingCurrent = isLocatingCurrent,
                statusMessage = statusMessage,
                hasActiveInputFocus = isSearchFocused || isAddressFocused,
                isLocationPermanentlyDenied = isLocationPermanentlyDenied,
                collapseSpec = collapseSpec,
                maxBottomPanelHeight = (maxHeight * 0.68f).coerceAtLeast(156.dp),
                onSearchFocusChange = onSearchFocusChange,
                onAddressFocusChange = onAddressFocusChange,
                onSearchQueryChange = onSearchQueryChange,
                onExecuteSearch = onExecuteSearch,
                onDismissSearchResults = onDismissSearchResults,
                onConfirmSelection = onConfirmSelection,
                onRequestCurrentLocation = onRequestCurrentLocation,
                onOpenAppSettings = onOpenAppSettings,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun MapPickerOverlayColumns(
    viewport: MapViewportHolder,
    currentBd09: GeoPoint,
    targetBd09: GeoPoint?,
    targetRangeMeters: Int?,
    requiredLocationHint: String?,
    searchQuery: String,
    isSearching: Boolean,
    hasCompletedSearch: Boolean,
    searchResults: List<ResolvedPlace>,
    isLocatingCurrent: Boolean,
    statusMessage: String?,
    hasActiveInputFocus: Boolean,
    isLocationPermanentlyDenied: Boolean,
    collapseSpec: MapOverlayCollapseSpec,
    maxBottomPanelHeight: Dp,
    onSearchFocusChange: (Boolean) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String, Boolean) -> Unit,
    onDismissSearchResults: () -> Unit,
    onConfirmSelection: (GeoPoint, String) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onOpenAppSettings: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val targetGcj = remember(targetBd09) {
        targetBd09?.let { CoordinateConverter.bd09ToGcj02(it.latitude, it.longitude) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        MapPickerTopSearchSection(
            searchQuery = searchQuery,
            satelliteMode = viewport.satelliteMode,
            isSearching = isSearching,
            hasCompletedSearch = hasCompletedSearch,
            hasSearchResults = searchResults.isNotEmpty(),
            requiredLocationHint = requiredLocationHint,
            statusMessage = statusMessage,
            compactHeaderOnly = collapseSpec.collapseTopSearchControls,
            isLocationPermanentlyDenied = isLocationPermanentlyDenied,
            onSearchFocusChange = onSearchFocusChange,
            onSearchQueryChange = onSearchQueryChange,
            onExecuteSearch = { onExecuteSearch(searchQuery, false) },
            onQuickSearchRequired = { hint ->
                onSearchQueryChange(hint)
                onExecuteSearch(hint, true)
            },
            onOpenAppSettings = onOpenAppSettings,
            onToggleLayer = { viewport.satelliteMode = !viewport.satelliteMode },
            onDismiss = onDismiss,
        )

        MapViewportLayerBox(
            viewport = viewport,
            targetGcj = targetGcj,
            targetRangeMeters = targetRangeMeters,
            searchResults = searchResults,
            isLocatingCurrent = isLocatingCurrent,
            hasActiveInputFocus = hasActiveInputFocus,
            hideFloatingZoomControls = collapseSpec.ultraCompact,
            onClearSearchAfterSelect = { onSearchQueryChange("") },
            onDismissSearchResults = onDismissSearchResults,
            onRequestCurrentLocation = onRequestCurrentLocation,
            modifier = Modifier.weight(1f),
        )

        MapPickerBottomConfirmPanel(
            selectedBd09 = currentBd09,
            hasConfirmedLocation = viewport.hasConfirmedLocation,
            targetBd09 = targetBd09,
            targetRangeMeters = targetRangeMeters,
            addressText = viewport.addressText,
            collapsedSummaryOnly = collapseSpec.collapseBottomConfirmControls,
            ultraCompact = collapseSpec.ultraCompact,
            maxPanelHeight = maxBottomPanelHeight,
            onAddressFocusChange = onAddressFocusChange,
            onAddressChange = viewport::onManualAddressInput,
            onOpenSavePreset = { viewport.showSavePresetPrompt = true },
            onConfirm = { onConfirmSelection(currentBd09, viewport.addressText.trim()) },
        )
    }
}

@Composable
private fun MapViewportLayerBox(
    viewport: MapViewportHolder,
    targetGcj: GeoPoint?,
    targetRangeMeters: Int?,
    searchResults: List<ResolvedPlace>,
    isLocatingCurrent: Boolean,
    hasActiveInputFocus: Boolean,
    hideFloatingZoomControls: Boolean,
    onClearSearchAfterSelect: () -> Unit,
    onDismissSearchResults: () -> Unit,
    onRequestCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val hasOverlayOrFocus = searchResults.isNotEmpty() || hasActiveInputFocus

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val overlayMaxHeight = (maxHeight - ToolboxSpacing.large).coerceIn(96.dp, 240.dp)

        InteractiveSlippyMapCanvas(
            centerGcj = viewport.centerGcj,
            zoom = viewport.zoom,
            satelliteMode = viewport.satelliteMode,
            targetGcj = targetGcj,
            targetRangeMeters = targetRangeMeters,
            consumeTapToDismissOverlayOnly = hasOverlayOrFocus,
            onDismissOverlayAndFocus = {
                focusManager.clearFocus()
                if (searchResults.isNotEmpty()) {
                    onDismissSearchResults()
                }
            },
            onCenterAndZoomChange = viewport::updateCenterAndZoomByUser,
        )

        if (searchResults.isNotEmpty()) {
            SearchResultsOverlayCard(
                results = searchResults,
                maxListHeight = overlayMaxHeight,
                onSelectPlace = { place ->
                    focusManager.clearFocus()
                    viewport.applyPlace(place, fallbackTitle = true)
                    onClearSearchAfterSelect()
                },
                onDismissResults = {
                    focusManager.clearFocus()
                    onDismissSearchResults()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
            )
        }

        if (!hideFloatingZoomControls) {
            MapFloatingControls(
                isLocatingCurrent = isLocatingCurrent,
                onZoomIn = {
                    focusManager.clearFocus()
                    viewport.updateCenterAndZoomByUser(
                        viewport.centerGcj,
                        (viewport.zoom + 1.0).coerceAtMost(SlippyMapProjection.MAX_ZOOM),
                    )
                },
                onZoomOut = {
                    focusManager.clearFocus()
                    viewport.updateCenterAndZoomByUser(
                        viewport.centerGcj,
                        (viewport.zoom - 1.0).coerceAtLeast(SlippyMapProjection.MIN_ZOOM),
                    )
                },
                onLocateMe = {
                    focusManager.clearFocus()
                    onRequestCurrentLocation()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ToolboxSpacing.medium),
            )
        }
    }
}

@Composable
private fun MapPickerTopSearchSection(
    searchQuery: String,
    satelliteMode: Boolean,
    isSearching: Boolean,
    hasCompletedSearch: Boolean,
    hasSearchResults: Boolean,
    requiredLocationHint: String?,
    statusMessage: String?,
    compactHeaderOnly: Boolean,
    isLocationPermanentlyDenied: Boolean,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onQuickSearchRequired: (String) -> Unit,
    onOpenAppSettings: (() -> Unit)?,
    onToggleLayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val bannerHint = resolveSearchBannerHint(
        statusMessage = statusMessage,
        hasCompletedSearch = hasCompletedSearch,
        isSearching = isSearching,
        searchQuery = searchQuery,
        hasSearchResults = hasSearchResults,
    )
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolboxSpacing.small, vertical = ToolboxSpacing.extraSmall),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
        ) {
            MapPickerHeaderBar(satelliteMode = satelliteMode, onToggleLayer = onToggleLayer, onDismiss = onDismiss)
            if (!compactHeaderOnly) {
                MapSearchTextField(
                    searchQuery = searchQuery,
                    onSearchFocusChange = onSearchFocusChange,
                    onSearchQueryChange = onSearchQueryChange,
                    onExecuteSearch = {
                        focusManager.clearFocus()
                        onExecuteSearch()
                    },
                )
                if (!requiredLocationHint.isNullOrBlank()) {
                    RequiredLocationQuickRow(
                        hint = requiredLocationHint,
                        onQuickSearch = {
                            focusManager.clearFocus()
                            onQuickSearchRequired(requiredLocationHint)
                        },
                    )
                }
                if (!bannerHint.isNullOrBlank()) {
                    SearchWarningBanner(
                        message = bannerHint,
                        actionLabel = if (isLocationPermanentlyDenied && onOpenAppSettings != null) "前往设置" else null,
                        onAction = onOpenAppSettings,
                    )
                }
                if (isSearching) {
                    ToolboxLinearProgressBar()
                }
            }
        }
    }
}

@Composable
private fun MapSearchTextField(
    searchQuery: String,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(text = "搜索学校、教学楼或地标名称") },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                SearchInputTrailingActions(
                    onClear = { onSearchQueryChange("") },
                    onSearch = onExecuteSearch,
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.extraSmall)
            .onFocusChanged { onSearchFocusChange(it.isFocused) },
    )
}

@Composable
private fun SearchInputTrailingActions(onClear: () -> Unit, onSearch: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClear) {
            Icon(imageVector = Icons.Outlined.Close, contentDescription = "清空搜索词")
        }
        TextButton(onClick = onSearch) { Text(text = "搜索") }
    }
}

private fun resolveSearchBannerHint(
    statusMessage: String?,
    hasCompletedSearch: Boolean,
    isSearching: Boolean,
    searchQuery: String,
    hasSearchResults: Boolean,
): String? {
    if (!statusMessage.isNullOrBlank()) return statusMessage
    val isEmptyResult = hasCompletedSearch && !isSearching && !hasSearchResults
    return if (isEmptyResult && searchQuery.isNotBlank()) {
        "未找到匹配地点，请尝试输入「学校名称 + 教学楼/地标」"
    } else {
        null
    }
}

@Composable
private fun SearchWarningBanner(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(
        color = ToolboxTheme.extendedColors.warningContainer,
        shape = RoundedCornerShape(ToolboxRadius.chip),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ToolboxSpacing.small, vertical = ToolboxSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                color = ToolboxTheme.extendedColors.onWarningContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MapPickerHeaderBar(satelliteMode: Boolean, onToggleLayer: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Outlined.Close, contentDescription = "关闭地图选点")
        }
        Text(text = "地图手动选点", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onToggleLayer) {
            Icon(imageVector = Icons.Outlined.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = if (satelliteMode) " 标准图" else " 卫星图")
        }
    }
}

@Composable
private fun RequiredLocationQuickRow(hint: String, onQuickSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "要求地点：$hint",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onQuickSearch) { Text(text = "定位该地点") }
    }
}

@Composable
private fun InteractiveSlippyMapCanvas(
    centerGcj: GeoPoint,
    zoom: Double,
    satelliteMode: Boolean,
    targetGcj: GeoPoint?,
    targetRangeMeters: Int?,
    consumeTapToDismissOverlayOnly: Boolean,
    onDismissOverlayAndFocus: () -> Unit,
    onCenterAndZoomChange: (GeoPoint, Double) -> Unit,
) {
    val density = LocalDensity.current
    val baseTilePx = with(density) { SlippyMapProjection.BASE_TILE_DP.dp.toPx().toDouble() }
    val doubleTapSlopPx = with(density) { DOUBLE_TAP_SLOP_DP.dp.toPx() }
    val tileCache = remember { TileBitmapCache() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val tiles = remember(centerGcj, zoom, baseTilePx, viewportWidthPx, viewportHeightPx, satelliteMode) {
            SlippyMapProjection.computeVisibleTiles(
                center = centerGcj,
                zoom = zoom,
                baseTilePx = baseTilePx,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                satelliteMode = satelliteMode,
            )
        }

        TilePrefetchEffect(tiles = tiles, satelliteMode = satelliteMode, tileCache = tileCache)

        val primaryColor = MaterialTheme.colorScheme.primary
        val cacheRevision = tileCache.revision

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .unifiedMapGestureModifier(
                    centerGcj = centerGcj,
                    zoom = zoom,
                    baseTilePx = baseTilePx,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    doubleTapSlopPx = doubleTapSlopPx,
                    consumeTapToDismissOverlayOnly = consumeTapToDismissOverlayOnly,
                    onDismissOverlayAndFocus = onDismissOverlayAndFocus,
                    onCenterAndZoomChange = onCenterAndZoomChange,
                ),
        ) {
            // 读取 cacheRevision 订阅瓦片异步解码完成后的画布重绘
            if (cacheRevision >= 0) {
                drawRect(color = if (satelliteMode) SATELLITE_MAP_BG_COLOR else STANDARD_MAP_BG_COLOR)
                tiles.forEach { tile ->
                    drawTileWithFallback(tileCache = tileCache, tile = tile, satelliteMode = satelliteMode)
                }
            }
            if (targetGcj != null) {
                drawTargetRangeOverlay(
                    targetGcj = targetGcj,
                    centerGcj = centerGcj,
                    zoom = zoom,
                    baseTilePx = baseTilePx,
                    targetRangeMeters = targetRangeMeters,
                    primaryColor = primaryColor,
                )
            }
        }

        CenterPinMarker(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TilePrefetchEffect(
    tiles: List<SlippyMapProjection.TileSpec>,
    satelliteMode: Boolean,
    tileCache: TileBitmapCache,
) {
    val context = LocalContext.current
    LaunchedEffect(tiles, satelliteMode) {
        val imageLoader = SingletonImageLoader.get(context)
        val urls = LinkedHashSet<String>(tiles.size * 2)
        tiles.forEach { tile ->
            urls += tile.url
            tile.overlayUrl?.let { urls += it }
        }
        urls.forEach { url ->
            if (tileCache.markInFlightIfNeeded(url)) {
                launch {
                    val bitmap = runCatching {
                        val req = ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .build()
                        val res = imageLoader.execute(req)
                        (res as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
                    }.getOrNull()
                    tileCache.completeLoad(url, bitmap)
                }
            }
        }
    }
}

private fun DrawScope.drawTileWithFallback(
    tileCache: TileBitmapCache,
    tile: SlippyMapProjection.TileSpec,
    satelliteMode: Boolean,
) {
    val dstOffset = IntOffset(tile.offsetX, tile.offsetY)
    val dstSize = IntSize(tile.widthPx, tile.heightPx)
    val exactBitmap = tileCache.get(tile.url)
    if (exactBitmap != null) {
        drawImage(
            image = exactBitmap,
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.Medium,
        )
    } else {
        drawParentQuadrantFallback(
            tileCache = tileCache,
            tile = tile,
            satelliteMode = satelliteMode,
            dstOffset = dstOffset,
            dstSize = dstSize,
        )
    }

    val overlayBitmap = tile.overlayUrl?.let(tileCache::get)
    if (overlayBitmap != null) {
        drawImage(
            image = overlayBitmap,
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.Medium,
        )
    }
}

private fun DrawScope.drawParentQuadrantFallback(
    tileCache: TileBitmapCache,
    tile: SlippyMapProjection.TileSpec,
    satelliteMode: Boolean,
    dstOffset: IntOffset,
    dstSize: IntSize,
) {
    val maxStep = (tile.z - SlippyMapProjection.MIN_ZOOM.toInt()).coerceAtMost(MAX_PARENT_FALLBACK_STEPS)
    for (step in 1..maxStep) {
        val parentZ = tile.z - step
        val parentX = tile.x shr step
        val parentY = tile.y shr step
        val parentUrl = SlippyMapProjection.buildTileUrlForKey(parentZ, parentX, parentY, satelliteMode)
        val parentBitmap = tileCache.get(parentUrl)
        if (parentBitmap != null) {
            val gridSpan = 1 shl step
            val subX = tile.x and (gridSpan - 1)
            val subY = tile.y and (gridSpan - 1)
            val srcTileWidth = (parentBitmap.width / gridSpan).coerceAtLeast(1)
            val srcTileHeight = (parentBitmap.height / gridSpan).coerceAtLeast(1)
            drawImage(
                image = parentBitmap,
                srcOffset = IntOffset(subX * srcTileWidth, subY * srcTileHeight),
                srcSize = IntSize(srcTileWidth, srcTileHeight),
                dstOffset = dstOffset,
                dstSize = dstSize,
                filterQuality = FilterQuality.Low,
            )
            return
        }
    }
}

private fun DrawScope.drawTargetRangeOverlay(
    targetGcj: GeoPoint,
    centerGcj: GeoPoint,
    zoom: Double,
    baseTilePx: Double,
    targetRangeMeters: Int?,
    primaryColor: Color,
) {
    val targetOffset = SlippyMapProjection.pointToViewportOffset(
        target = targetGcj,
        center = centerGcj,
        zoom = zoom,
        baseTilePx = baseTilePx,
        viewportWidthPx = size.width,
        viewportHeightPx = size.height,
    )
    if (targetRangeMeters != null && targetRangeMeters > 0) {
        val radiusPx = SlippyMapProjection.metersToPixels(
            meters = targetRangeMeters,
            latitude = targetGcj.latitude,
            zoom = zoom,
            baseTilePx = baseTilePx,
        )
        drawCircle(color = primaryColor.copy(alpha = 0.18f), radius = radiusPx, center = targetOffset)
        drawCircle(
            color = primaryColor.copy(alpha = 0.65f),
            radius = radiusPx,
            center = targetOffset,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
    drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = targetOffset)
}

private class MapGestureRuntimeCallbacks(
    val centerProvider: () -> GeoPoint,
    val zoomProvider: () -> Double,
    val dismissOverlayOnlyProvider: () -> Boolean,
    val onDismissOverlay: () -> Unit,
    val onTransform: (GeoPoint, Double) -> Unit,
)

@Composable
private fun Modifier.unifiedMapGestureModifier(
    centerGcj: GeoPoint,
    zoom: Double,
    baseTilePx: Double,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    doubleTapSlopPx: Float,
    consumeTapToDismissOverlayOnly: Boolean,
    onDismissOverlayAndFocus: () -> Unit,
    onCenterAndZoomChange: (GeoPoint, Double) -> Unit,
): Modifier {
    val currentCenter by rememberUpdatedState(centerGcj)
    val currentZoom by rememberUpdatedState(zoom)
    val currentDismissOverlayOnly by rememberUpdatedState(consumeTapToDismissOverlayOnly)
    val currentOnDismissOverlay by rememberUpdatedState(onDismissOverlayAndFocus)
    val currentOnTransform by rememberUpdatedState(onCenterAndZoomChange)
    val callbacks = remember {
        MapGestureRuntimeCallbacks(
            centerProvider = { currentCenter },
            zoomProvider = { currentZoom },
            dismissOverlayOnlyProvider = { currentDismissOverlayOnly },
            onDismissOverlay = { currentOnDismissOverlay() },
            onTransform = { nextCenter, nextZoom -> currentOnTransform(nextCenter, nextZoom) },
        )
    }

    return this.pointerInput(baseTilePx, viewportWidthPx, viewportHeightPx, doubleTapSlopPx) {
        detectUnifiedMapGestures(
            baseTilePx = baseTilePx,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            doubleTapSlopPx = doubleTapSlopPx,
            callbacks = callbacks,
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectUnifiedMapGestures(
    baseTilePx: Double,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    doubleTapSlopPx: Float,
    callbacks: MapGestureRuntimeCallbacks,
) = coroutineScope {
    var pendingSingleTapJob: Job? = null
    var lastCleanTapTimeMs = 0L
    var lastCleanTapOffset = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = MapGestureSessionTracker(touchSlopPx = viewConfiguration.touchSlop)
        tracker.onFrame(
            pointers = listOf(MapPointerSample(down.id.value, down.position, down.position, down.pressed)),
            anyConsumedExternally = down.isConsumed,
        )

        val isSecondTapCandidate = pendingSingleTapJob?.isActive == true &&
            (down.uptimeMillis - lastCleanTapTimeMs) <= viewConfiguration.doubleTapTimeoutMillis &&
            (down.position - lastCleanTapOffset).getDistance() <= doubleTapSlopPx
        if (isSecondTapCandidate) {
            pendingSingleTapJob?.cancel()
        }

        var lastUpOffset = down.position
        var lastUpTimeMs = down.uptimeMillis
        do {
            val event = awaitPointerEvent()
            val anyExternalConsume = event.changes.any { it.isConsumed }
            val samples = event.changes.map { ch ->
                if (!ch.pressed) {
                    lastUpOffset = ch.position
                    lastUpTimeMs = ch.uptimeMillis
                }
                MapPointerSample(ch.id.value, ch.previousPosition, ch.position, ch.pressed)
            }
            val outcome = tracker.onFrame(samples, anyExternalConsume)
            if (tracker.hasTransformed || tracker.hadMultiTouch) {
                pendingSingleTapJob?.cancel()
                callbacks.onDismissOverlay()
                event.changes.forEach { ch -> if (ch.position != ch.previousPosition) ch.consume() }
            }
            if (outcome is MapGestureFrameOutcome.Transform) {
                applyGestureTransformFrame(
                    outcome = outcome,
                    baseTilePx = baseTilePx,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    callbacks = callbacks,
                )
            }
        } while (event.changes.any { it.pressed })

        if (tracker.canTriggerCleanSingleTap()) {
            val tapUpdate = dispatchCleanTapOutcome(
                scope = this@coroutineScope,
                isSecondTapCandidate = isSecondTapCandidate,
                upOffset = lastUpOffset,
                upTimeMs = lastUpTimeMs,
                doubleTapWaitMs = viewConfiguration.doubleTapTimeoutMillis,
                baseTilePx = baseTilePx,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                callbacks = callbacks,
            )
            lastCleanTapTimeMs = tapUpdate.first
            lastCleanTapOffset = tapUpdate.second
            pendingSingleTapJob = tapUpdate.third
        }
    }
}

private fun applyGestureTransformFrame(
    outcome: MapGestureFrameOutcome.Transform,
    baseTilePx: Double,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    callbacks: MapGestureRuntimeCallbacks,
) {
    val curZoom = callbacks.zoomProvider()
    val nextZoom = (curZoom + ln(outcome.zoomFactor.toDouble()) / ln(2.0))
        .coerceIn(SlippyMapProjection.MIN_ZOOM, SlippyMapProjection.MAX_ZOOM)
    val nextCenter = SlippyMapProjection.transformViewport(
        center = callbacks.centerProvider(),
        currentZoom = curZoom,
        nextZoom = nextZoom,
        baseTilePx = baseTilePx,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        centroid = outcome.centroid,
        pan = outcome.pan,
    )
    callbacks.onTransform(nextCenter, nextZoom)
}

@Suppress("LongParameterList")
private fun dispatchCleanTapOutcome(
    scope: kotlinx.coroutines.CoroutineScope,
    isSecondTapCandidate: Boolean,
    upOffset: Offset,
    upTimeMs: Long,
    doubleTapWaitMs: Long,
    baseTilePx: Double,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    callbacks: MapGestureRuntimeCallbacks,
): Triple<Long, Offset, Job?> {
    if (callbacks.dismissOverlayOnlyProvider()) {
        callbacks.onDismissOverlay()
        return Triple(0L, Offset.Zero, null)
    }
    if (isSecondTapCandidate) {
        val curZoom = callbacks.zoomProvider()
        val nextZoom = (curZoom + 1.0).coerceAtMost(SlippyMapProjection.MAX_ZOOM)
        val nextCenter = SlippyMapProjection.transformViewport(
            center = callbacks.centerProvider(),
            currentZoom = curZoom,
            nextZoom = nextZoom,
            baseTilePx = baseTilePx,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            centroid = upOffset,
            pan = Offset.Zero,
        )
        callbacks.onTransform(nextCenter, nextZoom)
        return Triple(0L, Offset.Zero, null)
    }
    val delayedJob = scope.launch {
        delay(doubleTapWaitMs)
        val dx = upOffset.x - viewportWidthPx / 2f
        val dy = upOffset.y - viewportHeightPx / 2f
        val curZoom = callbacks.zoomProvider()
        val nextCenter = SlippyMapProjection.panByPixels(
            center = callbacks.centerProvider(),
            zoom = curZoom,
            baseTilePx = baseTilePx,
            deltaXPx = -dx.toDouble(),
            deltaYPx = -dy.toDouble(),
        )
        callbacks.onTransform(nextCenter, curZoom)
    }
    return Triple(upTimeMs, upOffset, delayedJob)
}

@Composable
private fun CenterPinMarker(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.offset(y = (-18).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = "当前选点准星",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
        )
    }
}

@Composable
private fun SearchResultsOverlayCard(
    results: List<ResolvedPlace>,
    maxListHeight: Dp,
    onSelectPlace: (ResolvedPlace) -> Unit,
    onDismissResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ToolboxSpacing.medium, end = ToolboxSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "搜索候选（${results.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismissResults) {
                    Text(text = "收起")
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                items(results) { item ->
                    SearchResultCandidateRow(item = item, onSelect = { onSelectPlace(item) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SearchResultCandidateRow(item: ResolvedPlace, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.address.isNotBlank() && item.address != item.title) {
            Text(
                text = item.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MapFloatingControls(
    isLocatingCurrent: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onLocateMe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        horizontalAlignment = Alignment.End,
    ) {
        SmallFloatingActionButton(
            onClick = onLocateMe,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = if (isLocatingCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Icon(imageVector = Icons.Outlined.MyLocation, contentDescription = "定位当前位置")
        }
        SmallFloatingActionButton(onClick = onZoomIn, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(imageVector = Icons.Outlined.Add, contentDescription = "放大地图")
        }
        SmallFloatingActionButton(onClick = onZoomOut, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(imageVector = Icons.Outlined.Remove, contentDescription = "缩小地图")
        }
    }
}

@Composable
private fun MapPickerBottomConfirmPanel(
    selectedBd09: GeoPoint,
    hasConfirmedLocation: Boolean,
    targetBd09: GeoPoint?,
    targetRangeMeters: Int?,
    addressText: String,
    collapsedSummaryOnly: Boolean,
    ultraCompact: Boolean,
    maxPanelHeight: Dp,
    onAddressFocusChange: (Boolean) -> Unit,
    onAddressChange: (String) -> Unit,
    onOpenSavePreset: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val coordStatusText = formatBottomCoordinateLabel(
        selectedBd09 = selectedBd09,
        hasConfirmedLocation = hasConfirmedLocation,
        targetBd09 = targetBd09,
        targetRangeMeters = targetRangeMeters,
    )
    val panelPadding = if (ultraCompact || collapsedSummaryOnly) ToolboxSpacing.small else ToolboxSpacing.medium

    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = ToolboxRadius.card, topEnd = ToolboxRadius.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxPanelHeight)
                .verticalScroll(rememberScrollState())
                .padding(panelPadding),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Text(
                text = coordStatusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!collapsedSummaryOnly) {
                OutlinedTextField(
                    value = addressText,
                    onValueChange = onAddressChange,
                    label = { Text(text = "签到位置描述") },
                    placeholder = { Text(text = "拖动地图自动识别，或手动编辑地点名称") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onAddressFocusChange(it.isFocused) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
                ) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            onOpenSavePreset()
                        },
                        enabled = hasConfirmedLocation,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = " 存为预设")
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onConfirm()
                        },
                        enabled = hasConfirmedLocation,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "确认使用该地点")
                    }
                }
            }
        }
    }
}

private fun formatBottomCoordinateLabel(
    selectedBd09: GeoPoint,
    hasConfirmedLocation: Boolean,
    targetBd09: GeoPoint?,
    targetRangeMeters: Int?,
): String {
    val coordLabel = if (hasConfirmedLocation) {
        "已选坐标：${CoordinateConverter.formatCoordinate(selectedBd09.latitude)}, " +
            CoordinateConverter.formatCoordinate(selectedBd09.longitude)
    } else {
        "正在获取定位，请搜索地点或拖动地图选点"
    }
    val distanceHint = if (hasConfirmedLocation && targetBd09 != null) {
        val dist = CoordinateConverter.distanceMeters(selectedBd09, targetBd09).roundToInt()
        val status = if (targetRangeMeters != null && dist <= targetRangeMeters) "范围内" else "距目标 ${dist}m"
        " · $status"
    } else {
        ""
    }
    return coordLabel + distanceHint
}

@Composable
internal fun SavePresetNameDialog(initialTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var titleInput by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    val focusManager = LocalFocusManager.current
    val submitSave = {
        focusManager.clearFocus()
        onConfirm(titleInput.trim().ifBlank { "常用签到地点" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "保存预设签到地点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
                Text(
                    text = "为该地点设置一个便于识别的名称（例如：第三教学楼、东区体育场）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(text = "预设名称") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitSave() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submitSave) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        },
    )
}
