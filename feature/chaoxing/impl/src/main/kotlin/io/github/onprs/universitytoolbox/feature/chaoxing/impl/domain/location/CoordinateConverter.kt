package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理经纬度坐标点。
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 标准地理坐标系转换工具（支持 WGS-84、GCJ-02 火星坐标系与超星签到使用的 BD-09 百度坐标系互转）。
 */
object CoordinateConverter {
    private const val PI = Math.PI
    private const val X_PI = PI * 3000.0 / 180.0
    private const val SEMI_MAJOR_AXIS = 6378245.0
    private const val ECCENTRICITY_SQUARED = 0.006693421622965943
    private const val EARTH_RADIUS_METERS = 6371000.0

    private const val CHINA_MIN_LON = 72.004
    private const val CHINA_MAX_LON = 137.8347
    private const val CHINA_MIN_LAT = 0.8293
    private const val CHINA_MAX_LAT = 55.8271

    private const val BD_LON_OFFSET = 0.0065
    private const val BD_LAT_OFFSET = 0.006
    private const val BD_Z_DELTA = 0.00002
    private const val BD_THETA_DELTA = 0.000003

    private const val DEG_HALF_CIRCLE = 180.0
    private const val DEG_DOUBLE = 2.0
    private const val GCJ_OFFSET_BASE_LON = 105.0
    private const val GCJ_OFFSET_BASE_LAT = 35.0
    private const val COORD_SCALE_SIX = 6.0
    private const val COORD_SCALE_TWELVE = 12.0
    private const val COORD_SCALE_THIRTY = 30.0
    private const val COORD_FACTOR_TWENTY = 20.0
    private const val COORD_FACTOR_FORTY = 40.0
    private const val COORD_FACTOR_ONE_FIFTY = 150.0
    private const val COORD_FACTOR_ONE_SIXTY = 160.0
    private const val COORD_FACTOR_THREE_HUNDRED = 300.0
    private const val COORD_FACTOR_THREE_TWENTY = 320.0
    private const val COORD_LINEAR_X = 0.1
    private const val COORD_LINEAR_Y = 0.2
    private const val COORD_LAT_BASE = -100.0
    private const val COORD_LAT_X_COEF = 2.0
    private const val COORD_LAT_Y_COEF = 3.0
    private const val COORD_LON_X_COEF = 1.0
    private const val COORD_TRIG_SCALE = 2.0 / 3.0
    private const val COORD_EPSILON = 1e-6
    private const val MAX_ABS_LATITUDE = 90.0
    private const val MAX_ABS_LONGITUDE = 180.0

    /** 判断坐标是否位于中国大陆及周边偏移适用范围外。 */
    fun isOutOfChina(latitude: Double, longitude: Double): Boolean =
        longitude !in CHINA_MIN_LON..CHINA_MAX_LON || latitude !in CHINA_MIN_LAT..CHINA_MAX_LAT

    /** WGS-84（硬件 GPS / OpenStreetMap）转 GCJ-02（高德 / 腾讯）。 */
    fun wgs84ToGcj02(latitude: Double, longitude: Double): GeoPoint {
        if (isOutOfChina(latitude, longitude)) {
            return GeoPoint(latitude, longitude)
        }
        val delta = computeGcjDelta(latitude, longitude)
        return GeoPoint(latitude + delta.latitude, longitude + delta.longitude)
    }

    /** GCJ-02（高德 / 腾讯）转 WGS-84（硬件 GPS / OpenStreetMap）。 */
    fun gcj02ToWgs84(latitude: Double, longitude: Double): GeoPoint {
        if (isOutOfChina(latitude, longitude)) {
            return GeoPoint(latitude, longitude)
        }
        val delta = computeGcjDelta(latitude, longitude)
        return GeoPoint(latitude - delta.latitude, longitude - delta.longitude)
    }

    /** GCJ-02（高德）转 BD-09（百度 / 超星签到接口标准坐标系）。 */
    fun gcj02ToBd09(latitude: Double, longitude: Double): GeoPoint {
        val z = sqrt(longitude * longitude + latitude * latitude) + BD_Z_DELTA * sin(latitude * X_PI)
        val theta = atan2(latitude, longitude) + BD_THETA_DELTA * cos(longitude * X_PI)
        val bdLng = z * cos(theta) + BD_LON_OFFSET
        val bdLat = z * sin(theta) + BD_LAT_OFFSET
        return GeoPoint(bdLat, bdLng)
    }

    /** BD-09（百度 / 超星签到接口）转 GCJ-02（高德地图瓦片展示坐标系）。 */
    fun bd09ToGcj02(latitude: Double, longitude: Double): GeoPoint {
        val x = longitude - BD_LON_OFFSET
        val y = latitude - BD_LAT_OFFSET
        val z = sqrt(x * x + y * y) - BD_Z_DELTA * sin(y * X_PI)
        val theta = atan2(y, x) - BD_THETA_DELTA * cos(x * X_PI)
        val gcjLng = z * cos(theta)
        val gcjLat = z * sin(theta)
        return GeoPoint(gcjLat, gcjLng)
    }

