package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import androidx.compose.ui.geometry.Offset
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

/**
 * 标准 Web 墨卡托 XYZ 瓦片投影纯数学计算器。
 * - 采用高密度基准瓦片逻辑尺寸（128dp）与就近四舍五入层级选择（`round(zoom)`），使移动端渲染清晰度提升 4 倍且消除过度拉伸模糊；
 * - 使用像素边界精确差分计算每张瓦片的绘制宽高，彻底消除瓦片拼接缝隙。
 */
internal object SlippyMapProjection {
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 19.5
    const val DEFAULT_CAMPUS_ZOOM = 17.5
    const val BASE_TILE_DP = 128

    private const val MIN_TILE_Z = 3
    private const val MAX_STANDARD_TILE_Z = 19
    private const val MAX_SATELLITE_TILE_Z = 18
    private const val FULL_LON_SPAN = 360.0
    private const val HALF_LON_SPAN = 180.0
    private const val MAX_MERCATOR_LAT = 85.05112878
    private const val HALF_UNIT = 0.5
    private const val TWO_DOUBLE = 2.0
    private const val TWO_FLOAT = 2f
    private const val FOUR_DOUBLE = 4.0
    private const val EQUATOR_RADIUS_METERS = 6378137.0
    private const val SUBDOMAIN_MODULO = 4

    data class TileSpec(
        val z: Int,
        val x: Int,
        val y: Int,
        val offsetX: Int,
        val offsetY: Int,
        val widthPx: Int,
        val heightPx: Int,
        val url: String,
        val overlayUrl: String? = null,
    )

    fun latLonToNormalized(latitude: Double, longitude: Double): Pair<Double, Double> {
        val clampedLat = latitude.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val wrappedLon = (((longitude + HALF_LON_SPAN) % FULL_LON_SPAN + FULL_LON_SPAN) % FULL_LON_SPAN) -
            HALF_LON_SPAN
        val normX = (wrappedLon + HALF_LON_SPAN) / FULL_LON_SPAN
        val sinLat = sin(Math.toRadians(clampedLat))
        val normY = HALF_UNIT - ln((1.0 + sinLat) / (1.0 - sinLat)) / (FOUR_DOUBLE * PI)
        return normX.coerceIn(0.0, 1.0) to normY.coerceIn(0.0, 1.0)
    }

