package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

/**
 * 账号凭据加密桥接契约：阶段 B 将由 `:core:security` Tink AEAD 引擎实现并注入。
 */
interface ChaoxingCredentialCipher {
    suspend fun encrypt(plainText: String, associatedData: String): String

    suspend fun decrypt(cipherTextBase64: String, associatedData: String): String
}

/**
 * 脱敏摘要审计写入桥接契约：阶段 B 将对接 `:core:database` 全局脱敏日志表。
 */
interface ChaoxingAuditSink {
    suspend fun recordSummary(action: String, status: String, summary: String)
}

/**
 * 默认空审计桥接，用于独立测试阶段或尚未绑定宿主审计仓的场景。
 */
object NoOpChaoxingAuditSink : ChaoxingAuditSink {
    override suspend fun recordSummary(action: String, status: String, summary: String) = Unit
}
