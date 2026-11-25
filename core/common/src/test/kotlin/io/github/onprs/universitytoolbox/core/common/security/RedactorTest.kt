package io.github.onprs.universitytoolbox.core.common.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @Test
    fun `maskPhone keeps prefix and suffix only`() {
        assertEquals("138****0000", Redactor.maskPhone("13800130000"))
        assertEquals("****", Redactor.maskPhone("1234"))
        assertEquals("", Redactor.maskPhone("  "))
    }

    @Test
    fun `maskSecret hides middle segment`() {
        assertEquals("ABCD********WXYZ", Redactor.maskSecret("ABCD12345678WXYZ"))
        assertEquals("******", Redactor.maskSecret("SECRET"))
    }

    @Test
    fun `redactText strips sensitive headers cookies passwords and phones`() {
        val raw = """
            Cookie: UID=998877; _d=secret_d; vc3=secret_vc3
            Authorization: Bearer super_secret_token
            {"uname":"13912345678","password":"plainPassword123"}
            phone=13912345678&token=abc123
        """.trimIndent()

        val redacted = Redactor.redactText(raw)

        assertFalse(redacted.contains("secret_d"))
        assertFalse(redacted.contains("secret_vc3"))
        assertFalse(redacted.contains("super_secret_token"))
        assertFalse(redacted.contains("plainPassword123"))
        assertFalse(redacted.contains("13912345678"))
        assertFalse(redacted.contains("abc123"))
        assertEquals(true, redacted.contains("139****5678"))
    }
}
