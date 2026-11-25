package io.github.onprs.universitytoolbox.feature.unlock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockDeepLinkParserTest {
    @Test
    fun `extracts activation code from custom deep link and query variants`() {
        assertEquals(
            "SAMPLE-KEY-ALPHA-001",
            UnlockDeepLinkParser.extractActivationCode("universitytoolbox://unlock?key=SAMPLE-KEY-ALPHA-001"),
        )
        assertEquals(
            "SAMPLE-KEY-BETA-002",
            UnlockDeepLinkParser.extractActivationCode(
                "universitytoolbox://activate?source=mail&code=SAMPLE-KEY-BETA-002",
            ),
        )
        assertEquals(
            "SAMPLE-KEY-GAMMA-003",
            UnlockDeepLinkParser.extractActivationCode(
                "https://portal.example.org/unlock?token=SAMPLE-KEY-GAMMA-003",
            ),
        )
        assertEquals(
            "SAMPLE-KEY-PATH-004",
            UnlockDeepLinkParser.extractActivationCode("universitytoolbox://unlock/SAMPLE-KEY-PATH-004"),
        )
    }

    @Test
    fun `normalizes whitespace in direct activation strings and ignores empty inputs`() {
        assertEquals(
            "ABC-123-XYZ",
            UnlockDeepLinkParser.extractActivationCode("  ABC-\n  123-\tXYZ  "),
        )
        assertNull(UnlockDeepLinkParser.extractActivationCode("   "))
        assertNull(UnlockDeepLinkParser.extractActivationCode(null))
        assertNull(UnlockDeepLinkParser.extractActivationCode("universitytoolbox://unlock"))
    }

    @Test
    fun `detects valid activation deep links accurately`() {
        assertTrue(UnlockDeepLinkParser.isActivationDeepLink("universitytoolbox://unlock?key=TEST"))
        assertTrue(UnlockDeepLinkParser.isActivationDeepLink("https://host.org/activate?code=TEST"))
        assertFalse(UnlockDeepLinkParser.isActivationDeepLink("PLAIN-TEXT-KEY"))
        assertFalse(UnlockDeepLinkParser.isActivationDeepLink("https://host.org/about"))
    }
}
