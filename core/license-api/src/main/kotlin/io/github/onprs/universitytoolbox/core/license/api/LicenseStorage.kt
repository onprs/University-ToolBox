package io.github.onprs.universitytoolbox.core.license.api

/**
 * 授权持久化和防回拨时钟水位存储契约。
 * 由公开仓安全存储模块实现并注入至密钥引擎，避免底层引擎直接耦合 Android DataStore/Tink。
 */
interface LicenseStorage {
    suspend fun loadActivatedKey(): String?

    suspend fun saveActivatedKey(rawKey: String)

    suspend fun clearActivatedKey()

    suspend fun loadClockHighWaterMarkEpochSec(): Long?

    suspend fun saveClockHighWaterMarkEpochSec(epochSec: Long)

    suspend fun loadRevokedSerialIds(): Set<String>

    suspend fun saveRevokedSerialIds(serialIds: Set<String>)
}

/**
 * 空操作内存占位存储，适用于无持久化需求的最小测试环境。
 */
object NoOpLicenseStorage : LicenseStorage {
    override suspend fun loadActivatedKey(): String? = null

    override suspend fun saveActivatedKey(rawKey: String) = Unit

    override suspend fun clearActivatedKey() = Unit

    override suspend fun loadClockHighWaterMarkEpochSec(): Long? = null

    override suspend fun saveClockHighWaterMarkEpochSec(epochSec: Long) = Unit

    override suspend fun loadRevokedSerialIds(): Set<String> = emptySet()

    override suspend fun saveRevokedSerialIds(serialIds: Set<String>) = Unit
}
