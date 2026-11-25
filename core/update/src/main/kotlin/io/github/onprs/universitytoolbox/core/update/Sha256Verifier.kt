package io.github.onprs.universitytoolbox.core.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 流式 SHA-256 摘要计算与安全比对工具。
 */
object Sha256Verifier {
    private const val STREAM_BUFFER_SIZE = 8192
    private const val BYTE_MASK = 0xFF
    private const val SHA256_HEX_LENGTH = 64
    private val hexRegex = Regex("^[0-9a-f]{$SHA256_HEX_LENGTH}$")

    /** 校验传入的十六进制字符串是否为规范的 64 位 SHA-256 摘要。 */
    fun isValidSha256Hex(candidate: String): Boolean = hexRegex.matches(candidate.trim().lowercase())

    /** 流式读取文件并计算小写十六进制 SHA-256 摘要。 */
    fun computeSha256Hex(file: File): String = file.inputStream().use { input ->
        computeSha256Hex(input)
    }

    /** 流式读取输入流并计算小写十六进制 SHA-256 摘要。 */
    fun computeSha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
        return toLowerHex(digest.digest())
    }

    /** 计算字节数组的小写十六进制 SHA-256 摘要。 */
    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return toLowerHex(digest)
    }

    /** 使用常量时间比较判断期望与实际 SHA-256 摘要是否一致。 */
    fun matchesSha256(expectedHex: String, actualHex: String): Boolean {
        val normalizedExpected = expectedHex.trim().lowercase()
        val normalizedActual = actualHex.trim().lowercase()
        if (!isValidSha256Hex(normalizedExpected) || !isValidSha256Hex(normalizedActual)) {
            return false
        }
        return MessageDigest.isEqual(
            normalizedExpected.toByteArray(Charsets.US_ASCII),
            normalizedActual.toByteArray(Charsets.US_ASCII),
        )
    }

    private fun toLowerHex(bytes: ByteArray): String = bytes.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and BYTE_MASK)
    }
}
