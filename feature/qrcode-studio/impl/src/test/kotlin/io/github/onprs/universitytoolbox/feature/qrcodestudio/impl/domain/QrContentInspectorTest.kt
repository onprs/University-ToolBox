package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrContentInspectorTest {
    @Test
    fun `inspects https url with query parameters and flags sensitive params`() {
        val result = QrContentInspector.inspect(
            rawContent = "https://portal.university.edu/auth/callback?course=101&token=secret123",
            formatLabel = "QR_CODE",
        )

        assertEquals(QrPayloadCategory.URL, result.category)
        assertEquals("portal.university.edu", result.summaryTitle)
        assertTrue(SecurityNoticeKind.SAFE_HTTPS in result.securityNotices)
        assertTrue(SecurityNoticeKind.SENSITIVE_QUERY_PARAM in result.securityNotices)
        val tokenField = result.fields.first { it.customLabel == "token" }
        assertTrue(tokenField.isSensitive)
        assertEquals("secret123", tokenField.value)
        val courseField = result.fields.first { it.customLabel == "course" }
        assertFalse(courseField.isSensitive)
    }

    @Test
    fun `inspects insecure http ip link and flags risks`() {
        val result = QrContentInspector.inspect(
            rawContent = "http://192.168.1.25:8080/login",
            formatLabel = "QR_CODE",
        )

        assertEquals(QrPayloadCategory.URL, result.category)
        assertTrue(SecurityNoticeKind.INSECURE_HTTP in result.securityNotices)
        assertTrue(SecurityNoticeKind.IP_HOST_DETECTED in result.securityNotices)
        assertEquals("8080", result.fields.first { it.key == QrFieldKey.PORT }.value)
    }

    @Test
    fun `inspects wifi payload with escaped semicolons and marks password sensitive`() {
        val result = QrContentInspector.inspect(
            rawContent = """WIFI:T:WPA;S:Campus\;Lab;P:Pass\:1234;H:true;;""",
            formatLabel = "QR_CODE",
        )

        assertEquals(QrPayloadCategory.WIFI, result.category)
        assertEquals("Campus;Lab", result.fields.first { it.key == QrFieldKey.WIFI_SSID }.value)
        assertEquals("WPA", result.fields.first { it.key == QrFieldKey.WIFI_ENCRYPTION }.value)
        val passField = result.fields.first { it.key == QrFieldKey.WIFI_PASSWORD }
        assertEquals("Pass:1234", passField.value)
        assertTrue(passField.isSensitive)
        assertEquals("true", result.fields.first { it.key == QrFieldKey.WIFI_HIDDEN }.value)
        assertTrue(SecurityNoticeKind.CREDENTIAL_PROTECTED in result.securityNotices)
    }

    @Test
    fun `inspects campus sign token and chaoxing sign url`() {
        val tokenResult = QrContentInspector.inspect("SIGNIN:aid=9988&enc=abc123")
        assertEquals(QrPayloadCategory.CAMPUS_SIGN, tokenResult.category)
        assertTrue(SecurityNoticeKind.SENSITIVE_QUERY_PARAM in tokenResult.securityNotices)

        val urlResult = QrContentInspector.inspect(
            "https://mobilelearn.chaoxing.com/widget/sign/e?id=12345&enc=xyz789",
        )
        assertEquals(QrPayloadCategory.CAMPUS_SIGN, urlResult.category)
    }

    @Test
    fun `inspects json geo contact email phone and plain barcode`() {
        val jsonResult = QrContentInspector.inspect("""{"course":"CS101","credit":3}""")
        assertEquals(QrPayloadCategory.JSON, jsonResult.category)
        assertEquals("course, credit", jsonResult.fields.first { it.key == QrFieldKey.JSON_KEYS }.value)

        val geoResult = QrContentInspector.inspect("geo:39.9869,116.3058?q=library")
        assertEquals(QrPayloadCategory.GEO, geoResult.category)
        assertEquals("39.9869", geoResult.fields.first { it.key == QrFieldKey.LATITUDE }.value)

        val vcardResult = QrContentInspector.inspect(
            "BEGIN:VCARD\nFN:Zhang San\nTEL:010-5550-0199\nEMAIL:user@campus.edu\nEND:VCARD",
        )
        assertEquals(QrPayloadCategory.CONTACT, vcardResult.category)
        assertEquals("Zhang San", vcardResult.fields.first { it.key == QrFieldKey.CONTACT_NAME }.value)
        assertEquals("010-5550-0199", vcardResult.fields.first { it.key == QrFieldKey.CONTACT_PHONE }.value)

        val eanResult = QrContentInspector.inspect("9787111544937", formatLabel = "EAN_13")
        assertEquals(QrPayloadCategory.PLAIN_TEXT, eanResult.category)
        assertEquals("EAN_13", eanResult.fields.first { it.key == QrFieldKey.FORMAT }.value)

        val emptyResult = QrContentInspector.inspect("   ")
        assertTrue(SecurityNoticeKind.RAW_PAYLOAD_EMPTY in emptyResult.securityNotices)
    }
}
