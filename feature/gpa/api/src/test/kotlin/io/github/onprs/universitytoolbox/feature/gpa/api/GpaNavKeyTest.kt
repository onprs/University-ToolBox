package io.github.onprs.universitytoolbox.feature.gpa.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpaNavKeyTest {
    @Test
    fun `gpa nav key implements NavKey and serializes deterministically`() {
        val key: NavKey = GpaNavKey
        assertTrue(key is GpaNavKey)

        val encoded = Json.encodeToString(GpaNavKey.serializer(), GpaNavKey)
        val decoded = Json.decodeFromString(GpaNavKey.serializer(), encoded)
        assertEquals(GpaNavKey, decoded)
        assertEquals("gpa_calculator", GPA_TOOL_ID)
    }
}
