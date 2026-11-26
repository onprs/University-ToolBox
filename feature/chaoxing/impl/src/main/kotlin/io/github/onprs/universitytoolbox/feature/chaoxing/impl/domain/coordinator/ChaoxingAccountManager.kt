package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator

import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.security.Redactor
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAuditSink
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCourseDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCourseEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCredentialCipher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingDeviceSettingsRepository
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.InMemoryChaoxingDeviceSettingsRepository
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.NoOpChaoxingAuditSink
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAccountProfile
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAuthRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingCourse
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingLoginCipher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingLoginOutcome
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrLoginSession
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrLoginStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device.ChaoxingDeviceIdentityHelper
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device.ChaoxingSharedAccountCodec
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device.ChaoxingSharedAccountPayload
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 账号绑定前置配额与授权检查结果。
 */
sealed interface AccountBindResult {
    data class Bound(
        val account: ChaoxingAccountEntity,
        val isFirstAccount: Boolean = false,
        val updatedDeviceBindingOnly: Boolean = false,
    ) : AccountBindResult

    data class RequiresCaptcha(val message: String) : AccountBindResult

    data class RequiresTwoFactor(val twoFactorUrl: String, val isMobileUrl: Boolean) : AccountBindResult

    data class WeakPasswordRequired(val uid: String) : AccountBindResult

    data class InvalidCredentials(val message: String) : AccountBindResult

    data class QuotaExceeded(val maxSubAccounts: Int, val currentSubAccounts: Int) : AccountBindResult

    data class LicenseDenied(val message: String) : AccountBindResult

    data class Failed(val error: DomainError) : AccountBindResult
}

/**
 * 已绑定账号导出的跨设备绑定分享数据。
 */
class ChaoxingAccountShareBundle(
    val accountId: String,
    val displayName: String,
    val maskedAccount: String,
    val shareUrl: String,
    val qrPngBytes: ByteArray,
    val isUnifiedDeviceCode: Boolean,
)

private data class ResolvedDeviceIdentity(
    val deviceCode: String,
    val isUnified: Boolean,
    val uniqueId: String,
    val deviceModel: String,
)

private class PendingLoginContext(
    val provisionalId: String,
    val accountInput: String,
    val plainPassword: String?,
    val loginFid: String = ChaoxingEndpointConfig.DEFAULT_FID,
    val loginUniqueId: String,
    val loginDeviceModel: String,
    val sharedPayload: ChaoxingSharedAccountPayload? = null,
)

private data class StoredLoginCredentials(val rawLogin: String, val plainPassword: String, val loginFid: String)

private data class ProbedIdentityCandidate(val entity: ChaoxingAccountEntity, val foundServerDeviceCode: Boolean)

/**
 * 多账号签到协调器调用的账号会话修复与设备码维护桥接契约。
 */
interface ChaoxingAccountSessionBridge {
    suspend fun repairSessionIfPossible(accountId: String): Boolean

    suspend fun ensureAccountDeviceIdentity(account: ChaoxingAccountEntity): ChaoxingAccountEntity

    suspend fun rotateRandomDeviceCode(accountId: String): String
}

/**
 * 默认空实现，供独立单元测试在不挂载完整账号管理器时使用。
 */
object NoOpChaoxingAccountSessionBridge : ChaoxingAccountSessionBridge {
    override suspend fun repairSessionIfPossible(accountId: String): Boolean = false

    override suspend fun ensureAccountDeviceIdentity(account: ChaoxingAccountEntity): ChaoxingAccountEntity = account

    override suspend fun rotateRandomDeviceCode(accountId: String): String = ""
}

/**
 * 学习通多账号管理、设备码一致性绑定与授权配额门禁管理器。
 */
