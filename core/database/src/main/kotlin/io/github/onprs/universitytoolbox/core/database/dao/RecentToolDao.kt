package io.github.onprs.universitytoolbox.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.onprs.universitytoolbox.core.database.entity.RecentToolUsageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 最近使用工具记录数据访问接口。
 */
@Dao
interface RecentToolDao {
    @Query("SELECT * FROM recent_tool_usages ORDER BY last_used_at_epoch_ms DESC LIMIT :limit")
    fun observeRecentUsages(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<RecentToolUsageEntity>>

    @Query("SELECT * FROM recent_tool_usages WHERE tool_id = :toolId LIMIT 1")
    suspend fun findByToolId(toolId: String): RecentToolUsageEntity?

    @Upsert
    suspend fun upsert(entity: RecentToolUsageEntity)

    @Query("DELETE FROM recent_tool_usages WHERE tool_id = :toolId")
    suspend fun deleteByToolId(toolId: String)

    @Query("DELETE FROM recent_tool_usages")
    suspend fun clearAll()

    companion object {
        const val DEFAULT_RECENT_LIMIT = 10
    }
}
