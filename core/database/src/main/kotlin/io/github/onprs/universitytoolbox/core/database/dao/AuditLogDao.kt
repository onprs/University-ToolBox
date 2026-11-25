package io.github.onprs.universitytoolbox.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.onprs.universitytoolbox.core.database.entity.AuditLogEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 脱敏审计日志数据访问接口。
 */
@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log_entries ORDER BY timestamp_epoch_ms DESC, id DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = DEFAULT_AUDIT_LIMIT): Flow<List<AuditLogEntryEntity>>

    @Query("SELECT * FROM audit_log_entries ORDER BY timestamp_epoch_ms DESC, id DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = DEFAULT_AUDIT_LIMIT): List<AuditLogEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntryEntity)

    @Query(
        """
        DELETE FROM audit_log_entries
        WHERE id NOT IN (
            SELECT id FROM audit_log_entries
            ORDER BY timestamp_epoch_ms DESC, id DESC
            LIMIT :maxEntries
        )
        """,
    )
    suspend fun trimKeepingLatest(maxEntries: Int)

    @Query("DELETE FROM audit_log_entries")
    suspend fun clearAll()

    companion object {
        const val DEFAULT_AUDIT_LIMIT = 200
    }
}