@Singleton
@Suppress("TooManyFunctions")
class ChaoxingAccountManager @Inject constructor(
    private val accountDao: ChaoxingAccountDao,
    private val courseDao: ChaoxingCourseDao,
    private val authRemoteDataSource: ChaoxingAuthRemoteDataSource,
    private val remoteDataSource: ChaoxingRemoteDataSource,
    private val cookieVault: AccountCookieVault,
    private val credentialCipher: ChaoxingCredentialCipher,
    private val licenseCoordinator: LicenseCoordinator,
    private val timeProvider: TimeProvider,
    private val auditSink: ChaoxingAuditSink = NoOpChaoxingAuditSink,
    private val deviceSettingsRepository: ChaoxingDeviceSettingsRepository =
        InMemoryChaoxingDeviceSettingsRepository(),
) : ChaoxingAccountSessionBridge {
    /** 实时观察是否隐藏代签列表中的设备码提示图标。 */
    val hideDeviceCodeBadge: Flow<Boolean>
        get() = deviceSettingsRepository.hideDeviceCodeBadge

    /** 更新是否隐藏设备码提示图标。 */
    suspend fun setHideDeviceCodeBadge(hide: Boolean) {
        deviceSettingsRepository.setHideDeviceCodeBadge(hide)
    }

    /** 实时观察本地已绑定的全部账号（主账号优先）。 */
    fun observeAccounts(): Flow<List<ChaoxingAccountEntity>> = accountDao.observeAll()

    /** 观察指定账号缓存的课程列表。 */
    fun observeCourses(accountId: String): Flow<List<ChaoxingCourseEntity>> = courseDao.observeByAccount(accountId)

    /** 判断指定账号本地是否已有缓存的课程记录。 */
    suspend fun hasCachedCourses(accountId: String): Boolean = courseDao.getByAccount(accountId).isNotEmpty()

    /** 判断当前授权与配额状态是否允许再绑定一个新账号。 */
    suspend fun canBindNewAccount(existingAccountId: String? = null): AccountBindResult? {
        if (!licenseCoordinator.canAccess(CHAOXING_TOOL_ID, LicenseTier.STANDARD)) {
            return AccountBindResult.LicenseDenied("当前授权未解锁学习通辅助模块（需 STANDARD 及以上等级）")
        }
        if (existingAccountId != null && accountDao.getById(existingAccountId) != null) {
            return null
        }
        if (accountDao.getPrimaryAccount() == null) {
            return null
        }
        val currentSubCount = accountDao.countSubAccounts()
        return if (!licenseCoordinator.canBindAdditionalSubAccount(currentSubCount)) {
            AccountBindResult.QuotaExceeded(
                maxSubAccounts = licenseCoordinator.maxSubAccounts(),
                currentSubAccounts = currentSubCount,
            )
        } else {
            null
        }
    }

    /** 使用账号密码登录并按主/子账号配额门禁完成加密持久化。 */
    suspend fun bindWithPassword(
        accountInput: String,
        plainPassword: String,
        fid: String = ChaoxingEndpointConfig.DEFAULT_FID,
        validate: String = "",
    ): AccountBindResult {
        if (!licenseCoordinator.canAccess(CHAOXING_TOOL_ID, LicenseTier.STANDARD)) {
            return AccountBindResult.LicenseDenied("当前授权未解锁学习通辅助模块（需 STANDARD 及以上等级）")
        }

        val provisionalId = "pending_${UUID.randomUUID()}"
        val loginUniqueId = ChaoxingDeviceIdentityHelper.generateUniqueId()
        val loginModel = ChaoxingDeviceIdentityHelper.randomMainstreamDeviceModel()
        val customUa = ChaoxingDeviceIdentityHelper.buildClientUserAgent(loginModel, loginUniqueId)
        val normalizedFid = fid.trim().ifBlank { ChaoxingEndpointConfig.DEFAULT_FID }
        val loginRes = authRemoteDataSource.loginWithPassword(
            provisionalAccountId = provisionalId,
            accountInput = accountInput,
            plainPassword = plainPassword,
            fid = normalizedFid,
            validate = validate,
            customUserAgent = customUa,
        )

        return when (loginRes) {
            is AppResult.Failure -> {
                cookieVault.clearAccount(provisionalId)
                AccountBindResult.Failed(loginRes.error)
            }

            is AppResult.Success -> handleLoginOutcome(
                outcome = loginRes.data,
                context = PendingLoginContext(
                    provisionalId = provisionalId,
                    accountInput = accountInput,
                    plainPassword = plainPassword,
                    loginFid = normalizedFid,
                    loginUniqueId = loginUniqueId,
                    loginDeviceModel = loginModel,
                ),
            )
        }
    }

    /** 通过账号绑定二维码内容或分享深链一键导入账号并同步统一设备码。 */
    suspend fun importFromSharedText(rawText: String, validate: String = ""): AccountBindResult {
        if (!licenseCoordinator.canAccess(CHAOXING_TOOL_ID, LicenseTier.STANDARD)) {
            return AccountBindResult.LicenseDenied("当前授权未解锁学习通辅助模块（需 STANDARD 及以上等级）")
        }
        val payload = ChaoxingSharedAccountCodec.parseShareText(rawText)
            ?: return AccountBindResult.InvalidCredentials("未识别到有效的账号绑定二维码或导入链接")

        val provisionalId = "import_${UUID.randomUUID()}"
        val loginUniqueId = payload.uniqueId.ifBlank { ChaoxingDeviceIdentityHelper.generateUniqueId() }
        val loginModel = payload.deviceModel.ifBlank { ChaoxingDeviceIdentityHelper.randomMainstreamDeviceModel() }
        val customUa = ChaoxingDeviceIdentityHelper.buildClientUserAgent(loginModel, loginUniqueId)
        val decryptedPlainPwd = runCatching { ChaoxingLoginCipher.decrypt(payload.encryptedPassword) }.getOrNull()

        val normalizedFid = payload.fid.trim().ifBlank { ChaoxingEndpointConfig.DEFAULT_FID }
        val loginRes = authRemoteDataSource.loginWithPassword(
            provisionalAccountId = provisionalId,
            accountInput = payload.accountInput,
            plainPassword = payload.encryptedPassword,
            fid = normalizedFid,
            validate = validate,
            isPasswordEncrypted = true,
            customUserAgent = customUa,
        )

        return when (loginRes) {
            is AppResult.Failure -> {
                cookieVault.clearAccount(provisionalId)
                AccountBindResult.Failed(loginRes.error)
            }

            is AppResult.Success -> handleLoginOutcome(
                outcome = loginRes.data,
                context = PendingLoginContext(
                    provisionalId = provisionalId,
                    accountInput = payload.accountInput,
                    plainPassword = decryptedPlainPwd,
                    loginFid = normalizedFid,
                    loginUniqueId = loginUniqueId,
                    loginDeviceModel = loginModel,
                    sharedPayload = payload,
                ),
            )
        }
    }

    /** 生成指定已绑定账号的绑定二维码与导入链接（携带统一设备码）。 */
    suspend fun createShareBundle(accountId: String): AppResult<ChaoxingAccountShareBundle> {
        val account = accountDao.getById(accountId)
            ?: return AppResult.Failure(DomainError.NotFound("账号不存在"))
        val creds = decryptStoredCredentials(account)
            ?: return AppResult.Failure(
                DomainError.Validation("该账号未保存登录密码，请使用账号密码重新登录一次后再分享绑定"),
            )
        val readyAccount = ensureAccountDeviceIdentity(account)
        val payload = ChaoxingSharedAccountPayload(
            accountInput = creds.rawLogin,
            encryptedPassword = ChaoxingLoginCipher.encrypt(creds.plainPassword),
            displayName = readyAccount.displayName,
            fid = creds.loginFid,
            deviceCode = readyAccount.deviceCode,
            uniqueId = readyAccount.clientUniqueId,
            deviceModel = readyAccount.deviceModel,
        )
        val shareUrl = ChaoxingSharedAccountCodec.encodeShareUrl(payload)
        val qrPng = ChaoxingSharedAccountCodec.generateQrPngBytes(shareUrl)
        return AppResult.Success(
            ChaoxingAccountShareBundle(
                accountId = readyAccount.accountId,
                displayName = readyAccount.displayName,
                maskedAccount = readyAccount.maskedAccount,
                shareUrl = shareUrl,
                qrPngBytes = qrPng,
                isUnifiedDeviceCode = readyAccount.isUnifiedDeviceCode,
            ),
        )
    }

    /** 当账号存有加密凭据时，使用专属客户端指纹自动重登续期 Cookie 分区。 */
    override suspend fun repairSessionIfPossible(accountId: String): Boolean {
        val account = accountDao.getById(accountId) ?: return false
        val creds = decryptStoredCredentials(account) ?: return false
        val ready = ensureAccountDeviceIdentity(account)
        val customUa = ChaoxingDeviceIdentityHelper.buildClientUserAgent(ready.deviceModel, ready.clientUniqueId)

        suspend fun attemptLogin(targetFid: String): Boolean {
            val provisionalId = "repair_${UUID.randomUUID()}"
            val loginRes = authRemoteDataSource.loginWithPassword(
                provisionalAccountId = provisionalId,
                accountInput = creds.rawLogin,
                plainPassword = creds.plainPassword,
                fid = targetFid,
                customUserAgent = customUa,
            )
            val authenticated = (loginRes as? AppResult.Success)?.data as? ChaoxingLoginOutcome.Authenticated
            if (authenticated != null) {
                if (authenticated.accountId != accountId) {
                    cookieVault.migrateAccountSession(authenticated.accountId, accountId)
                }
            } else {
                cookieVault.clearAccount(provisionalId)
            }
            return authenticated != null
        }

        val recovered = attemptLogin(creds.loginFid) ||
            (
                creds.loginFid == ChaoxingEndpointConfig.DEFAULT_FID &&
                    ready.fid.isNotBlank() &&
                    ready.fid != ChaoxingEndpointConfig.DEFAULT_FID &&
                    ready.fid != "0" &&
                    attemptLogin(ready.fid)
                )
        if (recovered) {
            accountDao.updateSessionStatus(accountId, SESSION_ACTIVE, timeProvider.currentTimeMillis())
        }
        return recovered
    }

    /** 确保账号拥有非空设备码与客户端会话指纹（兼容旧库记录平滑补齐）。 */
    override suspend fun ensureAccountDeviceIdentity(account: ChaoxingAccountEntity): ChaoxingAccountEntity {
        if (account.deviceCode.isNotBlank() &&
            account.clientUniqueId.isNotBlank() &&
            account.deviceModel.isNotBlank()
        ) {
            return account
        }
        val resolved = resolveDeviceIdentity(
            existing = account,
            isPrimary = account.isPrimary,
            loginUniqueId = ChaoxingDeviceIdentityHelper.generateUniqueId(),
            loginDeviceModel = ChaoxingDeviceIdentityHelper.randomMainstreamDeviceModel(),
            sharedPayload = null,
        )
        accountDao.updateDeviceIdentity(
            accountId = account.accountId,
            deviceCode = resolved.deviceCode,
            isUnified = resolved.isUnified,
            uniqueId = resolved.uniqueId,
            deviceModel = resolved.deviceModel,
        )
        return account.copy(
            deviceCode = resolved.deviceCode,
            isUnifiedDeviceCode = resolved.isUnified,
            clientUniqueId = resolved.uniqueId,
            deviceModel = resolved.deviceModel,
        )
    }

    /** 在发生设备码重复冲突时重置该子账号的独立随机设备码。 */
    override suspend fun rotateRandomDeviceCode(accountId: String): String {
        val existing = accountDao.getById(accountId)
        val newCode = ChaoxingDeviceIdentityHelper.generateRandomDeviceCode()
        val uniqueId = existing?.clientUniqueId.orEmpty().ifBlank { ChaoxingDeviceIdentityHelper.generateUniqueId() }
        val model = existing?.deviceModel.orEmpty().ifBlank {
            ChaoxingDeviceIdentityHelper.randomMainstreamDeviceModel()
        }
        accountDao.updateDeviceIdentity(
            accountId = accountId,
            deviceCode = newCode,
            isUnified = false,
            uniqueId = uniqueId,
            deviceModel = model,
        )
        return newCode
    }

    /** 初始化扫码登录会话。 */
    suspend fun startQrLogin(): AppResult<ChaoxingQrLoginSession> {
        if (!licenseCoordinator.canAccess(CHAOXING_TOOL_ID, LicenseTier.STANDARD)) {
            return AppResult.Failure(DomainError.Unauthorized("当前授权未解锁学习通辅助模块"))
        }
        val provisionalId = "qr_pending_${UUID.randomUUID()}"
        return authRemoteDataSource.createQrLoginSession(provisionalId)
    }

    /** 轮询扫码状态，并在确认后执行配额检查与入库。 */
    suspend fun pollQrLogin(session: ChaoxingQrLoginSession): AccountBindResult? {
        val pollRes = authRemoteDataSource.pollQrLoginStatus(
            session.provisionalAccountId,
            session.uuid,
            session.enc,
        )
        return when (pollRes) {
            is AppResult.Failure -> AccountBindResult.Failed(pollRes.error)
            is AppResult.Success -> when (val status = pollRes.data) {
                is ChaoxingQrLoginStatus.Confirmed -> persistAuthenticatedAccount(
                    accountId = status.accountId,
                    profile = status.profile,
                    context = PendingLoginContext(
                        provisionalId = session.provisionalAccountId,
                        accountInput = "",
                        plainPassword = null,
                        loginUniqueId = ChaoxingDeviceIdentityHelper.generateUniqueId(),
                        loginDeviceModel = ChaoxingDeviceIdentityHelper.randomMainstreamDeviceModel(),
                    ),
                )

                is ChaoxingQrLoginStatus.Cancelled -> {
                    cookieVault.clearAccount(session.provisionalAccountId)
                    AccountBindResult.InvalidCredentials("已在客户端取消扫码登录")
                }

                is ChaoxingQrLoginStatus.Expired -> {
                    cookieVault.clearAccount(session.provisionalAccountId)
                    AccountBindResult.InvalidCredentials("登录二维码已失效，请刷新重试")
                }

                is ChaoxingQrLoginStatus.Failed -> {
                    cookieVault.clearAccount(session.provisionalAccountId)
                    AccountBindResult.InvalidCredentials(status.message)
                }

                ChaoxingQrLoginStatus.Waiting,
                is ChaoxingQrLoginStatus.ScannedPendingConfirm,
                -> null
            }
        }
    }

    /** 刷新指定账号的在线课程缓存并更新会话健康状态；会话过期且存有凭据时自动静默重连重试一次。 */
    suspend fun refreshCourses(accountId: String): AppResult<List<ChaoxingCourse>> {
        val firstRes = remoteDataSource.fetchCourses(accountId)
        val coursesRes = if (firstRes is AppResult.Failure &&
            firstRes.error is DomainError.Unauthorized &&
            repairSessionIfPossible(accountId)
        ) {
            remoteDataSource.fetchCourses(accountId)
        } else {
            firstRes
        }
        val nowMs = timeProvider.currentTimeMillis()
        when (coursesRes) {
            is AppResult.Success -> {
                accountDao.updateSessionStatus(accountId, SESSION_ACTIVE, nowMs)
                val entities = coursesRes.data.map { course ->
                    ChaoxingCourseEntity(
                        accountId = accountId,
                        courseId = course.courseId,
                        clazzId = course.clazzId,
                        cpi = course.cpi,
                        name = course.name,
                        className = course.className,
                        teacherName = course.teacherName,
                        coverUrl = course.coverUrl,
                        studentCount = course.studentCount,
                        isArchived = course.isArchived,
                        isPinned = course.isPinned,
                        updatedAtMs = nowMs,
                    )
                }
                courseDao.replaceForAccount(accountId, entities)
            }

            is AppResult.Failure -> {
                if (coursesRes.error is DomainError.Unauthorized) {
                    accountDao.updateSessionStatus(accountId, SESSION_EXPIRED, nowMs)
                }
            }
        }
        return coursesRes
    }

    /** 切换账号是否参与批量签到协调。 */
    suspend fun setAccountEnabled(accountId: String, enabled: Boolean) {
        accountDao.updateEnabled(accountId, enabled)
    }

    /** 将指定账号设为主账号。 */
    suspend fun promoteToPrimary(accountId: String) {
        accountDao.markPrimary(accountId)
    }

    /** 解绑账号并彻底清理加密凭据、Cookie 隔离分区与课程缓存。 */
    suspend fun removeAccount(accountId: String) {
        val target = accountDao.getById(accountId)
        cookieVault.clearAccount(accountId)
        courseDao.deleteByAccount(accountId)
        accountDao.deleteById(accountId)
        if (target?.isPrimary == true) {
            val nextPrimary = accountDao.getAll().firstOrNull()
            if (nextPrimary != null) {
                accountDao.markPrimary(nextPrimary.accountId)
            }
        }
        auditSink.recordSummary(
            action = "chaoxing_account_remove",
            status = "SUCCESS",
            summary = "已移除绑定账号 ${target?.maskedAccount.orEmpty()}",
        )
    }

    private suspend fun handleLoginOutcome(
        outcome: ChaoxingLoginOutcome,
        context: PendingLoginContext,
    ): AccountBindResult = when (outcome) {
        is ChaoxingLoginOutcome.Authenticated -> persistAuthenticatedAccount(
            accountId = outcome.accountId,
            profile = outcome.profile,
            context = context,
        )

        is ChaoxingLoginOutcome.RequiresCaptcha -> {
            cookieVault.clearAccount(context.provisionalId)
            AccountBindResult.RequiresCaptcha(outcome.message)
        }

        is ChaoxingLoginOutcome.RequiresTwoFactor -> {
            cookieVault.clearAccount(context.provisionalId)
            AccountBindResult.RequiresTwoFactor(outcome.twoFactorUrl, outcome.isMobileUrl)
        }

        is ChaoxingLoginOutcome.WeakPasswordRequired -> {
            cookieVault.clearAccount(context.provisionalId)
            AccountBindResult.WeakPasswordRequired(outcome.uid)
        }

        is ChaoxingLoginOutcome.InvalidCredentials -> {
            cookieVault.clearAccount(context.provisionalId)
            AccountBindResult.InvalidCredentials(outcome.message)
        }

        is ChaoxingLoginOutcome.Failed -> {
            cookieVault.clearAccount(context.provisionalId)
            AccountBindResult.Failed(outcome.error)
        }
    }

    private suspend fun persistAuthenticatedAccount(
        accountId: String,
        profile: ChaoxingAccountProfile,
        context: PendingLoginContext,
    ): AccountBindResult {
        val existing = accountDao.getById(accountId)
        val quotaViolation = canBindNewAccount(existingAccountId = existing?.accountId)
        if (quotaViolation != null) {
            cookieVault.clearAccount(accountId)
            return quotaViolation
        }

        val hadAnyAccountBefore = accountDao.getAll().isNotEmpty()
        val isPrimary = existing?.isPrimary ?: (accountDao.getPrimaryAccount() == null)
        val isFirstAccount = !hadAnyAccountBefore && existing == null
        val encryptedPwd = if (!context.plainPassword.isNullOrEmpty()) {
            credentialCipher.encrypt(context.plainPassword, associatedData = accountId)
        } else {
            existing?.encryptedPasswordCiphertext
        }
        val trimmedInput = context.accountInput.trim()
        val encodedLoginInput = encodeStoredLoginInput(context.loginFid, trimmedInput)
        val encryptedLoginInput = if (encodedLoginInput.isNotEmpty()) {
            credentialCipher.encrypt(encodedLoginInput, associatedData = loginAssociatedData(accountId))
        } else {
            existing?.encryptedAccountInputCiphertext
        }
        val deviceIdentity = resolveDeviceIdentity(
            existing = existing,
            isPrimary = isPrimary,
            loginUniqueId = context.loginUniqueId,
            loginDeviceModel = context.loginDeviceModel,
            sharedPayload = context.sharedPayload,
        )
        val masked = profile.maskedPhone.ifBlank { Redactor.maskPhone(trimmedInput) }
        val nowMs = timeProvider.currentTimeMillis()
        val displayName = profile.displayName.ifBlank { context.sharedPayload?.displayName.orEmpty() }

        val initialEntity = ChaoxingAccountEntity(
            accountId = accountId,
            uid = profile.uid,
            fid = profile.fid,
            maskedAccount = masked,
            displayName = displayName,
            schoolName = profile.schoolName,
            avatarUrl = profile.avatarUrl,
            encryptedPasswordCiphertext = encryptedPwd,
            encryptedCookieVaultRef = accountId,
            isPrimary = isPrimary,
            isEnabled = existing?.isEnabled ?: true,
            sessionStatus = SESSION_ACTIVE,
            lastVerifiedAtMs = nowMs,
            createdAtMs = existing?.createdAtMs ?: nowMs,
            encryptedAccountInputCiphertext = encryptedLoginInput,
            deviceCode = deviceIdentity.deviceCode,
            isUnifiedDeviceCode = deviceIdentity.isUnified,
            clientUniqueId = deviceIdentity.uniqueId,
            deviceModel = deviceIdentity.deviceModel,
        )
        accountDao.upsert(initialEntity)

        val finalEntity = if (context.sharedPayload?.deviceCode.isNullOrBlank()) {
            ChaoxingHistoricalDeviceProber.probeAndPersist(initialEntity, remoteDataSource, accountDao)
        } else {
            initialEntity
        }
        val updatedDeviceOnly = existing != null && !context.sharedPayload?.deviceCode.isNullOrBlank()
        auditSink.recordSummary(
            action = "chaoxing_account_bind",
            status = "SUCCESS",
            summary = "已绑定账号 $masked（${if (isPrimary) "主账号" else "子账号"}）",
        )
        return AccountBindResult.Bound(
            account = finalEntity,
            isFirstAccount = isFirstAccount,
            updatedDeviceBindingOnly = updatedDeviceOnly,
        )
    }

    private fun encodeStoredLoginInput(loginFid: String, trimmedInput: String): String {
        if (trimmedInput.isEmpty()) return ""
        val normalizedLoginFid = loginFid.trim().ifBlank { ChaoxingEndpointConfig.DEFAULT_FID }
        return if (normalizedLoginFid != ChaoxingEndpointConfig.DEFAULT_FID) {
            "$normalizedLoginFid\n$trimmedInput"
        } else {
            trimmedInput
        }
    }

    private suspend fun resolveDeviceIdentity(
        existing: ChaoxingAccountEntity?,
        isPrimary: Boolean,
        loginUniqueId: String,
        loginDeviceModel: String,
        sharedPayload: ChaoxingSharedAccountPayload?,
    ): ResolvedDeviceIdentity {
        val sharedCode = sharedPayload?.deviceCode?.trim().orEmpty()
        if (sharedCode.isNotEmpty()) {
            return ResolvedDeviceIdentity(
                deviceCode = sharedCode,
                isUnified = true,
                uniqueId = sharedPayload?.uniqueId.orEmpty()
                    .ifBlank { existing?.clientUniqueId.orEmpty() }
                    .ifBlank { loginUniqueId },
                deviceModel = sharedPayload?.deviceModel.orEmpty()
                    .ifBlank { existing?.deviceModel.orEmpty() }
                    .ifBlank { loginDeviceModel },
            )
        }
        if (existing != null && existing.deviceCode.isNotBlank()) {
            return ResolvedDeviceIdentity(
                deviceCode = existing.deviceCode,
                isUnified = existing.isUnifiedDeviceCode,
                uniqueId = existing.clientUniqueId.ifBlank { loginUniqueId },
                deviceModel = existing.deviceModel.ifBlank { loginDeviceModel },
            )
        }
        if (isPrimary) {
            val (localCode, isUnified) = deviceSettingsRepository.getLocalMachineDeviceCode()
            return ResolvedDeviceIdentity(
                deviceCode = localCode,
                isUnified = isUnified,
                uniqueId = loginUniqueId,
                deviceModel = deviceSettingsRepository.getLocalDeviceModel(),
            )
        }
        return ResolvedDeviceIdentity(
            deviceCode = ChaoxingDeviceIdentityHelper.generateRandomDeviceCode(),
            isUnified = false,
            uniqueId = loginUniqueId,
            deviceModel = loginDeviceModel,
        )
    }

    private suspend fun decryptStoredCredentials(account: ChaoxingAccountEntity): StoredLoginCredentials? {
        val loginCipher = account.encryptedAccountInputCiphertext?.takeIf { it.isNotBlank() } ?: return null
        val pwdCipher = account.encryptedPasswordCiphertext?.takeIf { it.isNotBlank() } ?: return null
        val decodedLogin = runCatching {
            credentialCipher.decrypt(loginCipher, associatedData = loginAssociatedData(accountId = account.accountId))
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val plainPwd = runCatching {
            credentialCipher.decrypt(pwdCipher, associatedData = account.accountId)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        val newlineIndex = decodedLogin.indexOf('\n')
        val (loginFid, rawLogin) = if (newlineIndex > 0) {
            val fidPart = decodedLogin.substring(0, newlineIndex).trim().ifBlank { ChaoxingEndpointConfig.DEFAULT_FID }
            val loginPart = decodedLogin.substring(newlineIndex + 1).trim()
            fidPart to loginPart
        } else {
            ChaoxingEndpointConfig.DEFAULT_FID to decodedLogin.trim()
        }
        if (rawLogin.isEmpty()) {
            return null
        }
        return StoredLoginCredentials(rawLogin = rawLogin, plainPassword = plainPwd, loginFid = loginFid)
    }

    private fun loginAssociatedData(accountId: String): String = "${accountId}_login"

    companion object {
        const val SESSION_ACTIVE = "ACTIVE"
        const val SESSION_EXPIRED = "EXPIRED"
    }
}

private object ChaoxingHistoricalDeviceProber {
    private const val MAX_PROBE_COURSES = 2
    private const val MAX_PROBE_ACTIVITIES_PER_COURSE = 3

    suspend fun probeAndPersist(
        entity: ChaoxingAccountEntity,
        remoteDataSource: ChaoxingRemoteDataSource,
        accountDao: ChaoxingAccountDao,
    ): ChaoxingAccountEntity {
        val courses = (remoteDataSource.fetchCourses(entity.accountId) as? AppResult.Success)
            ?.data
            ?.filterNot { it.isArchived }
            ?.take(MAX_PROBE_COURSES)
            .orEmpty()
        var fallbackUaIdentity: ChaoxingAccountEntity? = null
        for (course in courses) {
            val probed = probeCourse(entity, course, fallbackUaIdentity, remoteDataSource)
            if (probed != null) {
                if (probed.foundServerDeviceCode) {
                    persist(accountDao, probed.entity)
                    return probed.entity
                }
                if (fallbackUaIdentity == null) {
                    fallbackUaIdentity = probed.entity
                }
            }
        }
        if (fallbackUaIdentity != null) {
            persist(accountDao, fallbackUaIdentity)
            return fallbackUaIdentity
        }
        return entity
    }

    private suspend fun probeCourse(
        entity: ChaoxingAccountEntity,
        course: ChaoxingCourse,
        currentFallback: ChaoxingAccountEntity?,
        remoteDataSource: ChaoxingRemoteDataSource,
    ): ProbedIdentityCandidate? {
        val activities = (
            remoteDataSource.fetchActivities(
                accountId = entity.accountId,
                fid = entity.fid,
                courseId = course.courseId,
                clazzId = course.clazzId,
                cpi = course.cpi,
            ) as? AppResult.Success
            )?.data.orEmpty()
        val signedItems = activities.filter { it.userStatus.isSignedSuccess }.take(MAX_PROBE_ACTIVITIES_PER_COURSE)
        var uaCandidate: ProbedIdentityCandidate? = null
        for (signed in signedItems) {
            val record = remoteDataSource.fetchSignRecordDeviceInfo(entity.accountId, signed.activeId)
            if (record != null) {
                val (parsedModel, parsedUid) = ChaoxingDeviceIdentityHelper.parseUserAgentIdentity(record.second)
                val base = uaCandidate?.entity ?: currentFallback ?: entity
                val updatedUid = parsedUid ?: base.clientUniqueId
                val updatedModel = parsedModel ?: base.deviceModel
                val probedCode = record.first
                if (!probedCode.isNullOrBlank()) {
                    return ProbedIdentityCandidate(
                        entity = entity.copy(
                            deviceCode = probedCode,
                            isUnifiedDeviceCode = true,
                            clientUniqueId = updatedUid,
                            deviceModel = updatedModel,
                        ),
                        foundServerDeviceCode = true,
                    )
                }
                if (uaCandidate == null && (parsedModel != null || parsedUid != null)) {
                    uaCandidate = ProbedIdentityCandidate(
                        entity = entity.copy(
                            clientUniqueId = updatedUid,
                            deviceModel = updatedModel,
                        ),
                        foundServerDeviceCode = false,
                    )
                }
            }
        }
        return uaCandidate
    }

    private suspend fun persist(accountDao: ChaoxingAccountDao, probed: ChaoxingAccountEntity) {
        accountDao.updateDeviceIdentity(
            accountId = probed.accountId,
            deviceCode = probed.deviceCode,
            isUnified = probed.isUnifiedDeviceCode,
            uniqueId = probed.clientUniqueId,
            deviceModel = probed.deviceModel,
        )
    }
}
