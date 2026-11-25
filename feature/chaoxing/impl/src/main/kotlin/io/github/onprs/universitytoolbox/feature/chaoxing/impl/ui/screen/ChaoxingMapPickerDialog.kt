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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLinearProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.CoordinateConverter
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.GeoPoint
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.ResolvedPlace
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 全屏交互式地图手动选点对话框。
 * 底图采用 GCJ-02 中文高清瓦片，选点确认时自动转换为超星签到所需的 BD-09 百度坐标系。
 */
@Composable
internal fun ChaoxingMapPickerDialog(
    initialBd09Point: GeoPoint?,
    targetBd09Point: GeoPoint?,
    targetRangeMeters: Int?,
    requiredLocationHint: String?,
    initialAddress: String,
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<ResolvedPlace>,
    isLocatingCurrent: Boolean,
    externalJumpPlace: ResolvedPlace?,
    reverseGeocodedAddress: String?,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onReverseGeocodeCenter: (Double, Double) -> Unit,
    onConfirmSelection: (bd09Point: GeoPoint, address: String) -> Unit,
    onSaveAsPresetAndSelect: (title: String, bd09Point: GeoPoint, address: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultGcj = remember(initialBd09Point, targetBd09Point) {
        resolveInitialGcjCenter(initialBd09Point, targetBd09Point)
    }
    var centerGcj by remember { mutableStateOf(defaultGcj) }
    var zoom by remember { mutableDoubleStateOf(SlippyMapProjection.DEFAULT_CAMPUS_ZOOM) }
    var satelliteMode by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf(initialAddress) }
    var showSavePresetPrompt by remember { mutableStateOf(false) }

    MapPickerSideEffects(
        centerGcj = centerGcj,
        externalJumpPlace = externalJumpPlace,
        reverseGeocodedAddress = reverseGeocodedAddress,
        autoSearchKeyword = requiredLocationHint?.takeIf { initialBd09Point == null && targetBd09Point == null },
        onJumpToPlace = { place ->
            centerGcj = place.gcj02Point
            zoom = SlippyMapProjection.DEFAULT_CAMPUS_ZOOM
            if (place.address.isNotBlank()) addressText = place.address
        },
        onAddressResolved = { resolved -> addressText = resolved },
        onReverseGeocodeCenter = onReverseGeocodeCenter,
        onExecuteSearch = onExecuteSearch,
    )

    val currentBd09 = remember(centerGcj) {
        CoordinateConverter.gcj02ToBd09(centerGcj.latitude, centerGcj.longitude)
    }
    val targetGcj = remember(targetBd09Point) {
        targetBd09Point?.let { CoordinateConverter.bd09ToGcj02(it.latitude, it.longitude) }
    }

    if (showSavePresetPrompt) {
        SavePresetNameDialog(
            initialTitle = addressText.trim().ifBlank { requiredLocationHint.orEmpty() },
            onConfirm = { title ->
                showSavePresetPrompt = false
                onSaveAsPresetAndSelect(title, currentBd09, addressText.trim())
            },
            onDismiss = { showSavePresetPrompt = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MapPickerScaffoldContent(
            centerGcj = centerGcj,
            zoom = zoom,
            satelliteMode = satelliteMode,
            currentBd09 = currentBd09,
            targetBd09 = targetBd09Point,
            targetGcj = targetGcj,
            targetRangeMeters = targetRangeMeters,
            requiredLocationHint = requiredLocationHint,
            addressText = addressText,
            searchQuery = searchQuery,
            isSearching = isSearching,
            searchResults = searchResults,
            isLocatingCurrent = isLocatingCurrent,
            onSearchQueryChange = onSearchQueryChange,
            onExecuteSearch = onExecuteSearch,
            onToggleLayer = { satelliteMode = !satelliteMode },
            onCenterAndZoomChange = { nextCenter, nextZoom ->
                centerGcj = nextCenter
                zoom = nextZoom
            },
            onSelectPlace = { place ->
                centerGcj = place.gcj02Point
                zoom = SlippyMapProjection.DEFAULT_CAMPUS_ZOOM
                addressText = place.address.ifBlank { place.title }
                onSearchQueryChange("")
            },
            onRequestCurrentLocation = onRequestCurrentLocation,
            onAddressChange = { addressText = it },
            onOpenSavePreset = { showSavePresetPrompt = true },
            onConfirm = { onConfirmSelection(currentBd09, addressText.trim()) },
            onDismiss = onDismiss,
        )
    }
}

private fun resolveInitialGcjCenter(initialBd09: GeoPoint?, targetBd09: GeoPoint?): GeoPoint = when {
    initialBd09 != null -> CoordinateConverter.bd09ToGcj02(initialBd09.latitude, initialBd09.longitude)
    targetBd09 != null -> CoordinateConverter.bd09ToGcj02(targetBd09.latitude, targetBd09.longitude)
    else -> GeoPoint(latitude = DEFAULT_CENTER_LAT, longitude = DEFAULT_CENTER_LON)
}

private const val DEFAULT_CENTER_LAT = 39.908722
private const val DEFAULT_CENTER_LON = 116.397499

@Composable
private fun MapPickerSideEffects(
    centerGcj: GeoPoint,
    externalJumpPlace: ResolvedPlace?,
    reverseGeocodedAddress: String?,
    autoSearchKeyword: String?,
    onJumpToPlace: (ResolvedPlace) -> Unit,
    onAddressResolved: (String) -> Unit,
    onReverseGeocodeCenter: (Double, Double) -> Unit,
    onExecuteSearch: (String) -> Unit,
) {
    LaunchedEffect(externalJumpPlace) {
        if (externalJumpPlace != null) onJumpToPlace(externalJumpPlace)
    }
    LaunchedEffect(reverseGeocodedAddress) {
        if (!reverseGeocodedAddress.isNullOrBlank()) onAddressResolved(reverseGeocodedAddress)
    }
    LaunchedEffect(centerGcj) {
        delay(450L)
        onReverseGeocodeCenter(centerGcj.latitude, centerGcj.longitude)
    }
    LaunchedEffect(Unit) {
        if (!autoSearchKeyword.isNullOrBlank()) onExecuteSearch(autoSearchKeyword)
    }
}

@Composable
private fun MapPickerScaffoldContent(
    centerGcj: GeoPoint,
    zoom: Double,
    satelliteMode: Boolean,
    currentBd09: GeoPoint,
    targetBd09: GeoPoint?,
    targetGcj: GeoPoint?,
    targetRangeMeters: Int?,
    requiredLocationHint: String?,
    addressText: String,
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<ResolvedPlace>,
    isLocatingCurrent: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onToggleLayer: () -> Unit,
    onCenterAndZoomChange: (GeoPoint, Double) -> Unit,
    onSelectPlace: (ResolvedPlace) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onAddressChange: (String) -> Unit,
    onOpenSavePreset: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            MapPickerTopSearchSection(
                searchQuery = searchQuery,
                satelliteMode = satelliteMode,
                isSearching = isSearching,
                requiredLocationHint = requiredLocationHint,
                onSearchQueryChange = onSearchQueryChange,
                onExecuteSearch = { onExecuteSearch(searchQuery) },
                onQuickSearchRequired = { hint ->
                    onSearchQueryChange(hint)
                    onExecuteSearch(hint)
                },
                onToggleLayer = onToggleLayer,
                onDismiss = onDismiss,
            )

            Box(modifier = Modifier.weight(1f)) {
                InteractiveSlippyMapCanvas(
                    centerGcj = centerGcj,
                    zoom = zoom,
                    satelliteMode = satelliteMode,
                    targetGcj = targetGcj,
                    targetRangeMeters = targetRangeMeters,
                    onCenterAndZoomChange = onCenterAndZoomChange,
                )

                if (searchResults.isNotEmpty()) {
                    SearchResultsOverlayCard(
                        results = searchResults,
                        onSelectPlace = onSelectPlace,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
                    )
                }

                MapFloatingControls(
                    isLocatingCurrent = isLocatingCurrent,
                    onZoomIn = {
                        onCenterAndZoomChange(centerGcj, (zoom + 1.0).coerceAtMost(SlippyMapProjection.MAX_ZOOM))
                    },
                    onZoomOut = {
                        onCenterAndZoomChange(centerGcj, (zoom - 1.0).coerceAtLeast(SlippyMapProjection.MIN_ZOOM))
                    },
                    onLocateMe = onRequestCurrentLocation,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(ToolboxSpacing.medium),
                )
            }

            MapPickerBottomConfirmPanel(
                selectedBd09 = currentBd09,
                targetBd09 = targetBd09,
                targetRangeMeters = targetRangeMeters,
                addressText = addressText,
                onAddressChange = onAddressChange,
                onOpenSavePreset = onOpenSavePreset,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun MapPickerTopSearchSection(
    searchQuery: String,
    satelliteMode: Boolean,
    isSearching: Boolean,
    requiredLocationHint: String?,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onQuickSearchRequired: (String) -> Unit,
    onToggleLayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
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
                    if (searchQuery.isNotBlank()) {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                onExecuteSearch()
                            },
                        ) { Text(text = "搜索") }
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
            if (isSearching) {
                ToolboxLinearProgressBar()
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
    onCenterAndZoomChange: (GeoPoint, Double) -> Unit,
) {
    val density = LocalDensity.current
    val baseTilePx = with(density) { 256.dp.toPx().toDouble() }
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clipToBounds(),
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val tiles = remember(centerGcj, zoom, viewportWidthPx, viewportHeightPx, satelliteMode) {
            SlippyMapProjection.computeVisibleTiles(
                center = centerGcj,
                zoom = zoom,
                baseTilePx = baseTilePx,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                satelliteMode = satelliteMode,
            )
        }

        Box(
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
            tiles.forEach { tile ->
                key(tile.z, tile.x, tile.y, satelliteMode) {
                    val sizeDp = with(density) { tile.sizePx.toDp() }
                    AsyncImage(
                        model = tile.url,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset { IntOffset(tile.offsetX, tile.offsetY) }
                            .size(sizeDp),
                    )
                }
            }
            if (targetGcj != null) {
                TargetRangeOverlay(
                    targetGcj = targetGcj,
                    centerGcj = centerGcj,
                    zoom = zoom,
                    baseTilePx = baseTilePx,
                    targetRangeMeters = targetRangeMeters,
                )
            }
        }

        CenterPinMarker(modifier = Modifier.align(Alignment.Center))
    }
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
        .pointerInput(baseTilePx) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                onClearFocus()
                val nextZoom = (currentZoom + ln(gestureZoom.toDouble()) / ln(2.0))
                    .coerceIn(SlippyMapProjection.MIN_ZOOM, SlippyMapProjection.MAX_ZOOM)
                val nextCenter = SlippyMapProjection.panByPixels(
                    center = currentCenter,
                    zoom = nextZoom,
                    baseTilePx = baseTilePx,
                    deltaXPx = pan.x.toDouble(),
                    deltaYPx = pan.y.toDouble(),
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
                onDoubleTap = {
                    val nextZoom = (currentZoom + 1.0).coerceAtMost(SlippyMapProjection.MAX_ZOOM)
                    onCenterAndZoomChange(currentCenter, nextZoom)
                },
            )
        }
}

@Composable
private fun TargetRangeOverlay(
    targetGcj: GeoPoint,
    centerGcj: GeoPoint,
    zoom: Double,
    baseTilePx: Double,
    targetRangeMeters: Int?,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
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
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            items(results) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlace(item) }
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
            val coordLabel = "已选坐标：${CoordinateConverter.formatCoordinate(selectedBd09.latitude)}, " +
                CoordinateConverter.formatCoordinate(selectedBd09.longitude)
            val distanceHint = if (targetBd09 != null) {
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
