package io.github.onprs.universitytoolbox.core.database

import io.github.onprs.universitytoolbox.core.common.dispatcher.IoDispatcher
import io.github.onprs.universitytoolbox.core.common.security.Redactor
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.database.dao.AuditLogDao
import io.github.onprs.universitytoolbox.core.database.dao.FavoriteToolDao
import io.github.onprs.universitytoolbox.core.database.dao.RecentToolDao
import io.github.onprs.universitytoolbox.core.database.entity.AuditLogEntryEntity
import io.github.onprs.universitytoolbox.core.database.entity.FavoriteToolEntity
import io.github.onprs.universitytoolbox.core.database.entity.RecentToolUsageEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 宿主元数据仓储契约：统一管理收藏工具、最近使用记录以及全局脱敏审计日志。
 */
interface ToolboxMetadataRepository {
    fun observeFavoriteToolIds(): Flow<Set<String>>

    fun observeFavoriteTools(): Flow<List<FavoriteToolEntity>>

    suspend fun toggleFavorite(toolId: String): Boolean

    suspend fun setFavorite(toolId: String, isFavorite: Boolean)

    fun observeRecentToolUsages(limit: Int = RecentToolDao.DEFAULT_RECENT_LIMIT): Flow<List<RecentToolUsageEntity>>

    suspend fun recordToolUsage(toolId: String)

    fun observeAuditLogs(limit: Int = AuditLogDao.DEFAULT_AUDIT_LIMIT): Flow<List<AuditLogEntryEntity>>

    suspend fun logAuditEvent(
        category: String,
        action: String,
        status: String,
        summary: String,
        toolId: String? = null,
    )

    suspend fun exportRedactedAuditText(limit: Int = AuditLogDao.DEFAULT_AUDIT_LIMIT): String

    suspend fun clearRecentUsages()

    suspend fun clearAuditLogs()

    suspend fun clearAllHistory()
}

/**
 * 基于 Room 的 [ToolboxMetadataRepository] 默认实现。
 */
@Singleton
class RoomToolboxMetadataRepository @Inject constructor(
    private val favoriteToolDao: FavoriteToolDao,
    private val recentToolDao: RecentToolDao,
    private val auditLogDao: AuditLogDao,
    private val timeProvider: TimeProvider,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ToolboxMetadataRepository {
    override fun observeFavoriteToolIds(): Flow<Set<String>> =
        favoriteToolDao.observeFavoriteToolIds().map { ids -> LinkedHashSet(ids) }

    override fun observeFavoriteTools(): Flow<List<FavoriteToolEntity>> = favoriteToolDao.observeFavorites()

    override suspend fun toggleFavorite(toolId: String): Boolean = withContext(ioDispatcher) {
        val currentlyFavorite = favoriteToolDao.isFavorite(toolId)
        if (currentlyFavorite) {
            favoriteToolDao.deleteByToolId(toolId)
            false
        } else {
            favoriteToolDao.upsert(
                FavoriteToolEntity(
                    toolId = toolId,
                    pinnedAtEpochMs = timeProvider.currentTimeMillis(),
                ),
            )
            true
        }
    }

    override suspend fun setFavorite(toolId: String, isFavorite: Boolean) {
        withContext(ioDispatcher) {
            if (isFavorite) {
                if (!favoriteToolDao.isFavorite(toolId)) {
                    favoriteToolDao.upsert(
                        FavoriteToolEntity(
                            toolId = toolId,
                            pinnedAtEpochMs = timeProvider.currentTimeMillis(),
                        ),
                    )
                }
            } else {
                favoriteToolDao.deleteByToolId(toolId)
            }
        }
    }

    override fun observeRecentToolUsages(limit: Int): Flow<List<RecentToolUsageEntity>> =
        recentToolDao.observeRecentUsages(limit.coerceAtLeast(1))

    override suspend fun recordToolUsage(toolId: String) {
        withContext(ioDispatcher) {
            val existing = recentToolDao.findByToolId(toolId)
            val nowMs = timeProvider.currentTimeMillis()
            val nextCount = (existing?.useCount ?: 0) + 1
            recentToolDao.upsert(
                RecentToolUsageEntity(
                    toolId = toolId,
                    lastUsedAtEpochMs = nowMs,
                    useCount = nextCount,
                ),
            )
        }
    }

    override fun observeAuditLogs(limit: Int): Flow<List<AuditLogEntryEntity>> =
        auditLogDao.observeRecentLogs(limit.coerceAtLeast(1))

    override suspend fun logAuditEvent(
        category: String,
        action: String,
        status: String,
        summary: String,
        toolId: String?,
    ) {
        withContext(ioDispatcher) {
            val sanitizedSummary = Redactor.redactText(summary.trim())
                .take(MAX_SUMMARY_LENGTH)
            val sanitizedAction = Redactor.redactText(action.trim())
            val entry = AuditLogEntryEntity(
                timestampEpochMs = timeProvider.currentTimeMillis(),
                category = category.trim().uppercase(),
                toolId = toolId?.trim()?.takeIf { it.isNotEmpty() },
                action = sanitizedAction,
                status = status.trim().uppercase(),
                summary = sanitizedSummary,
            )
            auditLogDao.insert(entry)
            auditLogDao.trimKeepingLatest(MAX_RETAINED_AUDIT_LOGS)
        }
    }

    override suspend fun exportRedactedAuditText(limit: Int): String = withContext(ioDispatcher) {
        val entries = auditLogDao.getRecentLogs(limit.coerceAtLeast(1))
        if (entries.isEmpty()) {
            return@withContext ""
        }
        val formatted = entries.joinToString(separator = "\n") { entry ->
            val timestamp = ISO_FORMATTER.format(Instant.ofEpochMilli(entry.timestampEpochMs))
            val toolPart = entry.toolId?.let { " [$it]" }.orEmpty()
            "$timestamp | ${entry.category}$toolPart | ${entry.status} | ${entry.action} | ${entry.summary}"
        }
        Redactor.redactText(formatted)
    }

    override suspend fun clearRecentUsages() {
        withContext(ioDispatcher) {
            recentToolDao.clearAll()
        }
    }

    override suspend fun clearAuditLogs() {
        withContext(ioDispatcher) {
            auditLogDao.clearAll()
        }
    }

    override suspend fun clearAllHistory() {
        withContext(ioDispatcher) {
            recentToolDao.clearAll()
            auditLogDao.clearAll()
        }
    }

    companion object {
        private const val MAX_RETAINED_AUDIT_LOGS = 500
        private const val MAX_SUMMARY_LENGTH = 512
        private val ISO_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)
    }
}
