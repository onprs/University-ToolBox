package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.network.RootHttpClient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 地点解析结果（同时携带供地图瓦片定位的 GCJ-02 坐标与供超星接口提交的 BD-09 坐标）。
 */
data class ResolvedPlace(val title: String, val address: String, val gcj02Point: GeoPoint, val bd09Point: GeoPoint)

/**
 * 学习通位置签到定位、地点检索与逆地理编码服务契约。
 */
interface ChaoxingLocationService {
    /** 获取当前设备定位并自动转换为签到所需坐标系与地址描述。 */
    suspend fun getCurrentLocation(): AppResult<ResolvedPlace>

    /** 按地点关键字检索候选地点列表。 */
    suspend fun searchPlaces(query: String): AppResult<List<ResolvedPlace>>

    /** 根据高德地图选点（GCJ-02）反向解析可读中文地址描述。 */
    suspend fun reverseGeocodeGcj02(gcjLatitude: Double, gcjLongitude: Double): String?
}

@Serializable
private data class NominatimSearchItemDto(
    val lat: String = "",
    val lon: String = "",
    val name: String? = null,
    @SerialName("display_name")
    val displayName: String = "",
)

@Serializable
private data class NominatimReverseDto(
    val name: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
)

@Singleton
class AndroidChaoxingLocationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:RootHttpClient private val httpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) : ChaoxingLocationService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): AppResult<ResolvedPlace> = withContext(dispatchers.io) {
        if (!hasLocationPermission()) {
            return@withContext AppResult.Failure(DomainError.Unauthorized("尚未授予定位权限"))
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext AppResult.Failure(DomainError.Unsupported("当前设备不支持系统定位服务"))

        val location = obtainBestDeviceLocation(manager)
            ?: return@withContext AppResult.Failure(
                DomainError.Network("无法获取当前定位，请确认系统定位开关已开启或改用地图选点"),
            )

        val wgsLat = location.latitude
        val wgsLng = location.longitude
        val gcj = CoordinateConverter.wgs84ToGcj02(wgsLat, wgsLng)
        val bd = CoordinateConverter.wgs84ToBd09(wgsLat, wgsLng)
        val address = resolveAddressFromWgs84(wgsLat, wgsLng).orEmpty()
        AppResult.Success(
            ResolvedPlace(
                title = address.ifBlank { "当前位置" },
                address = address,
                gcj02Point = gcj,
                bd09Point = bd,
            ),
        )
    }

