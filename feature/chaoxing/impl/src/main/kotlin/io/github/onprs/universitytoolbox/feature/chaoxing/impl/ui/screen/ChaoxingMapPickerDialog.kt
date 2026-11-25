package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FALLBACK_CHINA_CENTER_LAT = 35.86166
private const val FALLBACK_CHINA_CENTER_LON = 104.195397
private const val FALLBACK_OVERVIEW_ZOOM = 5.0
private const val REVERSE_GEOCODE_DEBOUNCE_MS = 450L
private const val TILE_CACHE_MAX_ENTRIES = 160
private const val MAX_PARENT_FALLBACK_STEPS = 3

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

private class MapViewportHolder(initialCenterGcj: GeoPoint, initialConfirmed: Boolean, initialAddress: String) {
    var centerGcj by mutableStateOf(initialCenterGcj)
    var zoom by mutableDoubleStateOf(
        if (initialConfirmed) SlippyMapProjection.DEFAULT_CAMPUS_ZOOM else FALLBACK_OVERVIEW_ZOOM,
    )
    var hasConfirmedLocation by mutableStateOf(initialConfirmed)
    var satelliteMode by mutableStateOf(false)
    var addressText by mutableStateOf(initialAddress)
    var showSavePresetPrompt by mutableStateOf(false)

    fun applyPlace(place: ResolvedPlace, fallbackTitle: Boolean = false) {
        centerGcj = place.gcj02Point
        zoom = SlippyMapProjection.DEFAULT_CAMPUS_ZOOM
        hasConfirmedLocation = true
        if (fallbackTitle) {
            addressText = place.address.ifBlank { place.title }
        } else if (place.address.isNotBlank()) {
            addressText = place.address
        }
    }

    fun updateCenterAndZoom(nextCenter: GeoPoint, nextZoom: Double) {
        centerGcj = nextCenter
        zoom = nextZoom
        hasConfirmedLocation = true
    }
}

