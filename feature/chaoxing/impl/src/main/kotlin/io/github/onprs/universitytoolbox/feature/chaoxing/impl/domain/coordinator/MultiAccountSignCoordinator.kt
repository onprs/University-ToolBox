package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator

import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAuditSink
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCourseDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingSignRecordDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingSignRecordEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.NoOpChaoxingAuditSink
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActiveDetail
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingRemoteDataSource
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit.ChaoxingRateLimitCoordinator
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignAccountIdentity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionRequest
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignExecutionResult
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignStrategyDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单个账号在批量签到中的执行明细报告。
 */
data class AccountSignReport(
    val accountId: String,
    val maskedAccount: String,
    val displayName: String,
    val result: SignExecutionResult,
)

/**
 * 多账号协同签到汇总报告。
 */
data class BatchSignSummary(val activeId: Long, val reports: List<AccountSignReport>)

/**
 * 多账号批量签到与风控限流协调器。
 * - 按 `LicenseCoordinator` 校验工具授权及子账号并发配额；
 * - 对同一课程活动按顺序注入随机抖动延迟并消耗令牌桶配额；
 * - 持久化详细本地签到审计记录与全局脱敏摘要日志。
 */
@Singleton
class MultiAccountSignCoordinator @Inject constructor(
    private val accountDao: ChaoxingAccountDao,
    private val courseDao: ChaoxingCourseDao,
    private val signRecordDao: ChaoxingSignRecordDao,
    private val remoteDataSource: ChaoxingRemoteDataSource,
    private val strategyDispatcher: SignStrategyDispatcher,
    private val rateLimitCoordinator: ChaoxingRateLimitCoordinator,
    private val licenseCoordinator: LicenseCoordinator,
    private val timeProvider: TimeProvider,
    private val auditSink: ChaoxingAuditSink = NoOpChaoxingAuditSink,
) {
    /**
     * 对指定账号集合执行签到或签退（单账号时 `targetAccountIds` 仅含一个 ID）。
     */
    suspend fun executeForAccounts(
        targetAccountIds: List<String>,
        courseName: String,
        activityTitle: String,
        request: SignExecutionRequest,
        prefetchedDetail: ChaoxingActiveDetail? = null,
    ): BatchSignSummary {
        if (!licenseCoordinator.canAccess(CHAOXING_TOOL_ID, LicenseTier.STANDARD)) {
            val denial = SignExecutionResult.QuotaRestricted("当前授权未解锁学习通辅助功能")
            val deniedReports = targetAccountIds.map { id ->
                AccountSignReport(
                    accountId = id,
                    maskedAccount = id,
                    displayName = "",
                    result = denial,
                )
            }
            return BatchSignSummary(activeId = request.activeId, reports = deniedReports)
        }

        val allAccounts = accountDao.getAll().associateBy { it.accountId }
        val maxSubQuota = licenseCoordinator.maxSubAccounts()
        var subAccountsUsed = 0
        val reports = ArrayList<AccountSignReport>(targetAccountIds.size)

        targetAccountIds.forEachIndexed { index, accountId ->
            val account = allAccounts[accountId]
            if (account == null) {
                reports += AccountSignReport(
                    accountId = accountId,
                    maskedAccount = accountId,
                    displayName = "",
                    result = SignExecutionResult.Failed(DomainError.NotFound("账号不存在")),
                )
                return@forEachIndexed
            }

            if (!account.isPrimary) {
                if (subAccountsUsed >= maxSubQuota) {
                    val restricted = SignExecutionResult.QuotaRestricted(
                        "超出当前授权子账号配额（上限 $maxSubQuota）",
                    )
                    reports += reportAndRecord(account, courseName, activityTitle, request, restricted)
                    return@forEachIndexed
                }
                subAccountsUsed++
            }

            rateLimitCoordinator.awaitAccountTurn(index)
            val resolvedRequest = resolveCourseContextForAccount(account, request)
            val execResult = strategyDispatcher.dispatch(
                account = account.toIdentity(),
                request = resolvedRequest,
                prefetchedDetail = if (index == 0) prefetchedDetail else null,
            )

            if (execResult is SignExecutionResult.SessionExpired) {
                accountDao.updateSessionStatus(
                    accountId = account.accountId,
                    sessionStatus = ChaoxingAccountManager.SESSION_EXPIRED,
                    verifiedAtMs = timeProvider.currentTimeMillis(),
                )
            }
            reports += reportAndRecord(account, courseName, activityTitle, resolvedRequest, execResult)
        }

        auditSink.recordSummary(
            action = "chaoxing_batch_sign",
            status = if (reports.any { it.result is SignExecutionResult.Success }) "SUCCESS" else "COMPLETED",
            summary = "活动 ${request.activeId} 共处理 ${reports.size} 个账号",
        )
        return BatchSignSummary(activeId = request.activeId, reports = reports)
    }

    private suspend fun resolveCourseContextForAccount(
        account: ChaoxingAccountEntity,
        baseRequest: SignExecutionRequest,
    ): SignExecutionRequest {
        if (account.isPrimary) {
            return baseRequest
        }
        val cachedCourses = courseDao.getByAccount(account.accountId)
        val matched = cachedCourses.firstOrNull { it.courseId == baseRequest.courseId }
            ?: run {
                val fetched = remoteDataSource.fetchCourses(account.accountId)
                if (fetched is AppResult.Success) {
                    fetched.data.firstOrNull { it.courseId == baseRequest.courseId }
                        ?.let { course ->
                            return baseRequest.copy(clazzId = course.clazzId)
                        }
                }
                null
            }
        return if (matched != null) {
            baseRequest.copy(clazzId = matched.clazzId)
        } else {
            baseRequest
        }
    }

    private suspend fun reportAndRecord(
        account: ChaoxingAccountEntity,
        courseName: String,
        activityTitle: String,
        request: SignExecutionRequest,
        result: SignExecutionResult,
    ): AccountSignReport {
        val (outcomeCode, outcomeMessage) = result.toRecordSummary()
        signRecordDao.insert(
            ChaoxingSignRecordEntity(
                accountId = account.accountId,
                maskedAccount = account.maskedAccount,
                courseId = request.courseId,
                courseName = courseName,
                clazzId = request.clazzId,
                activeId = request.activeId,
                activityTitle = activityTitle,
                signType = resolveSignTypeLabel(request),
                outcome = outcomeCode,
                message = outcomeMessage,
                timestampMs = timeProvider.currentTimeMillis(),
            ),
        )
        return AccountSignReport(
            accountId = account.accountId,
            maskedAccount = account.maskedAccount,
            displayName = account.displayName,
            result = result,
        )
    }

    private fun resolveSignTypeLabel(request: SignExecutionRequest): String = when {
        !request.enc.isNullOrBlank() || !request.rawQrContent.isNullOrBlank() -> "QR_CODE"
        request.photoBytes != null || !request.preUploadedObjectId.isNullOrBlank() -> "PHOTO"
        request.latitude != null && request.longitude != null -> "LOCATION"
        !request.signCode.isNullOrBlank() -> "CODE_OR_GESTURE"
        else -> "NORMAL"
    }

    private fun ChaoxingAccountEntity.toIdentity(): SignAccountIdentity = SignAccountIdentity(
        accountId = accountId,
        uid = uid,
        fid = fid,
        maskedAccount = maskedAccount,
        displayName = displayName,
    )

    private fun SignExecutionResult.toRecordSummary(): Pair<String, String> = when (this) {
        is SignExecutionResult.Success -> "SUCCESS" to message
        is SignExecutionResult.AlreadySigned -> "ALREADY_SIGNED" to message
        is SignExecutionResult.RequiresCaptcha -> "CAPTCHA_SUSPENDED" to message
        is SignExecutionResult.RequiresExtraInput -> "MISSING_INPUT" to message
        is SignExecutionResult.SessionExpired -> "SESSION_EXPIRED" to message
        is SignExecutionResult.QuotaRestricted -> "QUOTA_RESTRICTED" to message
        is SignExecutionResult.Rejected -> "REJECTED" to message
        is SignExecutionResult.Failed -> "FAILED" to error.message
    }
}
