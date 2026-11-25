package io.github.onprs.universitytoolbox.feature.qrcodestudio.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeStudioNavKeyTest {
    @Test
    fun `qrcode studio nav key implements NavKey and serializes deterministically`() {
        val key: NavKey = QrCodeStudioNavKey
        assertTrue(key is QrCodeStudioNavKey)

        val encoded = Json.encodeToString(QrCodeStudioNavKey.serializer(), QrCodeStudioNavKey)
        val decoded = Json.decodeFromString(QrCodeStudioNavKey.serializer(), encoded)
        assertEquals(QrCodeStudioNavKey, decoded)
        assertEquals("qrcode_studio", QR_CODE_STUDIO_TOOL_ID)
    }
}
