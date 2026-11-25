package io.github.onprs.universitytoolbox.feature.chaoxing.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChaoxingNavKeyTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun `tool id constant matches platform contract`() {
        assertEquals("chaoxing_assistant", CHAOXING_TOOL_ID)
    }

    @Test
    fun `chaoxing nav keys serialize and deserialize symmetrically`() {
        val keys: List<ChaoxingNavKey> = listOf(
            ChaoxingNavKey.Dashboard,
            ChaoxingNavKey.Accounts(),
            ChaoxingNavKey.Accounts(
                initialSharePayload = "universitytoolbox://chaoxing-bind?phone=13800138000&pwd=abc",
            ),
            ChaoxingNavKey.CourseActivities(
                accountId = "acc-01",
                courseId = 200000001L,
                clazzId = 100000001L,
                cpi = 100000002L,
                courseName = "数据结构示例课程",
            ),
            ChaoxingNavKey.SignExecution(
                accountId = "acc-01",
                courseId = 200000001L,
                clazzId = 100000001L,
                activeId = 7000000000001L,
                courseName = "数据结构示例课程",
            ),
            ChaoxingNavKey.SignRecords(accountId = "acc-01"),
            ChaoxingNavKey.SignRecords(accountId = null),
        )

        for (key in keys) {
            val encoded = json.encodeToString(ChaoxingNavKey.serializer(), key)
            val decoded = json.decodeFromString(ChaoxingNavKey.serializer(), encoded)
            assertEquals(key, decoded)
        }
    }
}
