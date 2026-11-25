package io.github.onprs.universitytoolbox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.onprs.universitytoolbox.core.database.dao.AuditLogDao
import io.github.onprs.universitytoolbox.core.database.dao.FavoriteToolDao
import io.github.onprs.universitytoolbox.core.database.dao.RecentToolDao
import io.github.onprs.universitytoolbox.core.database.entity.AuditLogEntryEntity
import io.github.onprs.universitytoolbox.core.database.entity.FavoriteToolEntity
import io.github.onprs.universitytoolbox.core.database.entity.RecentToolUsageEntity

/**
 * 宿主元数据 Room 数据库，仅持久化工具收藏、最近使用时间戳及脱敏审计摘要。
 */
@Database(
    entities = [
        FavoriteToolEntity::class,
        RecentToolUsageEntity::class,
        AuditLogEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ToolboxDatabase : RoomDatabase() {
    abstract fun favoriteToolDao(): FavoriteToolDao

    abstract fun recentToolDao(): RecentToolDao

    abstract fun auditLogDao(): AuditLogDao
}