    override suspend fun searchPlaces(query: String): AppResult<List<ResolvedPlace>> = withContext(dispatchers.io) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            return@withContext AppResult.Success(emptyList())
        }
        val onlineResults = searchViaNominatim(keyword)
        if (onlineResults.isNotEmpty()) {
            return@withContext AppResult.Success(onlineResults)
        }
        val localResults = searchViaSystemGeocoder(keyword)
        AppResult.Success(localResults)
    }

    override suspend fun reverseGeocodeGcj02(gcjLatitude: Double, gcjLongitude: Double): String? =
        withContext(dispatchers.io) {
            val wgs = CoordinateConverter.gcj02ToWgs84(gcjLatitude, gcjLongitude)
            resolveAddressFromWgs84(wgs.latitude, wgs.longitude)
        }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun obtainBestDeviceLocation(manager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }

        val activeProvider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        val freshLocation = if (activeProvider != null) {
            withTimeoutOrNull(LOCATION_FETCH_TIMEOUT_MS) {
                awaitSingleLocation(manager, activeProvider)
            }
        } else {
            null
        }
        if (freshLocation != null) {
            return freshLocation
        }
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private fun searchViaNominatim(keyword: String): List<ResolvedPlace> = runCatching {
        val url = NOMINATIM_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .addQueryParameter("accept-language", "zh-CN")
            .addQueryParameter("q", keyword)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", GEOCODER_USER_AGENT)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val items = json.decodeFromString<List<NominatimSearchItemDto>>(response.body.string())
            items.mapNotNull { dto ->
                val wgsLat = dto.lat.toDoubleOrNull() ?: return@mapNotNull null
                val wgsLng = dto.lon.toDoubleOrNull() ?: return@mapNotNull null
                val gcj = CoordinateConverter.wgs84ToGcj02(wgsLat, wgsLng)
                val bd = CoordinateConverter.wgs84ToBd09(wgsLat, wgsLng)
                val formattedAddress = simplifyDisplayName(dto.displayName)
                val title = dto.name?.takeIf { it.isNotBlank() }
                    ?: formattedAddress.substringBefore('，').ifBlank { keyword }
                ResolvedPlace(
                    title = title,
                    address = formattedAddress.ifBlank { title },
                    gcj02Point = gcj,
                    bd09Point = bd,
                )
            }
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun searchViaSystemGeocoder(keyword: String): List<ResolvedPlace> = runCatching {
        if (!Geocoder.isPresent()) {
            return emptyList()
        }
        val geocoder = Geocoder(context, Locale.CHINA)
        val addresses = geocoder.getFromLocationName(keyword, SEARCH_LIMIT).orEmpty()
        addresses.mapNotNull { addr ->
            if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapNotNull null
            val gcj = CoordinateConverter.wgs84ToGcj02(addr.latitude, addr.longitude)
            val bd = CoordinateConverter.wgs84ToBd09(addr.latitude, addr.longitude)
            val text = formatSystemAddress(addr).ifBlank { keyword }
            ResolvedPlace(
                title = addr.featureName?.takeIf { it.isNotBlank() } ?: text,
                address = text,
                gcj02Point = gcj,
                bd09Point = bd,
            )
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun resolveAddressFromWgs84(wgsLat: Double, wgsLng: Double): String? {
        val local = runCatching {
            if (!Geocoder.isPresent()) {
                null
            } else {
                val geocoder = Geocoder(context, Locale.CHINA)
                geocoder.getFromLocation(wgsLat, wgsLng, 1)?.firstOrNull()?.let(::formatSystemAddress)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (local != null) {
            return local
        }
        return runCatching {
            val url = NOMINATIM_REVERSE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("zoom", REVERSE_ZOOM_LEVEL.toString())
                .addQueryParameter("accept-language", "zh-CN")
                .addQueryParameter("lat", wgsLat.toString())
                .addQueryParameter("lon", wgsLng.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", GEOCODER_USER_AGENT)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val dto = json.decodeFromString<NominatimReverseDto>(response.body.string())
                val simplified = simplifyDisplayName(dto.displayName.orEmpty())
                simplified.ifBlank { dto.name }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun formatSystemAddress(address: Address): String {
        val fullLine = (0..address.maxAddressLineIndex)
            .mapNotNull { idx -> address.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
            .joinToString("")
        if (fullLine.isNotBlank()) {
            return fullLine
        }
        return listOfNotNull(
            address.adminArea,
            address.locality,
            address.subLocality,
            address.thoroughfare,
            address.featureName,
        ).distinct().joinToString("")
    }

    private fun simplifyDisplayName(raw: String): String {
        if (raw.isBlank()) return ""
        val parts = raw.split(',', '，')
            .map { it.trim() }
            .filter { segment -> segment.isNotEmpty() && !segment.all { ch -> ch.isDigit() } && segment != "中国" }
        if (parts.isEmpty()) return raw.trim()
        return parts.take(MAX_ADDRESS_SEGMENTS).reversed().distinct().joinToString("")
    }

    companion object {
        private const val LOCATION_FETCH_TIMEOUT_MS = 6_000L
        private const val SEARCH_LIMIT = 8
        private const val REVERSE_ZOOM_LEVEL = 18
        private const val MAX_ADDRESS_SEGMENTS = 4
        private const val NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search"
        private const val NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"
        private const val GEOCODER_USER_AGENT = "UniversityToolbox/1.0"
    }
}
