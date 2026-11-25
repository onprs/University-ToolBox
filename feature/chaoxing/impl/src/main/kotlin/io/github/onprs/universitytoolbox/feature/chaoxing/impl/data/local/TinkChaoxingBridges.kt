package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.security.AeadCryptoEngine
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 `:core:security` Tink AEAD 引擎的账号凭据加解密桥接实现。
 */
@Singleton
class TinkChaoxingCredentialCipher @Inject constructor(private val cryptoEngine: AeadCryptoEngine) :
    ChaoxingCredentialCipher {
    override suspend fun encrypt(plainText: String, associatedData: String): String =
        cryptoEngine.encryptString(plainText, associatedData = "$CRED_AAD_PREFIX$associatedData")

    override suspend fun decrypt(cipherTextBase64: String, associatedData: String): String =
        cryptoEngine.decryptString(cipherTextBase64, associatedData = "$CRED_AAD_PREFIX$associatedData")

    companion object {
        private const val CRED_AAD_PREFIX = "chaoxing_cred:"
    }
}

/**
 * 基于 `:core:database` [ToolboxMetadataRepository] 的全局脱敏摘要审计桥接实现。
 */
@Singleton
class DatabaseChaoxingAuditSink @Inject constructor(private val metadataRepository: ToolboxMetadataRepository) :
    ChaoxingAuditSink {
    override suspend fun recordSummary(action: String, status: String, summary: String) {
        metadataRepository.logAuditEvent(
            category = AUDIT_CATEGORY,
            action = action,
            status = status,
            summary = summary,
            toolId = CHAOXING_TOOL_ID,
        )
    }

    companion object {
        private const val AUDIT_CATEGORY = "CHAOXING"
    }
}
