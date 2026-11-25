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
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.NoOpChaoxingAuditSink
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAccountProfile
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingAuthRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingCourse
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingLoginOutcome
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrLoginSession
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrLoginStatus
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieVault
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 账号绑定前置配额与授权检查结果。
 */
sealed interface AccountBindResult {
    data class Bound(val account: ChaoxingAccountEntity) : AccountBindResult

    data class RequiresCaptcha(val message: String) : AccountBindResult

    data class RequiresTwoFactor(val twoFactorUrl: String, val isMobileUrl: Boolean) : AccountBindResult

    data class WeakPasswordRequired(val uid: String) : AccountBindResult

    data class InvalidCredentials(val message: String) : AccountBindResult

    data class QuotaExceeded(val maxSubAccounts: Int, val currentSubAccounts: Int) : AccountBindResult

    data class LicenseDenied(val message: String) : AccountBindResult

    data class Failed(val error: DomainError) : AccountBindResult
}

/**
 * 学习通多账号管理与授权配额门禁管理器。
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
) {
    /** 实时观察本地已绑定的全部账号（主账号优先）。 */
    fun observeAccounts(): Flow<List<ChaoxingAccountEntity>> = accountDao.observeAll()

    /** 观察指定账号缓存的课程列表。 */
    fun observeCourses(accountId: String): Flow<List<ChaoxingCourseEntity>> = courseDao.observeByAccount(accountId)

    /** 判断当前授权与配额状态是否允许再绑定一个新账号。 */
    suspend fun canBindNewAccount(existingAccountId: String? = null): AccountBindResult? {
        if (!licenseCoordinator.canAccess(CHAOXING_TOOL_ID, LicenseTier.STANDARD)) {
            return AccountBindResult.LicenseDenied("当前授权未解锁学习通辅助模块（需 STANDARD 及以上等级）")
        }
        if (existingAccountId != null && accountDao.getById(existingAccountId) != null) {
            return null
        }
        val primary = accountDao.getPrimaryAccount()
        if (primary == null) {
            return null
        }
        val currentSubCount = accountDao.countSubAccounts()
        if (!licenseCoordinator.canBindAdditionalSubAccount(currentSubCount)) {
            return AccountBindResult.QuotaExceeded(
                maxSubAccounts = licenseCoordinator.maxSubAccounts(),
                currentSubAccounts = currentSubCount,
            )
        }
        return null
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
        val loginRes = authRemoteDataSource.loginWithPassword(
            provisionalAccountId = provisionalId,
            accountInput = accountInput,
            plainPassword = plainPassword,
            fid = fid,
            validate = validate,
        )

        return when (loginRes) {
            is AppResult.Failure -> {
                cookieVault.clearAccount(provisionalId)
                AccountBindResult.Failed(loginRes.error)
            }

            is AppResult.Success -> handleLoginOutcome(
                provisionalId = provisionalId,
                outcome = loginRes.data,
                accountInput = accountInput,
                plainPassword = plainPassword,
            )
        }
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
                    rawAccountInput = "",
                    plainPassword = null,
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

    /** 刷新指定账号的在线课程缓存并更新会话健康状态。 */
    suspend fun refreshCourses(accountId: String): AppResult<List<ChaoxingCourse>> {
        val coursesRes = remoteDataSource.fetchCourses(accountId)
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
        provisionalId: String,
        outcome: ChaoxingLoginOutcome,
        accountInput: String,
        plainPassword: String,
    ): AccountBindResult = when (outcome) {
        is ChaoxingLoginOutcome.Authenticated -> persistAuthenticatedAccount(
            accountId = outcome.accountId,
            profile = outcome.profile,
            rawAccountInput = accountInput,
            plainPassword = plainPassword,
        )

        is ChaoxingLoginOutcome.RequiresCaptcha -> {
            cookieVault.clearAccount(provisionalId)
            AccountBindResult.RequiresCaptcha(outcome.message)
        }

        is ChaoxingLoginOutcome.RequiresTwoFactor -> {
            cookieVault.clearAccount(provisionalId)
            AccountBindResult.RequiresTwoFactor(outcome.twoFactorUrl, outcome.isMobileUrl)
        }

        is ChaoxingLoginOutcome.WeakPasswordRequired -> {
            cookieVault.clearAccount(provisionalId)
            AccountBindResult.WeakPasswordRequired(outcome.uid)
        }

        is ChaoxingLoginOutcome.InvalidCredentials -> {
            cookieVault.clearAccount(provisionalId)
            AccountBindResult.InvalidCredentials(outcome.message)
        }

        is ChaoxingLoginOutcome.Failed -> {
            cookieVault.clearAccount(provisionalId)
            AccountBindResult.Failed(outcome.error)
        }
    }

    private suspend fun persistAuthenticatedAccount(
        accountId: String,
        profile: ChaoxingAccountProfile,
        rawAccountInput: String,
        plainPassword: String?,
    ): AccountBindResult {
        val existing = accountDao.getById(accountId)
        val quotaViolation = canBindNewAccount(existingAccountId = existing?.accountId)
        if (quotaViolation != null) {
            cookieVault.clearAccount(accountId)
            return quotaViolation
        }

        val primaryExists = accountDao.getPrimaryAccount() != null
        val isPrimary = existing?.isPrimary ?: !primaryExists
        val encryptedPwd = if (!plainPassword.isNullOrEmpty()) {
            credentialCipher.encrypt(plainPassword, associatedData = accountId)
        } else {
            existing?.encryptedPasswordCiphertext
        }
        val masked = profile.maskedPhone.ifBlank { Redactor.maskPhone(rawAccountInput) }
        val nowMs = timeProvider.currentTimeMillis()

        val entity = ChaoxingAccountEntity(
            accountId = accountId,
            uid = profile.uid,
            fid = profile.fid,
            maskedAccount = masked,
            displayName = profile.displayName,
            schoolName = profile.schoolName,
            avatarUrl = profile.avatarUrl,
            encryptedPasswordCiphertext = encryptedPwd,
            encryptedCookieVaultRef = accountId,
            isPrimary = isPrimary,
            isEnabled = existing?.isEnabled ?: true,
            sessionStatus = SESSION_ACTIVE,
            lastVerifiedAtMs = nowMs,
            createdAtMs = existing?.createdAtMs ?: nowMs,
        )
        accountDao.upsert(entity)
        auditSink.recordSummary(
            action = "chaoxing_account_bind",
            status = "SUCCESS",
            summary = "已绑定账号 $masked（${if (isPrimary) "主账号" else "子账号"}）",
        )
        return AccountBindResult.Bound(entity)
    }

    companion object {
        const val SESSION_ACTIVE = "ACTIVE"
        const val SESSION_EXPIRED = "EXPIRED"
        const val SESSION_CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED"
    }
}
