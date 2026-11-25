package io.github.onprs.universitytoolbox.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 置顶收藏工具表实体。
 */
@Entity(tableName = "favorite_tools")
data class FavoriteToolEntity(
    @PrimaryKey
    @ColumnInfo(name = "tool_id")
    val toolId: String,
    @ColumnInfo(name = "pinned_at_epoch_ms")
    val pinnedAtEpochMs: Long,
    @ColumnInfo(name = "sort_priority")
    val sortPriority: Int = 0,
)
