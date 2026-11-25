package io.github.onprs.universitytoolbox.core.update

import io.github.onprs.universitytoolbox.core.common.dispatcher.IoDispatcher
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.result.runCatchingApp
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.network.RootHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 远程治理与安全配置仓储契约：统一拉取更新清单、同步模块级熔断状态及吊销授权列表。
 */
interface RemoteGovernanceRepository {
    /** 被远程熔断禁用的工具映射（Key 为工具 ID，Value 为可选维护公告说明）。 */
    val disabledTools: StateFlow<Map<String, String?>>

    /** 最近一次成功拉取或应用的更新清单。 */
    val latestManifest: StateFlow<UpdateManifest?>

    fun isToolDisabled(toolId: String): Boolean

    fun disableReason(toolId: String): String?

    suspend fun fetchAndApplyManifest(manifestUrl: String): AppResult<UpdateManifest>

    suspend fun applyManifest(manifest: UpdateManifest)
}

/**
 * 基于 [OkHttpClient] 与 [LicenseEngine] 的默认远程治理实现，拉取失败时保留既定安全状态降级运行。
 */
@Singleton
class DefaultRemoteGovernanceRepository internal constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val licenseEngine: LicenseEngine,
    private val ioDispatcher: CoroutineDispatcher,
    private val allowCleartextForTesting: Boolean,
) : RemoteGovernanceRepository {

    @Inject
    constructor(
        @RootHttpClient okHttpClient: OkHttpClient,
        json: Json,
        licenseEngine: LicenseEngine,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        okHttpClient = okHttpClient,
        json = json,
        licenseEngine = licenseEngine,
        ioDispatcher = ioDispatcher,
        allowCleartextForTesting = false,
    )

    private val mutableDisabledTools = MutableStateFlow<Map<String, String?>>(emptyMap())
    override val disabledTools: StateFlow<Map<String, String?>> = mutableDisabledTools.asStateFlow()

    private val mutableLatestManifest = MutableStateFlow<UpdateManifest?>(null)
    override val latestManifest: StateFlow<UpdateManifest?> = mutableLatestManifest.asStateFlow()

    override fun isToolDisabled(toolId: String): Boolean = toolId in mutableDisabledTools.value

    override fun disableReason(toolId: String): String? = mutableDisabledTools.value[toolId]

    override suspend fun fetchAndApplyManifest(manifestUrl: String): AppResult<UpdateManifest> {
        val parsedUrl = manifestUrl.trim().toHttpUrlOrNull()
            ?: return AppResult.Failure(DomainError.Validation("Invalid manifest URL", field = "manifestUrl"))

        if (!parsedUrl.isHttps && !allowCleartextForTesting) {
            return AppResult.Failure(DomainError.Security("Manifest URL must use HTTPS"))
        }

        val fetchResult = withContext(ioDispatcher) {
            runCatchingApp(
                errorMapper = { throwable ->
                    DomainError.Network(
                        message = throwable.message ?: "Failed to fetch update manifest",
                        cause = throwable,
                    )
                },
            ) {
                val request = Request.Builder()
                    .url(parsedUrl)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Unexpected HTTP ${response.code} when fetching manifest")
                    }
                    val rawBody = response.body.string()
                    json.decodeFromString<UpdateManifest>(rawBody)
                }
            }
        }

        if (fetchResult is AppResult.Success) {
            applyManifest(fetchResult.data)
        }
        return fetchResult
    }

    override suspend fun applyManifest(manifest: UpdateManifest) {
        mutableDisabledTools.value = manifest.disabledTools.toMap()
        mutableLatestManifest.value = manifest
        licenseEngine.updateRevokedList(manifest.revokedSerialIds.toSet())
    }
}
