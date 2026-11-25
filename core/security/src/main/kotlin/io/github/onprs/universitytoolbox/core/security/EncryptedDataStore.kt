package io.github.onprs.universitytoolbox.core.security

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 透明加解密 DataStore 序列化器。
 * 读取时校验 AEAD 认证标签；密文篡改或解码失败时转换为 [CorruptionException] 交由损坏处理器安全恢复。
 */
class AeadDataStoreSerializer<T>(
    override val defaultValue: T,
    private val aeadCryptoEngine: AeadCryptoEngine,
    private val associatedData: ByteArray = ByteArray(0),
    private val encode: (T) -> ByteArray,
    private val decode: (ByteArray) -> T,
) : Serializer<T> {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun readFrom(input: InputStream): T {
        val cipherBytes = input.readBytes()
        if (cipherBytes.isEmpty()) {
            return defaultValue
        }
        return try {
            val plainBytes = aeadCryptoEngine.decrypt(cipherBytes, associatedData)
            decode(plainBytes)
        } catch (securityException: GeneralSecurityException) {
            throw CorruptionException("Encrypted DataStore authentication failed", securityException)
        } catch (exception: Exception) {
            throw CorruptionException("Encrypted DataStore payload could not be decoded", exception)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plainBytes = encode(t)
        val cipherBytes = aeadCryptoEngine.encrypt(plainBytes, associatedData)
        output.write(cipherBytes)
        output.flush()
    }
}

/**
 * 加密 DataStore 创建辅助工厂，默认将文件落盘至 `noBackupFilesDir` 避免云备份同步密文。
 */
object EncryptedDataStoreFactory {
    /**
     * 基于 Android 应用无备份目录创建加密 [DataStore]。
     */
    fun <T> create(
        context: Context,
        fileName: String,
        aeadCryptoEngine: AeadCryptoEngine,
        defaultValue: T,
        associatedData: ByteArray = fileName.toByteArray(Charsets.UTF_8),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        corruptionHandler: ReplaceFileCorruptionHandler<T>? = ReplaceFileCorruptionHandler { defaultValue },
        encode: (T) -> ByteArray,
        decode: (ByteArray) -> T,
    ): DataStore<T> {
        val targetDir = File(context.applicationContext.noBackupFilesDir, SecurityConstants.NO_BACKUP_STORE_SUBDIR)
        return createWithFile(
            produceFile = {
                targetDir.mkdirs()
                File(targetDir, fileName)
            },
            aeadCryptoEngine = aeadCryptoEngine,
            defaultValue = defaultValue,
            associatedData = associatedData,
            scope = scope,
            corruptionHandler = corruptionHandler,
            encode = encode,
            decode = decode,
        )
    }

    /**
     * 基于显式文件提供器创建加密 [DataStore]，便于 JVM 测试使用临时目录隔离验证。
     */
    fun <T> createWithFile(
        produceFile: () -> File,
        aeadCryptoEngine: AeadCryptoEngine,
        defaultValue: T,
        associatedData: ByteArray = ByteArray(0),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        corruptionHandler: ReplaceFileCorruptionHandler<T>? = ReplaceFileCorruptionHandler { defaultValue },
        encode: (T) -> ByteArray,
        decode: (ByteArray) -> T,
    ): DataStore<T> {
        val serializer = AeadDataStoreSerializer(
            defaultValue = defaultValue,
            aeadCryptoEngine = aeadCryptoEngine,
            associatedData = associatedData,
            encode = encode,
            decode = decode,
        )
        return DataStoreFactory.create(
            serializer = serializer,
            corruptionHandler = corruptionHandler,
            migrations = emptyList(),
            scope = scope,
            produceFile = produceFile,
        )
    }
}
