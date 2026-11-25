package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 学习通模块自治 Room 数据库，独立导出 Schema 至 `feature/chaoxing/impl/schemas`。
 */
@Database(
    entities = [
        ChaoxingAccountEntity::class,
        ChaoxingCourseEntity::class,
        ChaoxingSignRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ChaoxingDatabase : RoomDatabase() {
    abstract fun accountDao(): ChaoxingAccountDao

    abstract fun courseDao(): ChaoxingCourseDao

    abstract fun signRecordDao(): ChaoxingSignRecordDao

    companion object {
        const val DATABASE_NAME = "chaoxing_autonomous.db"
    }
}
