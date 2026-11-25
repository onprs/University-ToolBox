package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    /** 最近一次成功解析的设备当前定位缓存（用于初始化地图视野避免飘往默认城市）。 */
    val lastKnownPlace: ResolvedPlace?
        get() = null

    /** 获取当前设备定位并自动转换为签到所需坐标系与地址描述。 */
    suspend fun getCurrentLocation(): AppResult<ResolvedPlace>

    /** 按地点关键字检索候选地点列表，可选传入参考中心优先排序邻近结果。 */
    suspend fun searchPlaces(query: String, proximityGcj: GeoPoint? = null): AppResult<List<ResolvedPlace>>

    /** 根据高德地图选点（GCJ-02）反向解析可读中文地址描述。 */
    suspend fun reverseGeocodeGcj02(gcjLatitude: Double, gcjLongitude: Double): String?
}

@Serializable
private data class ArcGisFindResponseDto(val candidates: List<ArcGisCandidateDto> = emptyList())

@Serializable
private data class ArcGisCandidateDto(
    val address: String = "",
    val location: ArcGisPointDto? = null,
    val attributes: ArcGisAttributesDto? = null,
)

@Serializable
private data class ArcGisPointDto(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
private data class ArcGisAttributesDto(
    @SerialName("PlaceName")
    val placeName: String? = null,
    @SerialName("Place_addr")
    val placeAddr: String? = null,
    @SerialName("LongLabel")
    val longLabel: String? = null,
)

@Serializable
private data class ArcGisReverseResponseDto(val address: ArcGisReverseAddressDto? = null)

@Serializable
private data class ArcGisReverseAddressDto(
    @SerialName("LongLabel")
    val longLabel: String? = null,
    @SerialName("Match_addr")
    val matchAddr: String? = null,
    @SerialName("PlaceName")
    val placeName: String? = null,
)

@Serializable
private data class PhotonResponseDto(val features: List<PhotonFeatureDto> = emptyList())

@Serializable
private data class PhotonFeatureDto(
    val geometry: PhotonGeometryDto? = null,
    val properties: PhotonPropertiesDto? = null,
)

@Serializable
private data class PhotonGeometryDto(val coordinates: List<Double> = emptyList())

@Serializable
private data class PhotonPropertiesDto(
    val name: String? = null,
    val state: String? = null,
    val city: String? = null,
    val district: String? = null,
    val street: String? = null,
)

@Serializable
private data class NominatimSearchItemDto(
    val lat: String = "",
    val lon: String = "",
    val name: String? = null,
    @SerialName("display_name")
    val displayName: String = "",
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

    @Volatile
    private var cachedLastPlace: ResolvedPlace? = null

    override val lastKnownPlace: ResolvedPlace?
        get() = cachedLastPlace

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): AppResult<ResolvedPlace> = withContext(dispatchers.io) {
        if (!hasLocationPermission()) {
            return@withContext AppResult.Failure(DomainError.Unauthorized("尚未授予定位权限"))
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext AppResult.Failure(DomainError.Unsupported("当前设备不支持系统定位服务"))

        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            return@withContext AppResult.Failure(
                DomainError.Network("系统位置信息开关未开启，请先开启系统定位服务"),
            )
        }

        val location = obtainBestDeviceLocation(manager)
            ?: return@withContext AppResult.Failure(
                DomainError.Network("暂未获取到实时定位信号，请移至信号良好处重试或直接搜索选点"),
            )

        val wgsLat = location.latitude
        val wgsLng = location.longitude
        val gcj = CoordinateConverter.wgs84ToGcj02(wgsLat, wgsLng)
        val bd = CoordinateConverter.wgs84ToBd09(wgsLat, wgsLng)
        val address = resolveAddressFromWgs84(wgsLat, wgsLng).orEmpty()
        val resolved = ResolvedPlace(
            title = address.ifBlank { "当前位置" },
            address = address,
            gcj02Point = gcj,
            bd09Point = bd,
        )
        cachedLastPlace = resolved
        AppResult.Success(resolved)
    }

    override suspend fun searchPlaces(query: String, proximityGcj: GeoPoint?): AppResult<List<ResolvedPlace>> =
        withContext(dispatchers.io) {
            val normalizedKeyword = normalizeSearchKeyword(query)
            if (normalizedKeyword.isEmpty()) {
                return@withContext AppResult.Success(emptyList())
            }
            val effectiveBiasGcj = proximityGcj ?: cachedLastPlace?.gcj02Point
            val wgsBias = effectiveBiasGcj?.let { CoordinateConverter.gcj02ToWgs84(it.latitude, it.longitude) }

            val combined = coroutineScope {
                val arcGisDeferred = async {
                    withTimeoutOrNull(ONLINE_SOURCE_TIMEOUT_MS) { searchViaArcGis(normalizedKeyword, wgsBias) }
                        .orEmpty()
                }
                val photonDeferred = async {
                    withTimeoutOrNull(ONLINE_SOURCE_TIMEOUT_MS) { searchViaPhoton(normalizedKeyword, wgsBias) }
                        .orEmpty()
                }
                val nominatimDeferred = async {
                    withTimeoutOrNull(NOMINATIM_TIMEOUT_MS) { searchViaNominatim(normalizedKeyword) }
                        .orEmpty()
                }
                val localDeferred = async { searchViaSystemGeocoder(normalizedKeyword) }
                awaitAll(arcGisDeferred, photonDeferred, nominatimDeferred, localDeferred).flatten()
            }

            val merged = mergeAndRankPlaces(
                candidates = combined,
                keyword = normalizedKeyword,
                proximityGcj = effectiveBiasGcj,
            )
            AppResult.Success(merged)
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
        val candidateProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.distinct().filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        val freshLocation = if (candidateProviders.isNotEmpty()) {
            withTimeoutOrNull(LOCATION_FETCH_TIMEOUT_MS) {
                raceActiveProviders(manager, candidateProviders)
            }
        } else {
            null
        }
        if (freshLocation != null) {
            return freshLocation
        }

        val allKnownProviders = (candidateProviders + LocationManager.PASSIVE_PROVIDER).distinct()
        return allKnownProviders
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { loc -> CoordinateConverter.isValidTargetCoordinate(loc.latitude, loc.longitude) }
            .maxByOrNull { it.time }
    }

    private suspend fun raceActiveProviders(manager: LocationManager, providers: List<String>): Location? =
        coroutineScope {
            val winner = CompletableDeferred<Location?>()
            val jobs = providers.map { provider ->
                launch {
                    val loc = awaitSingleLocation(manager, provider)
                    if (loc != null && CoordinateConverter.isValidTargetCoordinate(loc.latitude, loc.longitude)) {
                        winner.complete(loc)
                    }
                }
            }
            launch {
                jobs.forEach { it.join() }
                if (!winner.isCompleted) {
                    winner.complete(null)
                }
            }
            try {
                winner.await()
            } finally {
                coroutineContext.cancelChildren()
            }
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

    private fun searchViaArcGis(keyword: String, wgsBias: GeoPoint?): List<ResolvedPlace> = runCatching {
        val urlBuilder = ARCGIS_FIND_URL.toHttpUrl().newBuilder()
            .addQueryParameter("f", "json")
            .addQueryParameter("SingleLine", keyword)
            .addQueryParameter("maxLocations", SEARCH_LIMIT.toString())
            .addQueryParameter("outFields", "PlaceName,Place_addr,LongLabel")
            .addQueryParameter("langCode", "zh")
            .addQueryParameter("countryCode", "CHN")
        if (wgsBias != null) {
            urlBuilder.addQueryParameter("location", "${wgsBias.longitude},${wgsBias.latitude}")
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", GEOCODER_USER_AGENT)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val dto = json.decodeFromString<ArcGisFindResponseDto>(response.body.string())
            dto.candidates.mapNotNull { cand ->
                val loc = cand.location ?: return@mapNotNull null
                if (!CoordinateConverter.isValidTargetCoordinate(loc.y, loc.x)) {
                    return@mapNotNull null
                }
                val title = cand.attributes?.placeName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: cand.address.trim().ifBlank { keyword }
                val detailAddr = cand.attributes?.placeAddr?.trim()?.takeIf { it.isNotEmpty() }
                    ?: cand.attributes?.longLabel?.trim()?.removeSuffix(", 中国")?.removeSuffix("，中国")
                    ?: title
                val fullAddr = if (detailAddr.contains(title)) detailAddr else "$detailAddr$title"
                buildPlaceFromWgs84(title = title, address = fullAddr, wgsLat = loc.y, wgsLng = loc.x)
            }
        }
    }.getOrDefault(emptyList())

    private fun searchViaPhoton(keyword: String, wgsBias: GeoPoint?): List<ResolvedPlace> = runCatching {
        val urlBuilder = PHOTON_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", keyword)
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
        if (wgsBias != null) {
            urlBuilder.addQueryParameter("lat", wgsBias.latitude.toString())
            urlBuilder.addQueryParameter("lon", wgsBias.longitude.toString())
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", GEOCODER_USER_AGENT)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val dto = json.decodeFromString<PhotonResponseDto>(response.body.string())
            dto.features.mapNotNull { feat ->
                val coords = feat.geometry?.coordinates.orEmpty()
                if (coords.size < 2) {
                    return@mapNotNull null
                }
                val wgsLng = coords[0]
                val wgsLat = coords[1]
                if (CoordinateConverter.isOutOfChina(wgsLat, wgsLng)) {
                    return@mapNotNull null
                }
                val props = feat.properties
                val title = props?.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val addr = listOfNotNull(props.state, props.city, props.district, props.street, title)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .joinToString("")
                buildPlaceFromWgs84(title = title, address = addr.ifBlank { title }, wgsLat = wgsLat, wgsLng = wgsLng)
            }
        }
    }.getOrDefault(emptyList())

    private fun searchViaNominatim(keyword: String): List<ResolvedPlace> = runCatching {
        val url = NOMINATIM_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .addQueryParameter("accept-language", "zh-CN")
            .addQueryParameter("countrycodes", "cn")
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
                val formattedAddress = simplifyDisplayName(dto.displayName)
                val title = dto.name?.takeIf { it.isNotBlank() }
                    ?: formattedAddress.substringBefore('，').ifBlank { keyword }
                buildPlaceFromWgs84(
                    title = title,
                    address = formattedAddress.ifBlank { title },
                    wgsLat = wgsLat,
                    wgsLng = wgsLng,
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
            val text = formatSystemAddress(addr).ifBlank { keyword }
            val title = addr.featureName?.takeIf { it.isNotBlank() } ?: text
            buildPlaceFromWgs84(title = title, address = text, wgsLat = addr.latitude, wgsLng = addr.longitude)
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
        return reverseViaArcGis(wgsLat, wgsLng)
    }

    private fun reverseViaArcGis(wgsLat: Double, wgsLng: Double): String? = runCatching {
        val url = ARCGIS_REVERSE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("f", "json")
            .addQueryParameter("location", "$wgsLng,$wgsLat")
            .addQueryParameter("langCode", "zh")
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
            val addr = json.decodeFromString<ArcGisReverseResponseDto>(response.body.string()).address
            val raw = addr?.longLabel?.takeIf { it.isNotBlank() }
                ?: addr?.matchAddr?.takeIf { it.isNotBlank() }
                ?: addr?.placeName
            raw?.replace(", 中国", "")?.replace("，中国", "")?.trim()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun buildPlaceFromWgs84(title: String, address: String, wgsLat: Double, wgsLng: Double): ResolvedPlace {
        val gcj = CoordinateConverter.wgs84ToGcj02(wgsLat, wgsLng)
        val bd = CoordinateConverter.wgs84ToBd09(wgsLat, wgsLng)
        return ResolvedPlace(title = title, address = address, gcj02Point = gcj, bd09Point = bd)
    }

    private fun mergeAndRankPlaces(
        candidates: List<ResolvedPlace>,
        keyword: String,
        proximityGcj: GeoPoint?,
    ): List<ResolvedPlace> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val deduplicated = ArrayList<ResolvedPlace>()
        for (item in candidates) {
            val dupIndex = deduplicated.indexOfFirst { existing ->
                CoordinateConverter.distanceMeters(existing.gcj02Point, item.gcj02Point) < DEDUP_DISTANCE_METERS &&
                    (existing.title == item.title || existing.address.contains(item.title))
            }
            if (dupIndex < 0) {
                deduplicated += item
            } else if (item.address.length > deduplicated[dupIndex].address.length) {
                deduplicated[dupIndex] = item
            }
        }
        val tokens = keyword.split(' ').filter { it.isNotEmpty() }
        return deduplicated.sortedByDescending { place ->
            scoreCandidate(place, tokens, proximityGcj)
        }.take(MAX_MERGED_RESULTS)
    }

    private fun scoreCandidate(place: ResolvedPlace, tokens: List<String>, proximityGcj: GeoPoint?): Double {
        val combinedText = "${place.title} ${place.address}".lowercase()
        val matchedTokens = tokens.count { token -> combinedText.contains(token.lowercase()) }
        val exactTitleBoost = if (tokens.any { place.title.contains(it, ignoreCase = true) }) TITLE_MATCH_BOOST else 0.0
        val distanceScore = if (proximityGcj != null) {
            val distKm = CoordinateConverter.distanceMeters(proximityGcj, place.gcj02Point) / METERS_PER_KM
            PROXIMITY_WEIGHT / (1.0 + distKm / PROXIMITY_DECAY_KM)
        } else {
            0.0
        }
        return matchedTokens * TOKEN_MATCH_WEIGHT + exactTitleBoost + distanceScore
    }

    private fun normalizeSearchKeyword(raw: String): String = raw
        .replace('（', ' ')
        .replace('）', ' ')
        .replace('(', ' ')
        .replace(')', ' ')
        .replace(WHITESPACE_REGEX, " ")
        .trim()

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
        private const val LOCATION_FETCH_TIMEOUT_MS = 4_500L
        private const val ONLINE_SOURCE_TIMEOUT_MS = 4_000L
        private const val NOMINATIM_TIMEOUT_MS = 3_200L
        private const val SEARCH_LIMIT = 6
        private const val MAX_MERGED_RESULTS = 10
        private const val MAX_ADDRESS_SEGMENTS = 4
        private const val DEDUP_DISTANCE_METERS = 35.0
        private const val METERS_PER_KM = 1000.0
        private const val TOKEN_MATCH_WEIGHT = 30.0
        private const val TITLE_MATCH_BOOST = 20.0
        private const val PROXIMITY_WEIGHT = 25.0
        private const val PROXIMITY_DECAY_KM = 15.0

        private val WHITESPACE_REGEX = Regex("\\s+")

        private const val ARCGIS_FIND_URL =
            "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates"
        private const val ARCGIS_REVERSE_URL =
            "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/reverseGeocode"
        private const val PHOTON_SEARCH_URL = "https://photon.komoot.io/api/"
        private const val NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search"
        private const val GEOCODER_USER_AGENT = "UniversityToolbox/1.1"
    }
}