/**
 * 全屏交互式地图手动选点对话框。
 * - 底图采用 GCJ-02 高密度中文瓦片 + 纯 Canvas 无约束拼接与父瓦片垫底渲染，消除缩放十字撕裂与模糊；
 * - 全屏容器接入 `.imePadding()`，确保软键盘弹起时底部位置描述输入框完整可见。
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
) {
    val viewport = remember(initialBd09Point, targetBd09Point, fallbackBd09Point) {
        val start = resolveInitialCenterSpec(initialBd09Point, targetBd09Point, fallbackBd09Point)
        MapViewportHolder(start.first, start.second, initialAddress)
    }

    MapPickerSideEffects(
        centerGcj = viewport.centerGcj,
        hasConfirmedLocation = viewport.hasConfirmedLocation,
        jumpCommand = jumpCommand,
        reverseGeocodedAddress = reverseGeocodedAddress,
        autoLocateOnOpen = initialBd09Point == null && targetBd09Point == null,
        autoSearchKeyword = requiredLocationHint?.takeIf { initialBd09Point == null && targetBd09Point == null },
        onJumpToPlace = { viewport.applyPlace(it) },
        onAddressResolved = { viewport.addressText = it },
        onRequestCurrentLocation = onRequestCurrentLocation,
        onReverseGeocodeCenter = onReverseGeocodeCenter,
        onQuickLocateKeyword = { keyword ->
            onExecuteSearch(keyword, viewport.centerGcj.takeIf { viewport.hasConfirmedLocation }, true)
        },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MapPickerScaffoldContent(
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
            onSearchQueryChange = { q ->
                onSearchQueryChange(q, viewport.centerGcj.takeIf { viewport.hasConfirmedLocation })
            },
            onExecuteSearch = { q, jumpFirst ->
                onExecuteSearch(q, viewport.centerGcj.takeIf { viewport.hasConfirmedLocation }, jumpFirst)
            },
            onConfirmSelection = onConfirmSelection,
            onSaveAsPresetAndSelect = onSaveAsPresetAndSelect,
            onRequestCurrentLocation = onRequestCurrentLocation,
            onDismiss = onDismiss,
        )
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
    centerGcj: GeoPoint,
    hasConfirmedLocation: Boolean,
    jumpCommand: MapJumpCommand?,
    reverseGeocodedAddress: String?,
    autoLocateOnOpen: Boolean,
    autoSearchKeyword: String?,
    onJumpToPlace: (ResolvedPlace) -> Unit,
    onAddressResolved: (String) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onReverseGeocodeCenter: (Double, Double) -> Unit,
    onQuickLocateKeyword: (String) -> Unit,
) {
    LaunchedEffect(jumpCommand?.sequence) {
        if (jumpCommand != null) {
            onJumpToPlace(jumpCommand.place)
        }
    }
    LaunchedEffect(reverseGeocodedAddress) {
        if (!reverseGeocodedAddress.isNullOrBlank()) {
            onAddressResolved(reverseGeocodedAddress)
        }
    }
    LaunchedEffect(centerGcj, hasConfirmedLocation) {
        if (hasConfirmedLocation) {
            delay(REVERSE_GEOCODE_DEBOUNCE_MS)
            onReverseGeocodeCenter(centerGcj.latitude, centerGcj.longitude)
        }
    }
    LaunchedEffect(Unit) {
        if (!autoSearchKeyword.isNullOrBlank()) {
            onQuickLocateKeyword(autoSearchKeyword)
        } else if (autoLocateOnOpen) {
            onRequestCurrentLocation()
        }
    }
}

@Composable
private fun MapPickerScaffoldContent(
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
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String, Boolean) -> Unit,
    onConfirmSelection: (GeoPoint, String) -> Unit,
    onSaveAsPresetAndSelect: (String, GeoPoint, String) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentBd09 = remember(viewport.centerGcj) {
        CoordinateConverter.gcj02ToBd09(viewport.centerGcj.latitude, viewport.centerGcj.longitude)
    }
    val targetGcj = remember(targetBd09) {
        targetBd09?.let { CoordinateConverter.bd09ToGcj02(it.latitude, it.longitude) }
    }
    if (viewport.showSavePresetPrompt) {
        SavePresetNameDialog(
            initialTitle = viewport.addressText.trim().ifBlank { requiredLocationHint.orEmpty() },
            onConfirm = { title ->
                viewport.showSavePresetPrompt = false
                onSaveAsPresetAndSelect(title, currentBd09, viewport.addressText.trim())
            },
            onDismiss = { viewport.showSavePresetPrompt = false },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            MapPickerTopSearchSection(
                searchQuery = searchQuery,
                satelliteMode = viewport.satelliteMode,
                isSearching = isSearching,
                hasCompletedSearch = hasCompletedSearch,
                hasSearchResults = searchResults.isNotEmpty(),
                requiredLocationHint = requiredLocationHint,
                statusMessage = statusMessage,
                onSearchQueryChange = onSearchQueryChange,
                onExecuteSearch = { onExecuteSearch(searchQuery, false) },
                onQuickSearchRequired = { hint ->
                    onSearchQueryChange(hint)
                    onExecuteSearch(hint, true)
                },
                onToggleLayer = { viewport.satelliteMode = !viewport.satelliteMode },
                onDismiss = onDismiss,
            )

            MapViewportLayerBox(
                viewport = viewport,
                targetGcj = targetGcj,
                targetRangeMeters = targetRangeMeters,
                searchResults = searchResults,
                isLocatingCurrent = isLocatingCurrent,
                onClearSearchQuery = { onSearchQueryChange("") },
                onRequestCurrentLocation = onRequestCurrentLocation,
                modifier = Modifier.weight(1f),
            )

            MapPickerBottomConfirmPanel(
                selectedBd09 = currentBd09,
                hasConfirmedLocation = viewport.hasConfirmedLocation,
                targetBd09 = targetBd09,
                targetRangeMeters = targetRangeMeters,
                addressText = viewport.addressText,
                onAddressChange = { viewport.addressText = it },
                onOpenSavePreset = { viewport.showSavePresetPrompt = true },
                onConfirm = { onConfirmSelection(currentBd09, viewport.addressText.trim()) },
            )
        }
    }
}

@Composable
private fun MapViewportLayerBox(
    viewport: MapViewportHolder,
    targetGcj: GeoPoint?,
    targetRangeMeters: Int?,
    searchResults: List<ResolvedPlace>,
    isLocatingCurrent: Boolean,
    onClearSearchQuery: () -> Unit,
    onRequestCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        InteractiveSlippyMapCanvas(
            centerGcj = viewport.centerGcj,
            zoom = viewport.zoom,
            satelliteMode = viewport.satelliteMode,
            targetGcj = targetGcj,
            targetRangeMeters = targetRangeMeters,
            onCenterAndZoomChange = viewport::updateCenterAndZoom,
        )

        if (searchResults.isNotEmpty()) {
            SearchResultsOverlayCard(
                results = searchResults,
                onSelectPlace = { place ->
                    viewport.applyPlace(place, fallbackTitle = true)
                    onClearSearchQuery()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
            )
        }

        MapFloatingControls(
            isLocatingCurrent = isLocatingCurrent,
            onZoomIn = {
                viewport.updateCenterAndZoom(
                    viewport.centerGcj,
                    (viewport.zoom + 1.0).coerceAtMost(SlippyMapProjection.MAX_ZOOM),
                )
            },
            onZoomOut = {
                viewport.updateCenterAndZoom(
                    viewport.centerGcj,
                    (viewport.zoom - 1.0).coerceAtLeast(SlippyMapProjection.MIN_ZOOM),
                )
            },
            onLocateMe = onRequestCurrentLocation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ToolboxSpacing.medium),
        )
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
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onQuickSearchRequired: (String) -> Unit,
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(text = "搜索学校、教学楼或地标名称") },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        SearchInputTrailingActions(
                            onClear = { onSearchQueryChange("") },
                            onSearch = {
                                focusManager.clearFocus()
                                onExecuteSearch()
                            },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onExecuteSearch()
                    },
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ToolboxSpacing.extraSmall),
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
                SearchWarningBanner(message = bannerHint)
            }
            if (isSearching) {
                ToolboxLinearProgressBar()
            }
        }
    }
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
private fun SearchWarningBanner(message: String) {
    Surface(
        color = ToolboxTheme.extendedColors.warningContainer,
        shape = RoundedCornerShape(ToolboxRadius.chip),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.extraSmall),
    ) {
        Text(
            text = message,
            color = ToolboxTheme.extendedColors.onWarningContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(
                horizontal = ToolboxSpacing.small,
                vertical = ToolboxSpacing.extraSmall,
            ),
        )
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
    onCenterAndZoomChange: (GeoPoint, Double) -> Unit,
) {
    val density = LocalDensity.current
    val baseTilePx = with(density) { SlippyMapProjection.BASE_TILE_DP.dp.toPx().toDouble() }
    val focusManager = LocalFocusManager.current
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
                .mapGestureModifier(
                    centerGcj = centerGcj,
                    zoom = zoom,
                    baseTilePx = baseTilePx,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    onClearFocus = { focusManager.clearFocus() },
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

@Composable
private fun Modifier.mapGestureModifier(
    centerGcj: GeoPoint,
    zoom: Double,
    baseTilePx: Double,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    onClearFocus: () -> Unit,
    onCenterAndZoomChange: (GeoPoint, Double) -> Unit,
): Modifier {
    val currentCenter by rememberUpdatedState(centerGcj)
    val currentZoom by rememberUpdatedState(zoom)
    return this
        .pointerInput(baseTilePx, viewportWidthPx, viewportHeightPx) {
            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                onClearFocus()
                val nextZoom = (currentZoom + ln(gestureZoom.toDouble()) / ln(2.0))
                    .coerceIn(SlippyMapProjection.MIN_ZOOM, SlippyMapProjection.MAX_ZOOM)
                val nextCenter = SlippyMapProjection.transformViewport(
                    center = currentCenter,
                    currentZoom = currentZoom,
                    nextZoom = nextZoom,
                    baseTilePx = baseTilePx,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    centroid = centroid,
                    pan = pan,
                )
                onCenterAndZoomChange(nextCenter, nextZoom)
            }
        }
        .pointerInput(baseTilePx, viewportWidthPx, viewportHeightPx) {
            detectTapGestures(
                onTap = { tapOffset ->
                    onClearFocus()
                    val dx = tapOffset.x - viewportWidthPx / 2f
                    val dy = tapOffset.y - viewportHeightPx / 2f
                    val nextCenter = SlippyMapProjection.panByPixels(
                        center = currentCenter,
                        zoom = currentZoom,
                        baseTilePx = baseTilePx,
                        deltaXPx = -dx.toDouble(),
                        deltaYPx = -dy.toDouble(),
                    )
                    onCenterAndZoomChange(nextCenter, currentZoom)
                },
                onDoubleTap = { tapOffset ->
                    onClearFocus()
                    val nextZoom = (currentZoom + 1.0).coerceAtMost(SlippyMapProjection.MAX_ZOOM)
                    val nextCenter = SlippyMapProjection.transformViewport(
                        center = currentCenter,
                        currentZoom = currentZoom,
                        nextZoom = nextZoom,
                        baseTilePx = baseTilePx,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        centroid = tapOffset,
                        pan = androidx.compose.ui.geometry.Offset.Zero,
                    )
                    onCenterAndZoomChange(nextCenter, nextZoom)
                },
            )
        }
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
    onSelectPlace: (ResolvedPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            items(results) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            focusManager.clearFocus()
                            onSelectPlace(item)
                        }
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
                HorizontalDivider()
            }
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
    onAddressChange: (String) -> Unit,
    onOpenSavePreset: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = ToolboxRadius.card, topEnd = ToolboxRadius.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            val coordLabel = if (hasConfirmedLocation) {
                "已选坐标：${CoordinateConverter.formatCoordinate(selectedBd09.latitude)}, " +
                    CoordinateConverter.formatCoordinate(selectedBd09.longitude)
            } else {
                "正在获取定位，也可直接搜索或拖动地图选点"
            }
            val distanceHint = if (hasConfirmedLocation && targetBd09 != null) {
                val dist = CoordinateConverter.distanceMeters(selectedBd09, targetBd09).roundToInt()
                val status = if (targetRangeMeters != null && dist <= targetRangeMeters) "范围内" else "距目标 ${dist}m"
                " · $status"
            } else {
                ""
            }
            Text(
                text = coordLabel + distanceHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = addressText,
                onValueChange = onAddressChange,
                label = { Text(text = "签到位置描述") },
                placeholder = { Text(text = "拖动地图自动识别，或手动编辑地点名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            ) {
                OutlinedButton(onClick = onOpenSavePreset, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = " 存为预设")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(text = "确认使用该地点")
                }
            }
        }
    }
}

@Composable
internal fun SavePresetNameDialog(initialTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var titleInput by remember(initialTitle) { mutableStateOf(initialTitle) }
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(titleInput.trim().ifBlank { "常用签到地点" }) }) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        },
    )
}
