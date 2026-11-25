package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountContext
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * 超星学习通认证、二维码登录、个人档案与学校检索远程数据源。
 */
@Singleton
class ChaoxingAuthRemoteDataSource @Inject constructor(
    private val executor: ChaoxingHttpExecutor,
    private val cookieVault: AccountCookieVault,
    private val htmlParser: ChaoxingHtmlParser,
    private val endpointConfig: ChaoxingEndpointConfig,
    private val dispatchers: DispatcherProvider,
) {
    /** 使用账号密码登录超星统一认证中心，并在成功后将临时会话迁移至持久化账号分区。 */
    suspend fun loginWithPassword(
        provisionalAccountId: String,
        accountInput: String,
        plainPassword: String,
        fid: String = ChaoxingEndpointConfig.DEFAULT_FID,
        validate: String = "",
        isPasswordEncrypted: Boolean = false,
        customUserAgent: String? = null,
    ): AppResult<ChaoxingLoginOutcome> = withContext(dispatchers.io) {
        val trimmedAccount = accountInput.trim()
        if (trimmedAccount.isEmpty() || plainPassword.isEmpty()) {
            return@withContext AppResult.Success(
                ChaoxingLoginOutcome.InvalidCredentials("账号或密码不能为空"),
            )
        }
        val userAgent = customUserAgent?.takeIf { it.isNotBlank() }
            ?: ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT

        executor.executeText(
            Request.Builder()
                .url(endpointConfig.mloginPageUrl)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, userAgent)
                .tag(AccountContext::class.java, AccountContext(provisionalAccountId))
                .get()
                .build(),
        )

        val encryptedPwd = if (isPasswordEncrypted) {
            plainPassword.trim()
        } else {
            ChaoxingLoginCipher.encrypt(plainPassword)
        }
        val formBody = FormBody.Builder()
            .add("fid", fid.ifBlank { ChaoxingEndpointConfig.DEFAULT_FID })
            .add("uname", ChaoxingLoginCipher.encrypt(trimmedAccount))
            .add("password", encryptedPwd)
            .add("refer", ChaoxingEndpointConfig.DEFAULT_REFER)
            .add("t", "true")
            .add("forbidotherlogin", "0")
            .add("doubleFactorLogin", "0")
            .add("independentId", "0")
            .add("independentNameId", "0")
            .add("validate", validate)
            .build()

        val loginRequest = Request.Builder()
            .url(endpointConfig.fanyaLoginUrl)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, userAgent)
            .header(ChaoxingHttpExecutor.HEADER_REFERER, endpointConfig.mloginPageUrl)
            .header(ChaoxingHttpExecutor.HEADER_ORIGIN, endpointConfig.passportBaseUrl)
            .header(ChaoxingHttpExecutor.HEADER_X_REQUESTED_WITH, ChaoxingHttpExecutor.X_REQUESTED_WITH_AJAX)
            .tag(AccountContext::class.java, AccountContext(provisionalAccountId))
            .post(formBody)
            .build()

        val response = when (val result = executor.executeText(loginRequest)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext result
        }
        val dto = executor.decodeJson<FanyaLoginResponseDto>(response.body)
            ?: return@withContext AppResult.Failure(
                DomainError.Network("登录响应解析失败", httpCode = response.code),
            )

        mapLoginDto(dto, provisionalAccountId, trimmedAccount, userAgent)
    }

    /** 初始化桌面端扫码登录临时会话并提取二维码地址。 */
    suspend fun createQrLoginSession(provisionalAccountId: String): AppResult<ChaoxingQrLoginSession> {
        return withContext(dispatchers.io) {
            val request = Request.Builder()
                .url(endpointConfig.loginPageUrl)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
                .tag(AccountContext::class.java, AccountContext(provisionalAccountId))
                .get()
                .build()

            val response = when (val result = executor.executeText(request)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext result
            }
            val pageState = htmlParser.parseLoginPageState(response.body)
            val uuid = pageState.uuid
            val enc = pageState.enc
            if (uuid.isNullOrBlank() || enc.isNullOrBlank()) {
                return@withContext AppResult.Failure(DomainError.Network("未能从登录页解析二维码凭证"))
            }
            val qrImageUrl = endpointConfig.createQrUrl.toHttpUrl().newBuilder()
                .addQueryParameter("uuid", uuid)
                .addQueryParameter("fid", ChaoxingEndpointConfig.DEFAULT_FID)
                .build()
                .toString()

            AppResult.Success(
                ChaoxingQrLoginSession(
                    provisionalAccountId = provisionalAccountId,
                    uuid = uuid,
                    enc = enc,
                    qrImageUrl = qrImageUrl,
                ),
            )
        }
    }

    /** 下载登录二维码图片字节流。 */
    suspend fun fetchQrImageBytes(provisionalAccountId: String, qrImageUrl: String): AppResult<ByteArray> {
        val request = Request.Builder()
            .url(qrImageUrl)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
            .tag(AccountContext::class.java, AccountContext(provisionalAccountId))
            .get()
            .build()
        return withContext(dispatchers.io) {
            executor.executeBytes(request)
        }
    }

    /** 轮询桌面端扫码登录状态，一旦确认即拉取档案并迁移会话。 */
    suspend fun pollQrLoginStatus(
        provisionalAccountId: String,
        uuid: String,
        enc: String,
    ): AppResult<ChaoxingQrLoginStatus> = withContext(dispatchers.io) {
        val form = FormBody.Builder()
            .add("enc", enc)
            .add("uuid", uuid)
            .add("doubleFactorLogin", "0")
            .add("forbidotherlogin", "0")
            .build()
        val request = Request.Builder()
            .url(endpointConfig.getAuthStatusV2Url)
            .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
            .header(ChaoxingHttpExecutor.HEADER_REFERER, endpointConfig.loginPageUrl)
            .header(ChaoxingHttpExecutor.HEADER_X_REQUESTED_WITH, ChaoxingHttpExecutor.X_REQUESTED_WITH_AJAX)
            .tag(AccountContext::class.java, AccountContext(provisionalAccountId))
            .post(form)
            .build()
        val response = when (val result = executor.executeText(request)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext result
        }
        val dto = executor.decodeJson<QrAuthStatusResponseDto>(response.body)
            ?: return@withContext AppResult.Failure(DomainError.Network("扫码状态解析失败"))

        if (dto.status) {
            return@withContext when (val profileRes = finalizeAuthenticatedProfile(provisionalAccountId, "")) {
                is AppResult.Success -> {
                    val (canonicalId, profile) = profileRes.data
                    AppResult.Success(ChaoxingQrLoginStatus.Confirmed(canonicalId, profile))
                }

                is AppResult.Failure -> profileRes
            }
        }

        val status = when (dto.type) {
            QR_STATUS_WAITING -> ChaoxingQrLoginStatus.Waiting
            QR_STATUS_SCANNED -> ChaoxingQrLoginStatus.ScannedPendingConfirm(dto.nickname, dto.uid)
            QR_STATUS_CANCELLED -> ChaoxingQrLoginStatus.Cancelled
            QR_STATUS_EXPIRED -> ChaoxingQrLoginStatus.Expired
            else -> ChaoxingQrLoginStatus.Failed(dto.mes.orEmpty().ifBlank { "扫码登录失败" })
        }
        AppResult.Success(status)
    }

    /** 获取当前已认证账号的脱敏用户资料并校验登录态。 */
    suspend fun fetchAccountProfile(
        accountId: String,
        fallbackPhone: String = "",
        customUserAgent: String? = null,
    ): AppResult<ChaoxingAccountProfile> {
        return withContext(dispatchers.io) {
            val userAgent = customUserAgent?.takeIf { it.isNotBlank() }
                ?: ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT
            val request = Request.Builder()
                .url(endpointConfig.userLogin4UnameUrl)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, userAgent)
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
            val dto = executor.decodeJson<UserLogin4UnameResponseDto>(response.body)
                ?: return@withContext AppResult.Failure(DomainError.Network("用户信息解析失败"))

            if (dto.result != 1 || dto.msg == null) {
                return@withContext AppResult.Failure(
                    DomainError.Unauthorized(dto.errorMsg.orEmpty().ifBlank { "账号未登录或会话已失效" }),
                )
            }
            AppResult.Success(ChaoxingDomainMapper.toAccountProfile(dto.msg, endpointConfig, fallbackPhone))
        }
    }

    /** 按关键字检索超星官方支持学校/机构列表（无需认证 Cookie）。 */
    suspend fun searchSchools(keyword: String): AppResult<List<ChaoxingSchoolOption>> {
        return withContext(dispatchers.io) {
            val trimmed = keyword.trim()
            if (trimmed.isEmpty()) {
                return@withContext AppResult.Success(emptyList())
            }
            val url = endpointConfig.searchUnisUrl.toHttpUrl().newBuilder()
                .addQueryParameter("filter", trimmed)
                .addQueryParameter("product", SCHOOL_SEARCH_PRODUCT_ID)
                .addQueryParameter("type", "")
                .build()

            val request = Request.Builder()
                .url(url)
                .header(ChaoxingHttpExecutor.HEADER_USER_AGENT, ChaoxingEndpointConfig.MOBILE_WEB_USER_AGENT)
                .get()
                .build()

            val response = when (val result = executor.executeText(request)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext result
            }
            val dto = executor.decodeJson<SearchUnisResponseDto>(response.body)
                ?: return@withContext AppResult.Failure(DomainError.Network("学校列表解析失败"))

            val list = dto.froms.mapNotNull { item ->
                val fid = item.fid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ChaoxingSchoolOption(
                    schoolId = item.schoolid,
                    fid = fid,
                    name = item.name,
                )
            }
            AppResult.Success(list)
        }
    }

    private suspend fun mapLoginDto(
        dto: FanyaLoginResponseDto,
        provisionalAccountId: String,
        accountInput: String,
        userAgent: String? = null,
    ): AppResult<ChaoxingLoginOutcome> {
        if (dto.status && dto.containTwoFactorLogin) {
            val targetUrl = dto.twoFactorLoginUrl ?: dto.twoFactorLoginPCUrl.orEmpty()
            return AppResult.Success(
                ChaoxingLoginOutcome.RequiresTwoFactor(
                    twoFactorUrl = targetUrl,
                    isMobileUrl = !dto.twoFactorLoginUrl.isNullOrBlank(),
                ),
            )
        }
        if (dto.status) {
            return when (
                val profileRes = finalizeAuthenticatedProfile(provisionalAccountId, accountInput, userAgent)
            ) {
                is AppResult.Success -> AppResult.Success(
                    ChaoxingLoginOutcome.Authenticated(
                        accountId = profileRes.data.first,
                        profile = profileRes.data.second,
                    ),
                )

                is AppResult.Failure -> profileRes
            }
        }
        if (dto.weakpwd) {
            return AppResult.Success(
                ChaoxingLoginOutcome.WeakPasswordRequired(
                    uid = dto.uid.orEmpty(),
                    passwordTimeout = dto.passwordTimeout,
                ),
            )
        }
        val errMsg = dto.msg2.orEmpty().ifBlank { "登录失败，请检查账号和密码" }
        val outcome = if (dto.needVcode == "1" || errMsg.contains("验证码")) {
            ChaoxingLoginOutcome.RequiresCaptcha(errMsg)
        } else {
            ChaoxingLoginOutcome.InvalidCredentials(errMsg)
        }
        return AppResult.Success(outcome)
    }

    private suspend fun finalizeAuthenticatedProfile(
        provisionalAccountId: String,
        fallbackPhone: String,
        userAgent: String? = null,
    ): AppResult<Pair<String, ChaoxingAccountProfile>> {
        val profile = when (val res = fetchAccountProfile(provisionalAccountId, fallbackPhone, userAgent)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> return res
        }
        val canonicalAccountId = "cx_${profile.uid}"
        cookieVault.migrateAccountSession(provisionalAccountId, canonicalAccountId)
        return AppResult.Success(canonicalAccountId to profile)
    }

    companion object {
        private const val QR_STATUS_WAITING = "3"
        private const val QR_STATUS_SCANNED = "4"
        private const val QR_STATUS_CANCELLED = "6"
        private const val QR_STATUS_EXPIRED = "2"
        private const val SCHOOL_SEARCH_PRODUCT_ID = "44"
    }
}
