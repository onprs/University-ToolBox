package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.onprs.universitytoolbox.core.common.dispatcher.IoDispatcher
import io.github.onprs.universitytoolbox.core.security.AeadCryptoEngine
import java.io.File
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 基于 `:core:security` [AeadCryptoEngine] 的分账号隔离 Cookie 加密持久化存储。
 * - 存储目录位于 `noBackupFilesDir`，避免云备份同步敏感会话凭证；
 * - 每个账号独立加密文件并使用 `chaoxing_cookie:<accountId>` 作为 AEAD 关联数据防串号篡改。
 */
@Singleton
class EncryptedAccountCookieStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cryptoEngine: AeadCryptoEngine,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccountCookieStorage {
    private val vaultDir: File by lazy {
        File(context.noBackupFilesDir, VAULT_DIR_NAME).apply { mkdirs() }
    }

    override suspend fun readSerializedCookies(accountId: String): String? = withContext(ioDispatcher) {
        val targetFile = fileFor(accountId)
        if (!targetFile.exists()) {
            return@withContext null
        }
        val encrypted = runCatching { targetFile.readText(Charsets.UTF_8) }.getOrNull()
        if (encrypted.isNullOrBlank()) {
            return@withContext null
        }
        try {
            cryptoEngine.decryptString(encrypted, associatedData = associatedDataFor(accountId))
        } catch (_: GeneralSecurityException) {
            targetFile.delete()
            null
        } catch (_: IllegalArgumentException) {
            targetFile.delete()
            null
        }
    }

    override suspend fun writeSerializedCookies(accountId: String, payload: String) {
        withContext(ioDispatcher) {
            vaultDir.mkdirs()
            val ciphertext = cryptoEngine.encryptString(payload, associatedData = associatedDataFor(accountId))
            val targetFile = fileFor(accountId)
            val tempFile = File(vaultDir, "${targetFile.name}.tmp")
            tempFile.writeText(ciphertext, Charsets.UTF_8)
            if (!tempFile.renameTo(targetFile)) {
                targetFile.writeText(ciphertext, Charsets.UTF_8)
                tempFile.delete()
            }
        }
    }

    override suspend fun deleteCookies(accountId: String) {
        withContext(ioDispatcher) {
            val targetFile = fileFor(accountId)
            if (targetFile.exists()) {
                targetFile.delete()
            }
        }
    }

    private fun fileFor(accountId: String): File = File(vaultDir, "${sha256Hex(accountId)}.enc")

    private fun associatedDataFor(accountId: String): String = "$ASSOCIATED_DATA_PREFIX$accountId"

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val VAULT_DIR_NAME = "chaoxing_cookie_vault"
        private const val ASSOCIATED_DATA_PREFIX = "chaoxing_cookie:"
    }
}
