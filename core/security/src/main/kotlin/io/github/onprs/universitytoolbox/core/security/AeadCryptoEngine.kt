package io.github.onprs.universitytoolbox.core.security

import java.util.Base64

/**
 * 对称认证加密（AEAD）引擎契约，统一屏蔽底层密钥托管实现以便业务模块与 JVM 单元测试复用。
 */
interface AeadCryptoEngine {
    /** 使用可选关联数据对明文字节数组进行认证加密。 */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray

    /** 校验关联数据与认证标签并解密密文字节数组。 */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray

    /** 将字符串明文加密为 Base64 编码密文。 */
    fun encryptString(plaintext: String, associatedData: String = ""): String {
        val cipherBytes = encrypt(
            plaintext = plaintext.toByteArray(Charsets.UTF_8),
            associatedData = associatedData.toByteArray(Charsets.UTF_8),
        )
        return Base64.getEncoder().encodeToString(cipherBytes)
    }

    /** 解码并验证 Base64 密文后还原为 UTF-8 明文字符串。 */
    fun decryptString(encodedCiphertext: String, associatedData: String = ""): String {
        val cipherBytes = Base64.getDecoder().decode(encodedCiphertext)
        val plainBytes = decrypt(
            ciphertext = cipherBytes,
            associatedData = associatedData.toByteArray(Charsets.UTF_8),
        )
        return String(plainBytes, Charsets.UTF_8)
    }
}
