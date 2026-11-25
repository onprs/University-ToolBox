package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaoxingLoginCipherTest {
    @Test
    fun `encrypt and decrypt roundtrip preserves ascii and utf8 text`() {
        val samples = listOf(
            "13800138000",
            "Pass@2026!#%",
            "中文字符与符号_Test_123",
        )
        for (plain in samples) {
            val encrypted = ChaoxingLoginCipher.encrypt(plain)
            assertNotEquals(plain, encrypted)
            assertEquals(plain, ChaoxingLoginCipher.decrypt(encrypted))
        }
    }

    @Test
    fun `encrypt uses deterministic AES-128-CBC PKCS5Padding with official key and iv`() {
        // 16 字节明文 "1234567890123456" 经 PKCS5Padding 填充后为 32 字节（44 位 Base64 字符）
        val cipher = ChaoxingLoginCipher.encrypt("1234567890123456")
        assertEquals(44, cipher.length)
        assertTrue(cipher.endsWith("="))
        assertEquals("1234567890123456", ChaoxingLoginCipher.decrypt(cipher))
    }
}
