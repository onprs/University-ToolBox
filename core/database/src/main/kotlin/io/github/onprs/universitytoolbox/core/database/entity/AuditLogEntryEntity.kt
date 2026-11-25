package io.github.onprs.universitytoolbox.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 全局脱敏审计日志摘要表实体，仅允许持久化经脱敏处理的摘要信息。
 */
@Entity(
    tableName = "audit_log_entries",
    indices = [Index(value = ["timestamp_epoch_ms"])],
)
data class AuditLogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "timestamp_epoch_ms")
    val timestampEpochMs: Long,
    val category: String,
    @ColumnInfo(name = "tool_id")
    val toolId: String? = null,
    val action: String,
    val status: String,
    val summary: String,
)
