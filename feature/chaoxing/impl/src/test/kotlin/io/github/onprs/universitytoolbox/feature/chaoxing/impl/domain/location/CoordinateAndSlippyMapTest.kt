package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location

import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.SlippyMapProjection
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateAndSlippyMapTest {

    @Test
    fun `coordinate converter round trips between wgs84 gcj02 and bd09 with sub-meter precision`() {
        val wgsSample = GeoPoint(latitude = 40.002291, longitude = 116.320963)
        val gcj = CoordinateConverter.wgs84ToGcj02(wgsSample.latitude, wgsSample.longitude)
        val bd = CoordinateConverter.gcj02ToBd09(gcj.latitude, gcj.longitude)

        // 国内坐标偏移后与原始 WGS-84 应有数百米级偏移
        assertTrue(CoordinateConverter.distanceMeters(wgsSample, bd) > 300.0)

        val gcjBack = CoordinateConverter.bd09ToGcj02(bd.latitude, bd.longitude)
        assertTrue(abs(gcjBack.latitude - gcj.latitude) < 1e-5)
        assertTrue(abs(gcjBack.longitude - gcj.longitude) < 1e-5)

        val wgsBack = CoordinateConverter.gcj02ToWgs84(gcjBack.latitude, gcjBack.longitude)
        assertTrue(CoordinateConverter.distanceMeters(wgsSample, wgsBack) < 5.0)
    }

    @Test
    fun `target coordinate validator rejects zero and negative one placeholders`() {
        assertFalse(CoordinateConverter.isValidTargetCoordinate(null, null))
        assertFalse(CoordinateConverter.isValidTargetCoordinate(0.0, 0.0))
        assertFalse(CoordinateConverter.isValidTargetCoordinate(-1.0, -1.0))
        assertTrue(CoordinateConverter.isValidTargetCoordinate(39.915119, 116.403963))
    }

    @Test
    fun `slippy map projection round trips coordinates and generates contiguous tiles`() {
        val center = GeoPoint(latitude = 39.908722, longitude = 116.397499)
        val (normX, normY) = SlippyMapProjection.latLonToNormalized(center.latitude, center.longitude)
        val restored = SlippyMapProjection.normalizedToLatLon(normX, normY)
        assertTrue(abs(restored.latitude - center.latitude) < 1e-6)
        assertTrue(abs(restored.longitude - center.longitude) < 1e-6)

        val panned = SlippyMapProjection.panByPixels(
            center = center,
            zoom = 16.0,
            baseTilePx = 256.0,
            deltaXPx = 128.0,
            deltaYPx = -64.0,
        )
        val pannedBack = SlippyMapProjection.panByPixels(
            center = panned,
            zoom = 16.0,
            baseTilePx = 256.0,
            deltaXPx = -128.0,
            deltaYPx = 64.0,
        )
        assertTrue(abs(pannedBack.latitude - center.latitude) < 1e-6)
        assertTrue(abs(pannedBack.longitude - center.longitude) < 1e-6)

        val tiles = SlippyMapProjection.computeVisibleTiles(
            center = center,
            zoom = 16.5,
            baseTilePx = 256.0,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1920f,
            satelliteMode = false,
        )
        assertTrue(tiles.isNotEmpty())
        assertEquals(16, tiles.first().z)
        assertTrue(tiles.all { it.url.startsWith("https://webrd0") })
    }
}
