package io.github.onprs.universitytoolbox.core.license.stub

import io.github.onprs.universitytoolbox.core.license.api.LicenseStorage

/**
 * 调试桩内置的内存级持久化实现，用于在未注入持久化保险箱时完整模拟冷启动与状态持久化。
 */
internal class InMemoryLicenseStorage : LicenseStorage {
    private var activatedKey: String? = null
    private var highWaterMarkEpochSec: Long? = null
    private var revokedSerialIds: Set<String> = emptySet()

    override suspend fun loadActivatedKey(): String? = activatedKey

    override suspend fun saveActivatedKey(rawKey: String) {
        activatedKey = rawKey
    }

    override suspend fun clearActivatedKey() {
        activatedKey = null
    }

    override suspend fun loadClockHighWaterMarkEpochSec(): Long? = highWaterMarkEpochSec

    override suspend fun saveClockHighWaterMarkEpochSec(epochSec: Long) {
        highWaterMarkEpochSec = epochSec
    }

    override suspend fun loadRevokedSerialIds(): Set<String> = revokedSerialIds

    override suspend fun saveRevokedSerialIds(serialIds: Set<String>) {
        revokedSerialIds = serialIds.toSet()
    }
}
