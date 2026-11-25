package io.github.onprs.universitytoolbox.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 工具最近使用记录表实体。
 */
@Entity(tableName = "recent_tool_usages")
data class RecentToolUsageEntity(
    @PrimaryKey
    @ColumnInfo(name = "tool_id")
    val toolId: String,
    @ColumnInfo(name = "last_used_at_epoch_ms")
    val lastUsedAtEpochMs: Long,
    @ColumnInfo(name = "use_count")
    val useCount: Int = 1,
)
