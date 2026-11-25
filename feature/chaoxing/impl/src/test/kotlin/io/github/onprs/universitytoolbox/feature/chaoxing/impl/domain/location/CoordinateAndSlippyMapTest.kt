package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location

import androidx.compose.ui.geometry.Offset
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.SlippyMapProjection
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.formatRecordOutcomeLabel
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateAndSlippyMapTest {

    @Test
    fun `coordinate converter round trips between wgs84 gcj02 and bd09 with sub-meter precision`() {
        val wgsSample = GeoPoint(latitude = 36.001464, longitude = 120.116365)
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
        assertTrue(CoordinateConverter.isValidTargetCoordinate(36.007064, 120.128667))
    }

    @Test
    fun `slippy map projection generates gapless high-dpi tiles without viewport clipping`() {
        val center = GeoPoint(latitude = 36.007064, longitude = 120.128667)
        val (normX, normY) = SlippyMapProjection.latLonToNormalized(center.latitude, center.longitude)
        val restored = SlippyMapProjection.normalizedToLatLon(normX, normY)
        assertTrue(abs(restored.latitude - center.latitude) < 1e-6)
        assertTrue(abs(restored.longitude - center.longitude) < 1e-6)

        // 模拟高密度屏幕放大时单张瓦片超过视口宽度的极端场景（验证零裂缝无缝衔接）
        val tiles = SlippyMapProjection.computeVisibleTiles(
            center = center,
            zoom = 17.4,
            baseTilePx = 448.0,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1200f,
            satelliteMode = false,
        )
        assertTrue(tiles.isNotEmpty())
        assertEquals(17, tiles.first().z)
        assertTrue(tiles.all { it.url.startsWith("https://wprd0") && it.url.contains("style=7") })

        val byRow = tiles.groupBy { it.y }
        byRow.values.forEach { rowTiles ->
            val sorted = rowTiles.sortedBy { it.x }
            for (i in 0 until sorted.lastIndex) {
                assertEquals(sorted[i].offsetX + sorted[i].widthPx, sorted[i + 1].offsetX)
            }
        }
        val byCol = tiles.groupBy { it.x }
        byCol.values.forEach { colTiles ->
            val sorted = colTiles.sortedBy { it.y }
            for (i in 0 until sorted.lastIndex) {
                assertEquals(sorted[i].offsetY + sorted[i].heightPx, sorted[i + 1].offsetY)
            }
        }

        val satTiles = SlippyMapProjection.computeVisibleTiles(
            center = center,
            zoom = 18.8,
            baseTilePx = 384.0,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1200f,
            satelliteMode = true,
        )
        // 卫星图在 zoom > 18 时平滑锁定 z=18 超分放大并附带路网注记层
        assertEquals(18, satTiles.first().z)
        assertNotNull(satTiles.first().overlayUrl)
    }

    @Test
    fun `transformViewport preserves focal point geographic anchor during pinch zoom`() {
        val center = GeoPoint(latitude = 36.007064, longitude = 120.128667)
        val viewportW = 1080f
        val viewportH = 1400f
        val baseTilePx = 384.0
        val focalScreenOffset = Offset(310f, 420f)
        val targetBefore = SlippyMapProjection.panByPixels(
            center = center,
            zoom = 16.0,
            baseTilePx = baseTilePx,
            deltaXPx = (viewportW / 2f - focalScreenOffset.x).toDouble(),
            deltaYPx = (viewportH / 2f - focalScreenOffset.y).toDouble(),
        )

        val nextCenter = SlippyMapProjection.transformViewport(
            center = center,
            currentZoom = 16.0,
            nextZoom = 18.0,
            baseTilePx = baseTilePx,
            viewportWidthPx = viewportW,
            viewportHeightPx = viewportH,
            centroid = focalScreenOffset,
            pan = Offset.Zero,
        )
        val projectedAfter = SlippyMapProjection.pointToViewportOffset(
            target = targetBefore,
            center = nextCenter,
            zoom = 18.0,
            baseTilePx = baseTilePx,
            viewportWidthPx = viewportW,
            viewportHeightPx = viewportH,
        )
        assertTrue(abs(projectedAfter.x - focalScreenOffset.x) < 0.5f)
        assertTrue(abs(projectedAfter.y - focalScreenOffset.y) < 0.5f)
        assertEquals("签到成功", formatRecordOutcomeLabel("SUCCESS"))
        assertEquals("未通过", formatRecordOutcomeLabel("REJECTED"))
    }
}