    fun normalizedToLatLon(normX: Double, normY: Double): GeoPoint {
        val clampedX = normX.coerceIn(0.0, 1.0)
        val clampedY = normY.coerceIn(0.0, 1.0)
        val lon = clampedX * FULL_LON_SPAN - HALF_LON_SPAN
        val n = PI - TWO_DOUBLE * PI * clampedY
        val lat = Math.toDegrees(atan(sinh(n)))
        return GeoPoint(latitude = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT), longitude = lon)
    }

    fun panByPixels(center: GeoPoint, zoom: Double, baseTilePx: Double, deltaXPx: Double, deltaYPx: Double): GeoPoint {
        val worldSize = worldSizePx(zoom, baseTilePx)
        val (normX, normY) = latLonToNormalized(center.latitude, center.longitude)
        val nextX = (normX * worldSize - deltaXPx) / worldSize
        val nextY = (normY * worldSize - deltaYPx) / worldSize
        return normalizedToLatLon(nextX, nextY)
    }

    /**
     * 以手势焦点 [centroid] 为锚点同时执行双指缩放与平移，保证缩放过程中手指指向的地面位置不漂移。
     */
    @Suppress("LongParameterList")
    fun transformViewport(
        center: GeoPoint,
        currentZoom: Double,
        nextZoom: Double,
        baseTilePx: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        centroid: Offset,
        pan: Offset,
    ): GeoPoint {
        val oldWorldSize = worldSizePx(currentZoom, baseTilePx)
        val newWorldSize = worldSizePx(nextZoom, baseTilePx)
        val (centerNormX, centerNormY) = latLonToNormalized(center.latitude, center.longitude)

        // 手势发生前焦点 (centroid - pan) 对应的归一化世界坐标（Compose transformGesture 中 centroid 已包含当前帧 pan）
        val preCentroidX = centroid.x - pan.x
        val preCentroidY = centroid.y - pan.y
        val anchorNormX = centerNormX + (preCentroidX - viewportWidthPx / TWO_FLOAT) / oldWorldSize
        val anchorNormY = centerNormY + (preCentroidY - viewportHeightPx / TWO_FLOAT) / oldWorldSize

        // 缩放并平移后使 anchorNorm 落在最新 centroid 位置
        val nextCenterNormX = anchorNormX - (centroid.x - viewportWidthPx / TWO_FLOAT) / newWorldSize
        val nextCenterNormY = anchorNormY - (centroid.y - viewportHeightPx / TWO_FLOAT) / newWorldSize
        return normalizedToLatLon(nextCenterNormX, nextCenterNormY)
    }

    fun pointToViewportOffset(
        target: GeoPoint,
        center: GeoPoint,
        zoom: Double,
        baseTilePx: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): Offset {
        val worldSize = worldSizePx(zoom, baseTilePx)
        val (centerX, centerY) = latLonToNormalized(center.latitude, center.longitude)
        val (targetX, targetY) = latLonToNormalized(target.latitude, target.longitude)
        val dx = ((targetX - centerX) * worldSize).toFloat()
        val dy = ((targetY - centerY) * worldSize).toFloat()
        return Offset(viewportWidthPx / TWO_FLOAT + dx, viewportHeightPx / TWO_FLOAT + dy)
    }

    fun metersToPixels(meters: Int, latitude: Double, zoom: Double, baseTilePx: Double): Float {
        val worldSize = worldSizePx(zoom, baseTilePx)
        val metersPerPixel = cos(Math.toRadians(latitude)) * TWO_DOUBLE * PI * EQUATOR_RADIUS_METERS / worldSize
        return if (metersPerPixel > 0.0) (meters / metersPerPixel).toFloat() else 0f
    }

    fun computeVisibleTiles(
        center: GeoPoint,
        zoom: Double,
        baseTilePx: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        satelliteMode: Boolean,
    ): List<TileSpec> {
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f || baseTilePx <= 0.0) {
            return emptyList()
        }
        val clampedZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val maxTileZ = if (satelliteMode) MAX_SATELLITE_TILE_Z else MAX_STANDARD_TILE_Z
        val tileZ = clampedZoom.roundToInt().coerceIn(MIN_TILE_Z, maxTileZ)
        val numTiles = 1 shl tileZ
        val scaledTilePx = baseTilePx * TWO_DOUBLE.pow(clampedZoom - tileZ)
        val worldSize = numTiles * scaledTilePx
        val (centerNormX, centerNormY) = latLonToNormalized(center.latitude, center.longitude)
        val leftPx = centerNormX * worldSize - viewportWidthPx / TWO_DOUBLE
        val topPx = centerNormY * worldSize - viewportHeightPx / TWO_DOUBLE
        val minX = floor(leftPx / scaledTilePx).toInt().coerceIn(0, numTiles - 1)
        val maxX = floor((leftPx + viewportWidthPx) / scaledTilePx).toInt().coerceIn(0, numTiles - 1)
        val minY = floor(topPx / scaledTilePx).toInt().coerceIn(0, numTiles - 1)
        val maxY = floor((topPx + viewportHeightPx) / scaledTilePx).toInt().coerceIn(0, numTiles - 1)

        val tiles = ArrayList<TileSpec>((maxX - minX + 1) * (maxY - minY + 1))
        for (ty in minY..maxY) {
            val topEdge = (ty * scaledTilePx - topPx).roundToInt()
            val bottomEdge = ((ty + 1) * scaledTilePx - topPx).roundToInt()
            val heightPx = (bottomEdge - topEdge).coerceAtLeast(1)
            for (tx in minX..maxX) {
                val leftEdge = (tx * scaledTilePx - leftPx).roundToInt()
                val rightEdge = ((tx + 1) * scaledTilePx - leftPx).roundToInt()
                val widthPx = (rightEdge - leftEdge).coerceAtLeast(1)
                val subdomain = ((tx + ty).mod(SUBDOMAIN_MODULO)) + 1
                tiles += TileSpec(
                    z = tileZ,
                    x = tx,
                    y = ty,
                    offsetX = leftEdge,
                    offsetY = topEdge,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    url = buildBaseTileUrl(subdomain, tileZ, tx, ty, satelliteMode),
                    overlayUrl = if (satelliteMode) buildSatelliteOverlayUrl(subdomain, tileZ, tx, ty) else null,
                )
            }
        }
        return tiles
    }

    fun buildTileUrlForKey(z: Int, x: Int, y: Int, satellite: Boolean): String {
        val subdomain = ((x + y).mod(SUBDOMAIN_MODULO)) + 1
        return buildBaseTileUrl(subdomain, z, x, y, satellite)
    }

    private fun worldSizePx(zoom: Double, baseTilePx: Double): Double {
        val clampedZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return TWO_DOUBLE.pow(clampedZoom) * baseTilePx
    }

    private fun buildBaseTileUrl(subdomain: Int, z: Int, x: Int, y: Int, satellite: Boolean): String = if (satellite) {
        "https://wprd0$subdomain.is.autonavi.com/appmaptile?style=6&x=$x&y=$y&z=$z"
    } else {
        "https://wprd0$subdomain.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scl=1&style=7&x=$x&y=$y&z=$z"
    }

    private fun buildSatelliteOverlayUrl(subdomain: Int, z: Int, x: Int, y: Int): String =
        "https://wprd0$subdomain.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scl=1&style=8&x=$x&y=$y&z=$z"
}

