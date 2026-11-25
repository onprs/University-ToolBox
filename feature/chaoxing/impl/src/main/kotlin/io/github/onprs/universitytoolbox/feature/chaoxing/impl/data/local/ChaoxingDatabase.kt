package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 学习通模块自治 Room 数据库，独立导出 Schema 至 `feature/chaoxing/impl/schemas`。
 */
@Database(
    entities = [
        ChaoxingAccountEntity::class,
        ChaoxingCourseEntity::class,
        ChaoxingSignRecordEntity::class,
        ChaoxingPresetLocationEntity::class,
        ChaoxingPreferenceEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ChaoxingDatabase : RoomDatabase() {
    abstract fun accountDao(): ChaoxingAccountDao

    abstract fun courseDao(): ChaoxingCourseDao

    abstract fun signRecordDao(): ChaoxingSignRecordDao

    abstract fun presetLocationDao(): ChaoxingPresetLocationDao

    abstract fun preferenceDao(): ChaoxingPreferenceDao

    companion object {
        const val DATABASE_NAME = "chaoxing_autonomous.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `chaoxing_accounts` ADD COLUMN `encryptedAccountInputCiphertext` TEXT",
                )
                db.execSQL(
                    "ALTER TABLE `chaoxing_accounts` ADD COLUMN `deviceCode` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE `chaoxing_accounts` ADD COLUMN `isUnifiedDeviceCode` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `chaoxing_accounts` ADD COLUMN `clientUniqueId` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE `chaoxing_accounts` ADD COLUMN `deviceModel` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chaoxing_preset_locations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `createdAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chaoxing_preferences` (
                        `key` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
