package io.github.onprs.universitytoolbox.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LenientJsonTest {
    @Serializable
    private data class SamplePayload(val id: Int, val label: String = "default")

    @Test
    fun `lenient json ignores unknown keys and coerces defaults`() {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }
        val parsed = json.decodeFromString<SamplePayload>(
            """{"id": 7, "label": null, "extraField": "ignored"}""",
        )
        assertEquals(7, parsed.id)
        assertEquals("default", parsed.label)
    }
}
