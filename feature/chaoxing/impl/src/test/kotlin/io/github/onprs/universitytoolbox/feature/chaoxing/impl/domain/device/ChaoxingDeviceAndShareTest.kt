package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.device

import android.graphics.BitmapFactory
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingLoginCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChaoxingDeviceAndShareTest {
    @Test
    fun `deterministic and random device codes follow chaoxing protocol format`() {
        val codeA = ChaoxingDeviceIdentityHelper.deriveDeterministicDeviceCode("a1b2c3d4e5f60718")
        val codeB = ChaoxingDeviceIdentityHelper.deriveDeterministicDeviceCode("a1b2c3d4e5f60718")
        val codeOther = ChaoxingDeviceIdentityHelper.deriveDeterministicDeviceCode("ffeeddccbbaa9988")

        assertTrue(codeA.isNotBlank())
        assertEquals(codeA, codeB)
        assertNotEquals(codeA, codeOther)

        val random1 = ChaoxingDeviceIdentityHelper.generateRandomDeviceCode()
        val random2 = ChaoxingDeviceIdentityHelper.generateRandomDeviceCode()
        assertEquals(EXPECTED_RANDOM_CODE_LENGTH, random1.length)
        assertNotEquals(random1, random2)
    }

    @Test
    fun `client user agent embeds and extracts device model and unique id`() {
        val model = "24031PN0DC"
        val uniqueId = "9f8e7d6c5b4a3210fedcba9876543210"
        val ua = ChaoxingDeviceIdentityHelper.buildClientUserAgent(model, uniqueId)

        assertTrue(ua.contains("(schild:"))
        assertTrue(ua.contains("com.chaoxing.mobile"))

        val (parsedModel, parsedUid) = ChaoxingDeviceIdentityHelper.parseUserAgentIdentity(ua)
        assertEquals(model, parsedModel)
        assertEquals(uniqueId, parsedUid)

        val dalvikOnlyUa = "Dalvik/2.1.0 (Linux; U; Android 16; PKG110 Build/AP3A.240905) " +
            "com.chaoxing.mobile/ChaoXingStudy_3_6.8.1_android_phone (@Kalimdor)_abcdef0123456789"
        val (fallbackModel, fallbackUid) = ChaoxingDeviceIdentityHelper.parseUserAgentIdentity(dalvikOnlyUa)
        assertEquals("PKG110", fallbackModel)
        assertEquals("abcdef0123456789", fallbackUid)
    }

    @Test
    fun `share url codec encodes decodes and generates valid qr png`() {
        val encryptedPwd = ChaoxingLoginCipher.encrypt("Pass+Word/123=")
        val original = ChaoxingSharedAccountPayload(
            accountInput = "13800138999",
            encryptedPassword = encryptedPwd,
            displayName = "李明",
            fid = "1086",
            deviceCode = "AbCdEf+GhIjKl/MnOpQrStUvWxYz==",
            uniqueId = "uid_sample_001",
            deviceModel = "ALN-AL00",
        )

        val encodedUrl = ChaoxingSharedAccountCodec.encodeShareUrl(original)
        assertNull(ChaoxingSharedAccountCodec.parseShareText("https://example.com/other"))

        // 支持直接解析深链及从带前后说明文字的剪贴板消息中提取
        val wrappedClipboardText = "请点击导入代签绑定链接：$encodedUrl 谢谢"
        val decoded = ChaoxingSharedAccountCodec.parseShareText(wrappedClipboardText)
        assertNotNull(decoded)
        assertEquals(original, decoded)
        assertEquals("Pass+Word/123=", ChaoxingLoginCipher.decrypt(decoded!!.encryptedPassword))

        val qrBytes = ChaoxingSharedAccountCodec.generateQrPngBytes(encodedUrl, sizePx = TEST_QR_SIZE_PX)
        assertTrue(qrBytes.isNotEmpty())
        val bitmap = BitmapFactory.decodeByteArray(qrBytes, 0, qrBytes.size)
        assertNotNull(bitmap)
        assertEquals(TEST_QR_SIZE_PX, bitmap.width)
        assertEquals(TEST_QR_SIZE_PX, bitmap.height)
    }

    companion object {
        private const val EXPECTED_RANDOM_CODE_LENGTH = 88
        private const val TEST_QR_SIZE_PX = 256
    }
}
