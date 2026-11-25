package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChaoxingAccountDao {
    @Query("SELECT * FROM chaoxing_accounts ORDER BY isPrimary DESC, createdAtMs ASC")
    fun observeAll(): Flow<List<ChaoxingAccountEntity>>

    @Query("SELECT * FROM chaoxing_accounts ORDER BY isPrimary DESC, createdAtMs ASC")
    suspend fun getAll(): List<ChaoxingAccountEntity>

    @Query("SELECT * FROM chaoxing_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getById(accountId: String): ChaoxingAccountEntity?

    @Query("SELECT * FROM chaoxing_accounts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryAccount(): ChaoxingAccountEntity?

    @Query("SELECT COUNT(*) FROM chaoxing_accounts WHERE isPrimary = 0")
    suspend fun countSubAccounts(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChaoxingAccountEntity)

    @Query(
        "UPDATE chaoxing_accounts SET sessionStatus = :sessionStatus, " +
            "lastVerifiedAtMs = :verifiedAtMs WHERE accountId = :accountId",
    )
    suspend fun updateSessionStatus(accountId: String, sessionStatus: String, verifiedAtMs: Long)

    @Query("UPDATE chaoxing_accounts SET isEnabled = :isEnabled WHERE accountId = :accountId")
    suspend fun updateEnabled(accountId: String, isEnabled: Boolean)

    @Query("UPDATE chaoxing_accounts SET isPrimary = CASE WHEN accountId = :accountId THEN 1 ELSE 0 END")
    suspend fun markPrimary(accountId: String)

    @Query("DELETE FROM chaoxing_accounts WHERE accountId = :accountId")
    suspend fun deleteById(accountId: String)

    @Query(
        "UPDATE chaoxing_accounts SET deviceCode = :deviceCode, " +
            "isUnifiedDeviceCode = :isUnified, clientUniqueId = :uniqueId, " +
            "deviceModel = :deviceModel WHERE accountId = :accountId",
    )
    suspend fun updateDeviceIdentity(
        accountId: String,
        deviceCode: String,
        isUnified: Boolean,
        uniqueId: String,
        deviceModel: String,
    )
}

@Dao
interface ChaoxingCourseDao {
    @Query(
        "SELECT * FROM chaoxing_courses WHERE accountId = :accountId " +
            "ORDER BY isPinned DESC, isArchived ASC, courseId DESC",
    )
    fun observeByAccount(accountId: String): Flow<List<ChaoxingCourseEntity>>

    @Query("SELECT * FROM chaoxing_courses WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: String): List<ChaoxingCourseEntity>

    @Query("DELETE FROM chaoxing_courses WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<ChaoxingCourseEntity>)

    @Transaction
    suspend fun replaceForAccount(accountId: String, courses: List<ChaoxingCourseEntity>) {
        deleteByAccount(accountId)
        if (courses.isNotEmpty()) {
            insertAll(courses)
        }
    }
}

@Dao
interface ChaoxingSignRecordDao {
    @Query("SELECT * FROM chaoxing_sign_records ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = DEFAULT_RECORD_LIMIT): Flow<List<ChaoxingSignRecordEntity>>

    @Query(
        "SELECT * FROM chaoxing_sign_records WHERE accountId = :accountId " +
            "ORDER BY timestampMs DESC LIMIT :limit",
    )
    fun observeByAccount(accountId: String, limit: Int = DEFAULT_RECORD_LIMIT): Flow<List<ChaoxingSignRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ChaoxingSignRecordEntity): Long

    @Query("DELETE FROM chaoxing_sign_records")
    suspend fun clearAll()

    companion object {
        const val DEFAULT_RECORD_LIMIT = 100
    }
}

@Dao
interface ChaoxingPresetLocationDao {
    @Query("SELECT * FROM chaoxing_preset_locations ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<ChaoxingPresetLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChaoxingPresetLocationEntity): Long

    @Query("DELETE FROM chaoxing_preset_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChaoxingPreferenceDao {
    @Query("SELECT value FROM chaoxing_preferences WHERE `key` = :key LIMIT 1")
    fun observeValue(key: String): Flow<String?>

    @Query("SELECT value FROM chaoxing_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChaoxingPreferenceEntity)
}