    /** WGS-84（设备 GPS）直接转 BD-09（超星位置签到提交坐标）。 */
    fun wgs84ToBd09(latitude: Double, longitude: Double): GeoPoint {
        val gcj = wgs84ToGcj02(latitude, longitude)
        return gcj02ToBd09(gcj.latitude, gcj.longitude)
    }

    /** BD-09 转 WGS-84。 */
    fun bd09ToWgs84(latitude: Double, longitude: Double): GeoPoint {
        val gcj = bd09ToGcj02(latitude, longitude)
        return gcj02ToWgs84(gcj.latitude, gcj.longitude)
    }

    /** 计算两点间球面距离（单位：米）。 */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val radLat1 = Math.toRadians(a.latitude)
        val radLat2 = Math.toRadians(b.latitude)
        val deltaLat = radLat1 - radLat2
        val deltaLon = Math.toRadians(a.longitude - b.longitude)
        val haversine = sin(deltaLat / DEG_DOUBLE).pow(DEG_DOUBLE) +
            cos(radLat1) * cos(radLat2) * sin(deltaLon / DEG_DOUBLE).pow(DEG_DOUBLE)
        return DEG_DOUBLE * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    /** 将经纬度数值格式化为固定 6 位小数字符串。 */
    fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    /** 判断服务端下发的坐标数值是否为有效非占位坐标（排除 0.0 与 -1.0 等占位值）。 */
    fun isValidTargetCoordinate(latitude: Double?, longitude: Double?): Boolean {
        val validNumbers = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite()
        if (!validNumbers) {
            return false
        }
        val isZero = abs(latitude) < COORD_EPSILON && abs(longitude) < COORD_EPSILON
        val isPlaceholder = abs(latitude + 1.0) < COORD_EPSILON || abs(longitude + 1.0) < COORD_EPSILON
        val inBounds = latitude in -MAX_ABS_LATITUDE..MAX_ABS_LATITUDE &&
            longitude in -MAX_ABS_LONGITUDE..MAX_ABS_LONGITUDE
        return !isZero && !isPlaceholder && inBounds
    }

    private fun computeGcjDelta(latitude: Double, longitude: Double): GeoPoint {
        val dLatRaw = transformLat(longitude - GCJ_OFFSET_BASE_LON, latitude - GCJ_OFFSET_BASE_LAT)
        val dLonRaw = transformLon(longitude - GCJ_OFFSET_BASE_LON, latitude - GCJ_OFFSET_BASE_LAT)
        val radLat = latitude / DEG_HALF_CIRCLE * PI
        var magic = sin(radLat)
        magic = 1.0 - ECCENTRICITY_SQUARED * magic * magic
        val sqrtMagic = sqrt(magic)
        val dLat = (dLatRaw * DEG_HALF_CIRCLE) /
            ((SEMI_MAJOR_AXIS * (1.0 - ECCENTRICITY_SQUARED)) / (magic * sqrtMagic) * PI)
        val dLon = (dLonRaw * DEG_HALF_CIRCLE) / (SEMI_MAJOR_AXIS / sqrtMagic * cos(radLat) * PI)
        return GeoPoint(dLat, dLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = COORD_LAT_BASE + COORD_LAT_X_COEF * x + COORD_LAT_Y_COEF * y +
            COORD_LINEAR_Y * y * y + COORD_LINEAR_X * x * y + COORD_LINEAR_Y * sqrt(abs(x))
        ret += (
            COORD_FACTOR_TWENTY * sin(COORD_SCALE_SIX * x * PI) +
                COORD_FACTOR_TWENTY * sin(DEG_DOUBLE * x * PI)
            ) * COORD_TRIG_SCALE
        ret += (
            COORD_FACTOR_TWENTY * sin(y * PI) +
                COORD_FACTOR_FORTY * sin(y / COORD_LAT_Y_COEF * PI)
            ) * COORD_TRIG_SCALE
        ret += (
            COORD_FACTOR_ONE_SIXTY * sin(y / COORD_SCALE_TWELVE * PI) +
                COORD_FACTOR_THREE_TWENTY * sin(y * PI / COORD_SCALE_THIRTY)
            ) * COORD_TRIG_SCALE
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = COORD_FACTOR_THREE_HUNDRED + COORD_LON_X_COEF * x + DEG_DOUBLE * y +
            COORD_LINEAR_X * x * x + COORD_LINEAR_X * x * y + COORD_LINEAR_X * sqrt(abs(x))
        ret += (
            COORD_FACTOR_TWENTY * sin(COORD_SCALE_SIX * x * PI) +
                COORD_FACTOR_TWENTY * sin(DEG_DOUBLE * x * PI)
            ) * COORD_TRIG_SCALE
        ret += (
            COORD_FACTOR_TWENTY * sin(x * PI) +
                COORD_FACTOR_FORTY * sin(x / COORD_LAT_Y_COEF * PI)
            ) * COORD_TRIG_SCALE
        ret += (
            COORD_FACTOR_ONE_FIFTY * sin(x / COORD_SCALE_TWELVE * PI) +
                COORD_FACTOR_THREE_HUNDRED * sin(x / COORD_SCALE_THIRTY * PI)
            ) * COORD_TRIG_SCALE
        return ret
    }
}
