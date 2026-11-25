package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import androidx.compose.ui.geometry.Offset
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

/**
 * 标准 Web 墨卡托 XYZ 瓦片投影纯数学计算器。
 */
internal object SlippyMapProjection {
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 18.0
    const val DEFAULT_CAMPUS_ZOOM = 16.5
    private const val MIN_TILE_Z = 3
    private const val MAX_TILE_Z = 18
    private const val FULL_LON_SPAN = 360.0
    private const val HALF_LON_SPAN = 180.0
    private const val MAX_MERCATOR_LAT = 85.05112878
    private const val HALF_UNIT = 0.5
    private const val TWO_DOUBLE = 2.0
    private const val FOUR_DOUBLE = 4.0
    private const val EQUATOR_RADIUS_METERS = 6378137.0
    private const val SUBDOMAIN_MODULO = 4

    data class TileSpec(
        val z: Int,
        val x: Int,
        val y: Int,
        val offsetX: Int,
        val offsetY: Int,
        val sizePx: Int,
        val url: String,
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
        return Offset(viewportWidthPx / 2f + dx, viewportHeightPx / 2f + dy)
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
        val tileZ = floor(zoom).toInt().coerceIn(MIN_TILE_Z, MAX_TILE_Z)
        val numTiles = 1 shl tileZ
        val scaledTilePx = baseTilePx * TWO_DOUBLE.pow(zoom - tileZ)
        val worldSize = numTiles * scaledTilePx
        val (centerNormX, centerNormY) = latLonToNormalized(center.latitude, center.longitude)
        val leftPx = centerNormX * worldSize - viewportWidthPx / TWO_DOUBLE
        val topPx = centerNormY * worldSize - viewportHeightPx / TWO_DOUBLE
        val minX = floor(leftPx / scaledTilePx).toInt().coerceIn(0, numTiles - 1)
        val maxX = floor((leftPx + viewportWidthPx) / scaledTilePx).toInt().coerceIn(0, numTiles - 1)
        val minY = floor(topPx / scaledTilePx).toInt().coerceIn(0, numTiles - 1)
        val maxY = floor((topPx + viewportHeightPx) / scaledTilePx).toInt().coerceIn(0, numTiles - 1)
        val tileDrawSize = ceil(scaledTilePx).toInt() + 1

        val tiles = ArrayList<TileSpec>((maxX - minX + 1) * (maxY - minY + 1))
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val subdomain = ((tx + ty).mod(SUBDOMAIN_MODULO)) + 1
                tiles += TileSpec(
                    z = tileZ,
                    x = tx,
                    y = ty,
                    offsetX = (tx * scaledTilePx - leftPx).roundToInt(),
                    offsetY = (ty * scaledTilePx - topPx).roundToInt(),
                    sizePx = tileDrawSize,
                    url = buildAmapTileUrl(subdomain, tileZ, tx, ty, satelliteMode),
                )
            }
        }
        return tiles
    }

    private fun worldSizePx(zoom: Double, baseTilePx: Double): Double {
        val clampedZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val tileZ = floor(clampedZoom).toInt().coerceIn(MIN_TILE_Z, MAX_TILE_Z)
        return (1 shl tileZ) * baseTilePx * TWO_DOUBLE.pow(clampedZoom - tileZ)
    }

    private fun buildAmapTileUrl(subdomain: Int, z: Int, x: Int, y: Int, satellite: Boolean): String = if (satellite) {
        "https://webst0$subdomain.is.autonavi.com/appmaptile?style=6&x=$x&y=$y&z=$z"
    } else {
        "https://webrd0$subdomain.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=2&style=8&x=$x&y=$y&z=$z"
    }
}
