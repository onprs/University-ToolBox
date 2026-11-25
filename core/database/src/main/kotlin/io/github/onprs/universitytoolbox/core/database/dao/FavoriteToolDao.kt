package io.github.onprs.universitytoolbox.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.onprs.universitytoolbox.core.database.entity.FavoriteToolEntity
import kotlinx.coroutines.flow.Flow

/**
 * 收藏工具数据访问接口。
 */
@Dao
interface FavoriteToolDao {
    @Query("SELECT tool_id FROM favorite_tools ORDER BY sort_priority ASC, pinned_at_epoch_ms ASC")
    fun observeFavoriteToolIds(): Flow<List<String>>

    @Query("SELECT * FROM favorite_tools ORDER BY sort_priority ASC, pinned_at_epoch_ms ASC")
    fun observeFavorites(): Flow<List<FavoriteToolEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tools WHERE tool_id = :toolId)")
    suspend fun isFavorite(toolId: String): Boolean

    @Upsert
    suspend fun upsert(entity: FavoriteToolEntity)

    @Query("DELETE FROM favorite_tools WHERE tool_id = :toolId")
    suspend fun deleteByToolId(toolId: String)

    @Query("DELETE FROM favorite_tools")
    suspend fun clearAll()
}
