package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 超星登录表单传输加密工具（AES-128-CBC + PKCS5Padding + Base64）。
 */
object ChaoxingLoginCipher {
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"
    private const val TRANSFER_KEY_UTF8 = "u2oh6Vu^HWe4_AES"

    private val keyBytes: ByteArray = TRANSFER_KEY_UTF8.toByteArray(StandardCharsets.UTF_8)
    private val keySpec = SecretKeySpec(keyBytes, ALGORITHM)
    private val ivSpec = IvParameterSpec(keyBytes)

    /** 使用超星官方传输密钥对明文账号或密码进行 AES-128-CBC 加密并输出标准 Base64。 */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /** 对 Base64 密文执行反向解密，供本地单元校验使用。 */
    fun decrypt(cipherTextBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decoded = Base64.getDecoder().decode(cipherTextBase64)
        return String(cipher.doFinal(decoded), StandardCharsets.UTF_8)
    }
}