/**
 * 单帧触点采样数据（解耦 Compose PointerInputChange 以便纯算法单测与手势协调复用）。
 */
internal data class MapPointerSample(
    val pointerId: Long,
    val previousPosition: Offset,
    val currentPosition: Offset,
    val pressed: Boolean,
)

internal sealed interface MapGestureFrameOutcome {
    data object Idle : MapGestureFrameOutcome

    data class Transform(val centroid: Offset, val pan: Offset, val zoomFactor: Float) : MapGestureFrameOutcome
}

/**
 * 单次地图触摸会话协调器（从首指按下到全部手指抬起）：
 * - 一旦出现多指触控，全程屏蔽单击/双击选点，杜绝双指轻捏松手误触点击瞬移；
 * - 在手指增减切换帧（如 1 指变 2 指、2 指松开变 1 指）自动重锚定并跳过质心突变差分；
 * - 双指缩放松开其中一指后，对剩余单指施加抬手防抖阈值，避免松手瞬间选点准星漂移。
 */
internal class MapGestureSessionTracker(private val touchSlopPx: Float) {
    private val seenPointerIds = HashSet<Long>()
    private var previousActiveIds: Set<Long> = emptySet()
    private var accumulatedPan: Offset = Offset.Zero
    private var accumulatedZoom: Float = IDENTITY_ZOOM
    private var postPinchResidualPan: Offset = Offset.Zero
    private var postPinchLocked: Boolean = false

    var hadMultiTouch: Boolean = false
        private set
    var hasTransformed: Boolean = false
        private set
    var wasCanceled: Boolean = false
        private set

    fun onFrame(pointers: List<MapPointerSample>, anyConsumedExternally: Boolean = false): MapGestureFrameOutcome {
        if (anyConsumedExternally) {
            wasCanceled = true
            return MapGestureFrameOutcome.Idle
        }
        val active = pointers.filter { it.pressed }
        active.forEach { seenPointerIds.add(it.pointerId) }
        if (active.size >= MULTI_TOUCH_COUNT || seenPointerIds.size >= MULTI_TOUCH_COUNT) {
            hadMultiTouch = true
        }
        if (active.isEmpty()) {
            previousActiveIds = emptySet()
            return MapGestureFrameOutcome.Idle
        }

        val currentIds = active.mapTo(HashSet(active.size)) { it.pointerId }
        if (previousActiveIds.isEmpty() || currentIds != previousActiveIds) {
            if (previousActiveIds.size >= MULTI_TOUCH_COUNT && currentIds.size == 1) {
                postPinchLocked = true
                postPinchResidualPan = Offset.Zero
            } else if (currentIds.size >= MULTI_TOUCH_COUNT) {
                postPinchLocked = false
            }
            previousActiveIds = currentIds
            return MapGestureFrameOutcome.Idle
        }

        return if (hadMultiTouch && active.size == 1 && postPinchLocked) {
            handlePostPinchSinglePointer(active.first())
        } else {
            computeStableFrameOutcome(active)
        }
    }

