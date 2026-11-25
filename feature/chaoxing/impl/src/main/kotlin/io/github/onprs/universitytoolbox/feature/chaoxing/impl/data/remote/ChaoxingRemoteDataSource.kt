package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountContext
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * `stuSignajax` 统一提交参数。
 */
data class StuSignSubmitParams(
    val activeId: Long,
    val uid: String,
    val fid: String,
    val displayName: String = "",
    val latitude: String = "-1",
    val longitude: String = "-1",
    val address: String? = null,
    val ifTiJiao: String? = null,
    val enc: String? = null,
    val signCode: String? = null,
    val objectId: String? = null,
    val validate: String? = null,
)

/**
 * 超星课程列表、活动列表与签到协议远程数据源（防腐层核心）。
 * 所有需鉴权请求均通过 [AccountContext] Request Tag 强制绑定独立账号分区，避免 Cookie 串号。
 */
@Singleton
class ChaoxingRemoteDataSource @Inject constructor(
    private val executor: ChaoxingHttpExecutor,
    private val cookieVault: AccountCookieVault,
    private val htmlParser: ChaoxingHtmlParser,
    private val endpointConfig: ChaoxingEndpointConfig,
    private val dispatchers: DispatcherProvider,
) {
    /** 拉取指定账号的课程列表（优先移动端 JSON API，异常时自动降级新版 Web HTML 解析）。 */
    suspend fun fetchCourses(accountId: String): AppResult<List<ChaoxingCourse>> {
        return withContext(dispatchers.io) {
            val jsonUrl = endpointConfig.backClazzDataUrl.toHttpUrl().newBuilder()
                .addQueryParameter("view", "json")
                .addQueryParameter("rss", "1")
                .build()
            val request = Request.Builder()
                .url(jsonUrl)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
                .tag(AccountContext::class.java, AccountContext(accountId))
                .get()
                .build()

            val response = when (val result = executor.executeText(request)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext result
            }
            if (executor.isSessionExpiredRedirect(response)) {
                return@withContext AppResult.Failure(DomainError.Unauthorized("登录会话已过期，请重新登录"))
            }
            val dto = executor.decodeJson<BackClazzDataResponseDto>(response.body)
            if (dto != null) {
                if (dto.result == 0 || dto.status == false) {
                    val message = dto.errorMsg ?: dto.msg ?: "请重新登录"
                    return@withContext AppResult.Failure(DomainError.Unauthorized(message))
                }
                if (dto.result == 1) {
                    return@withContext AppResult.Success(ChaoxingDomainMapper.toCourses(dto))
                }
            }
            fetchCoursesFromWebHtml(accountId)
        }
    }

    /** 拉取课程下的活动列表，并自动识别 302 登录重定向。 */
    suspend fun fetchActivities(
        accountId: String,
        fid: String,
        courseId: Long,
        clazzId: Long,
        cpi: Long,
        showNotStarted: Boolean = false,
    ): AppResult<List<ChaoxingActivity>> = withContext(dispatchers.io) {
        val url = endpointConfig.activeListUrl.toHttpUrl().newBuilder()
            .addQueryParameter("fid", fid.ifBlank { "0" })
            .addQueryParameter("courseId", courseId.toString())
            .addQueryParameter("classId", clazzId.toString())
            .addQueryParameter("cpi", cpi.toString())
            .addQueryParameter("showNotStartedActive", if (showNotStarted) "1" else "0")
            .build()

        val request = Request.Builder()
            .url(url)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
            .tag(AccountContext::class.java, AccountContext(accountId))
            .get()
            .build()

        val response = when (val result = executor.executeText(request)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext result
        }
        if (executor.isSessionExpiredRedirect(response)) {
            return@withContext AppResult.Failure(DomainError.Unauthorized("会话已过期，请重新登录"))
        }

        val dto = executor.decodeJson<ActiveListResponseDto>(response.body)
            ?: return@withContext AppResult.Failure(DomainError.Network("活动列表响应解析失败"))

        if (dto.result != 1) {
            val err = dto.errorMsg ?: dto.msg ?: "获取活动列表失败"
            return@withContext if (err.contains("登录")) {
                AppResult.Failure(DomainError.Unauthorized(err))
            } else {
                AppResult.Failure(DomainError.Network(err, httpCode = response.code))
            }
        }

        val items = dto.data?.activeList.orEmpty().map { item ->
            ChaoxingDomainMapper.toActivity(item, courseId = courseId, clazzId = clazzId)
        }
        AppResult.Success(items)
    }

    /** 拉取单次签到活动的详细配置（使用 `getPPTActiveInfo` 实测有效端点）。 */
    suspend fun fetchActiveDetail(accountId: String, activeId: Long): AppResult<ChaoxingActiveDetail> {
        return withContext(dispatchers.io) {
            val url = endpointConfig.getPptActiveInfoUrl.toHttpUrl().newBuilder()
                .addQueryParameter("activeId", activeId.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
                .tag(AccountContext::class.java, AccountContext(accountId))
                .get()
                .build()

            val response = when (val result = executor.executeText(request)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext result
            }
            if (executor.isSessionExpiredRedirect(response)) {
                return@withContext AppResult.Failure(DomainError.Unauthorized("会话已过期，请重新登录"))
            }

            val dto = executor.decodeJson<PptActiveInfoResponseDto>(response.body)
                ?: return@withContext AppResult.Failure(DomainError.Network("签到详情解析失败"))

            if (dto.result != 1 || dto.data == null) {
                val err = dto.errorMsg ?: dto.msg ?: "获取签到详情失败"
                return@withContext if (err.contains("登录")) {
                    AppResult.Failure(DomainError.Unauthorized(err))
                } else {
                    AppResult.Failure(DomainError.Network(err, httpCode = response.code))
                }
            }
            AppResult.Success(ChaoxingDomainMapper.toActiveDetail(dto.data))
        }
    }

    /** 预加载 H5 签到上下文页面。 */
    suspend fun preSign(
        accountId: String,
        courseId: Long,
        clazzId: Long,
        activeId: Long,
        uid: String,
    ): AppResult<ChaoxingPreSignPageInfo> = withContext(dispatchers.io) {
        val url = endpointConfig.newSignPreSignUrl.toHttpUrl().newBuilder()
            .addQueryParameter("courseId", courseId.toString())
            .addQueryParameter("classId", clazzId.toString())
            .addQueryParameter("activePrimaryId", activeId.toString())
            .addQueryParameter("general", "1")
            .addQueryParameter("sys", "1")
            .addQueryParameter("ls", "1")
            .addQueryParameter("appType", ChaoxingEndpointConfig.DEFAULT_APP_TYPE)
            .addQueryParameter("tid", "")
            .addQueryParameter("uid", uid)
            .addQueryParameter("ut", "s")
            .build()

        val request = Request.Builder()
            .url(url)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
            .tag(AccountContext::class.java, AccountContext(accountId))
            .get()
            .build()

        val response = when (val result = executor.executeText(request)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext result
        }
        if (executor.isSessionExpiredRedirect(response)) {
            return@withContext AppResult.Failure(DomainError.Unauthorized("会话已过期，请重新登录"))
        }
        AppResult.Success(htmlParser.parsePreSignHtml(response.body))
    }

    /** 校验手势或数字签到码前置接口。 */
    suspend fun checkSignCode(accountId: String, activeId: Long, signCode: String): AppResult<Boolean> {
        return withContext(dispatchers.io) {
            val url = endpointConfig.checkSignCodeUrl.toHttpUrl().newBuilder()
                .addQueryParameter("activeId", activeId.toString())
                .addQueryParameter("signCode", signCode)
                .build()
            val request = Request.Builder()
                .url(url)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
                .tag(AccountContext::class.java, AccountContext(accountId))
                .get()
                .build()
            val response = when (val result = executor.executeText(request)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext result
            }
            if (executor.isSessionExpiredRedirect(response)) {
                return@withContext AppResult.Failure(DomainError.Unauthorized("会话已过期，请重新登录"))
            }
            val dto = executor.decodeJson<CheckSignCodeResponseDto>(response.body)
                ?: return@withContext AppResult.Failure(DomainError.Network("签到码校验响应解析失败"))
            AppResult.Success(dto.result == 1)
        }
    }

    /** 获取拍照签到云盘上传临时令牌 `_token`。 */
    suspend fun requestPanUploadToken(accountId: String): AppResult<String> = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url(endpointConfig.panUserValidUrl)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
            .tag(AccountContext::class.java, AccountContext(accountId))
            .get()
            .build()

        val response = when (val result = executor.executeText(request)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext result
        }
        val dto = executor.decodeJson<PanUserValidResponseDto>(response.body)
            ?: return@withContext AppResult.Failure(DomainError.Network("云盘令牌响应解析失败"))

        val token = dto.token
        if (!dto.result || token.isNullOrBlank()) {
            val isUnauthorized = dto.newCode == PAN_AUTH_EXPIRED_NEW_CODE || dto.code == PAN_AUTH_EXPIRED_CODE
            val errMsg = dto.msg.orEmpty().ifBlank { "获取云盘上传令牌失败" }
            return@withContext if (isUnauthorized) {
                AppResult.Failure(DomainError.Unauthorized(errMsg))
            } else {
                AppResult.Failure(DomainError.Network(errMsg))
            }
        }
        AppResult.Success(token)
    }

    /** 上传拍照签到图片至超星云盘并返回 `objectId`。 */
    suspend fun uploadSignPhoto(
        accountId: String,
        uid: String,
        token: String,
        fileName: String,
        imageBytes: ByteArray,
    ): AppResult<String> = withContext(dispatchers.io) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("puid", uid)
            .addFormDataPart("_token", token)
            .addFormDataPart(
                "file",
                fileName.ifBlank { DEFAULT_PHOTO_FILENAME },
                imageBytes.toRequestBody(MEDIA_TYPE_IMAGE_JPEG),
            )
            .build()

        val pAuthToken = cookieVault.getAllCookies(accountId)
            .firstOrNull { it.name.equals("p_auth_token", ignoreCase = true) }
            ?.value

        val request = Request.Builder()
            .url(endpointConfig.panUploadUrl)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
            .apply {
                if (!pAuthToken.isNullOrBlank()) {
                    header(ChaoxingHttpExecutor.HEADER_P_AUTH_TOKEN, pAuthToken)
                }
            }
            .tag(AccountContext::class.java, AccountContext(accountId))
            .post(body)
            .build()

        val response = when (val result = executor.executeText(request)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext result
        }
        val dto = executor.decodeJson<PanUploadResponseDto>(response.body)
            ?: return@withContext AppResult.Failure(DomainError.Network("照片上传响应解析失败"))

        val objectId = dto.objectId ?: dto.data?.objectId
        if (!dto.result || objectId.isNullOrBlank()) {
            return@withContext AppResult.Failure(
                DomainError.Network(dto.msg.orEmpty().ifBlank { "照片上传失败" }),
            )
        }
        AppResult.Success(objectId)
    }

    /** 提交签到或签退请求至 `/pptSign/stuSignajax`。 */
    suspend fun submitStuSignAjax(accountId: String, params: StuSignSubmitParams): AppResult<ChaoxingSignRawOutcome> {
        return withContext(dispatchers.io) {
            val urlBuilder = endpointConfig.stuSignAjaxUrl.toHttpUrl().newBuilder()
                .addQueryParameter("activeId", params.activeId.toString())
                .addQueryParameter("uid", params.uid)
                .addQueryParameter("fid", params.fid)
                .addQueryParameter("appType", ChaoxingEndpointConfig.DEFAULT_APP_TYPE)
                .addQueryParameter("name", params.displayName)
                .addQueryParameter("clientip", "")
                .addQueryParameter("latitude", params.latitude)
                .addQueryParameter("longitude", params.longitude)

            params.address?.let { urlBuilder.addQueryParameter("address", it) }
            params.ifTiJiao?.let { urlBuilder.addQueryParameter("ifTiJiao", it) }
            params.enc?.let { urlBuilder.addQueryParameter("enc", it) }
            params.signCode?.let { urlBuilder.addQueryParameter("signCode", it) }
            params.objectId?.let { urlBuilder.addQueryParameter("objectId", it) }
            params.validate?.let { urlBuilder.addQueryParameter("validate", it) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
                .tag(AccountContext::class.java, AccountContext(accountId))
                .get()
                .build()

            val response = when (val result = executor.executeText(request)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext result
            }
            if (executor.isSessionExpiredRedirect(response)) {
                return@withContext AppResult.Failure(DomainError.Unauthorized("会话已过期，请重新登录"))
            }
            AppResult.Success(ChaoxingSignResponseParser.parse(response.body))
        }
    }

    /** 解析课堂签到二维码内容；若为跳转短链且缺 `enc` 则追踪一次重定向提取 `rcode`。 */
    suspend fun resolveQrPayload(accountId: String, rawQrContent: String): AppResult<ChaoxingQrPayload> {
        return withContext(dispatchers.io) {
            val direct = ChaoxingQrParser.parse(rawQrContent)
            if (direct != null) {
                return@withContext AppResult.Success(direct)
            }
            val parsedUrl = rawQrContent.trim().toHttpUrlOrNull()
                ?: return@withContext AppResult.Failure(DomainError.Validation("无法识别的签到二维码内容"))

            val request = Request.Builder()
                .url(parsedUrl)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
                .tag(AccountContext::class.java, AccountContext(accountId))
                .get()
                .build()

            val response = when (val result = executor.executeText(request)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext result
            }
            val location = response.locationHeader.orEmpty()
            val redirected = ChaoxingQrParser.parse(location)
            if (redirected != null) {
                AppResult.Success(redirected)
            } else {
                AppResult.Failure(DomainError.Validation("二维码缺少有效签到参数"))
            }
        }
    }

    private suspend fun fetchCoursesFromWebHtml(accountId: String): AppResult<List<ChaoxingCourse>> {
        val htmlUrl = endpointConfig.courseListDataUrl.toHttpUrl().newBuilder()
            .addQueryParameter("courseType", "1")
            .addQueryParameter("courseFolderId", "0")
            .addQueryParameter("query", "")
            .addQueryParameter("superstarClass", "0")
            .build()

        val htmlRequest = Request.Builder()
            .url(htmlUrl)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
            .header(ChaoxingHttpExecutor.HEADER_REFERER, endpointConfig.visitInteractionUrl)
            .tag(AccountContext::class.java, AccountContext(accountId))
            .get()
            .build()

        val response = when (val result = executor.executeText(htmlRequest)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        if (executor.isSessionExpiredRedirect(response)) {
            return AppResult.Failure(DomainError.Unauthorized("登录会话已过期，请重新登录"))
        }
        return AppResult.Success(htmlParser.parseCourseListHtml(response.body))
    }

    companion object {
        private const val PAN_AUTH_EXPIRED_NEW_CODE = 200013
        private const val PAN_AUTH_EXPIRED_CODE = -10
        private const val DEFAULT_PHOTO_FILENAME = "sign_photo.jpg"
        private val MEDIA_TYPE_IMAGE_JPEG = "image/jpeg".toMediaType()
    }
}
