package io.github.onprs.universitytoolbox.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.core.common.dispatcher.ApplicationScope
import io.github.onprs.universitytoolbox.core.license.api.LicenseStorage
import io.github.onprs.universitytoolbox.core.security.AeadCryptoEngine
import io.github.onprs.universitytoolbox.core.security.EncryptedDataStoreFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 加密持久化的授权元数据快照（包含激活密钥密文载体、防回拨单调时钟水位与吊销序列号集合）。
 */
@Serializable
internal data class PersistedLicenseRecord(
    val activatedKey: String? = null,
    val clockHighWaterMarkEpochSec: Long? = null,
    val revokedSerialIds: Set<String> = emptySet(),
)

/**
 * 基于 `:core:security` AEAD 加密 DataStore 的 [LicenseStorage] 唯一正式实现。
 * 对所有读写串行加锁，并在底层密文被篡改导致认证失败时安全回落为空白锁定状态。
 */
@Singleton
class EncryptedLicenseStorage internal constructor(private val dataStore: DataStore<PersistedLicenseRecord>) :
    LicenseStorage {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        aeadCryptoEngine: AeadCryptoEngine,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : this(
        dataStore = EncryptedDataStoreFactory.create(
            context = context,
            fileName = LICENSE_STORE_FILE_NAME,
            aeadCryptoEngine = aeadCryptoEngine,
            defaultValue = PersistedLicenseRecord(),
            associatedData = LICENSE_STORE_AAD.toByteArray(Charsets.UTF_8),
            scope = applicationScope,
            corruptionHandler = ReplaceFileCorruptionHandler { PersistedLicenseRecord() },
            encode = { record -> storageJson.encodeToString(record).toByteArray(Charsets.UTF_8) },
            decode = { bytes -> storageJson.decodeFromString<PersistedLicenseRecord>(String(bytes, Charsets.UTF_8)) },
        ),
    )

    private val mutex = Mutex()

    override suspend fun loadActivatedKey(): String? = mutex.withLock {
        dataStore.data.first().activatedKey
    }

    override suspend fun saveActivatedKey(rawKey: String) {
        mutex.withLock {
            dataStore.updateData { current ->
                current.copy(activatedKey = rawKey)
            }
        }
    }

    override suspend fun clearActivatedKey() {
        mutex.withLock {
            dataStore.updateData { current ->
                current.copy(activatedKey = null)
            }
        }
    }

    override suspend fun loadClockHighWaterMarkEpochSec(): Long? = mutex.withLock {
        dataStore.data.first().clockHighWaterMarkEpochSec
    }

    override suspend fun saveClockHighWaterMarkEpochSec(epochSec: Long) {
        mutex.withLock {
            dataStore.updateData { current ->
                val updated = maxOf(current.clockHighWaterMarkEpochSec ?: epochSec, epochSec)
                current.copy(clockHighWaterMarkEpochSec = updated)
            }
        }
    }

    override suspend fun loadRevokedSerialIds(): Set<String> = mutex.withLock {
        dataStore.data.first().revokedSerialIds
    }

    override suspend fun saveRevokedSerialIds(serialIds: Set<String>) {
        mutex.withLock {
            dataStore.updateData { current ->
                current.copy(revokedSerialIds = serialIds.toSet())
            }
        }
    }

    companion object {
        internal const val LICENSE_STORE_FILE_NAME = "license_vault.enc"
        internal const val LICENSE_STORE_AAD = "university_toolbox_license_vault_v1"

        internal val storageJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * 构造使用显式文件路径的加密授权存储实例，专供 JVM 单元测试验证加解密与防篡改。
         */
        internal fun createForTest(
            produceFile: () -> File,
            aeadCryptoEngine: AeadCryptoEngine,
            scope: CoroutineScope,
        ): EncryptedLicenseStorage = EncryptedLicenseStorage(
            dataStore = EncryptedDataStoreFactory.createWithFile(
                produceFile = produceFile,
                aeadCryptoEngine = aeadCryptoEngine,
                defaultValue = PersistedLicenseRecord(),
                associatedData = LICENSE_STORE_AAD.toByteArray(Charsets.UTF_8),
                scope = scope,
                corruptionHandler = ReplaceFileCorruptionHandler { PersistedLicenseRecord() },
                encode = { record -> storageJson.encodeToString(record).toByteArray(Charsets.UTF_8) },
                decode = { bytes ->
                    storageJson.decodeFromString<PersistedLicenseRecord>(String(bytes, Charsets.UTF_8))
                },
            ),
        )
    }
}