    fun canTriggerCleanSingleTap(): Boolean =
        !wasCanceled && !hadMultiTouch && !hasTransformed && seenPointerIds.size == 1

    private fun handlePostPinchSinglePointer(pointer: MapPointerSample): MapGestureFrameOutcome {
        val delta = pointer.currentPosition - pointer.previousPosition
        postPinchResidualPan += delta
        if (postPinchResidualPan.getDistance() <= touchSlopPx * POST_PINCH_UNLOCK_MULTIPLIER) {
            return MapGestureFrameOutcome.Idle
        }
        postPinchLocked = false
        hasTransformed = true
        return if (delta != Offset.Zero) {
            MapGestureFrameOutcome.Transform(
                centroid = pointer.currentPosition,
                pan = delta,
                zoomFactor = IDENTITY_ZOOM,
            )
        } else {
            MapGestureFrameOutcome.Idle
        }
    }

    private fun computeStableFrameOutcome(active: List<MapPointerSample>): MapGestureFrameOutcome {
        val prevCentroid = averagePosition(active) { it.previousPosition }
        val currCentroid = averagePosition(active) { it.currentPosition }
        val pan = currCentroid - prevCentroid
        val zoomFactor = if (active.size >= MULTI_TOUCH_COUNT) {
            val prevSpan = averageSpan(active, prevCentroid) { it.previousPosition }
            val currSpan = averageSpan(active, currCentroid) { it.currentPosition }
            if (prevSpan >= MIN_PINCH_SPAN_PX) currSpan / prevSpan else IDENTITY_ZOOM
        } else {
            IDENTITY_ZOOM
        }

        if (!hasTransformed) {
            accumulatedPan += pan
            accumulatedZoom *= zoomFactor
            val currSpan = if (active.size >= MULTI_TOUCH_COUNT) {
                averageSpan(active, currCentroid) { it.currentPosition }
            } else {
                0f
            }
            val zoomMotion = abs(accumulatedZoom - IDENTITY_ZOOM) * currSpan
            if (accumulatedPan.getDistance() > touchSlopPx || zoomMotion > touchSlopPx) {
                hasTransformed = true
            }
        }

        return if (hasTransformed && (pan != Offset.Zero || zoomFactor != IDENTITY_ZOOM)) {
            MapGestureFrameOutcome.Transform(centroid = currCentroid, pan = pan, zoomFactor = zoomFactor)
        } else {
            MapGestureFrameOutcome.Idle
        }
    }

    private inline fun averagePosition(items: List<MapPointerSample>, selector: (MapPointerSample) -> Offset): Offset {
        var sumX = 0f
        var sumY = 0f
        items.forEach { sample ->
            val pos = selector(sample)
            sumX += pos.x
            sumY += pos.y
        }
        return Offset(sumX / items.size, sumY / items.size)
    }

    private inline fun averageSpan(
        items: List<MapPointerSample>,
        centroid: Offset,
        selector: (MapPointerSample) -> Offset,
    ): Float {
        var sumDist = 0f
        items.forEach { sample ->
            sumDist += (selector(sample) - centroid).getDistance()
        }
        return sumDist / items.size
    }

    companion object {
        private const val MULTI_TOUCH_COUNT = 2
        private const val IDENTITY_ZOOM = 1f
        private const val MIN_PINCH_SPAN_PX = 8f
        private const val POST_PINCH_UNLOCK_MULTIPLIER = 1.5f
    }
}
