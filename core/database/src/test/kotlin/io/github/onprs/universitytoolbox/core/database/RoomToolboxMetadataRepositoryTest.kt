package io.github.onprs.universitytoolbox.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomToolboxMetadataRepositoryTest {
    private lateinit var database: ToolboxDatabase
    private lateinit var repository: RoomToolboxMetadataRepository
    private val clockMs = AtomicLong(1_760_000_000_000L)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ToolboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomToolboxMetadataRepository(
            favoriteToolDao = database.favoriteToolDao(),
            recentToolDao = database.recentToolDao(),
            auditLogDao = database.auditLogDao(),
            timeProvider = TimeProvider { clockMs.get() },
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `toggle favorite adds and removes tool ids in pinned order`() = runTest {
        repository.observeFavoriteToolIds().test {
            assertEquals(emptySet<String>(), awaitItem())

            assertTrue(repository.toggleFavorite("gpa_calculator"))
            assertEquals(setOf("gpa_calculator"), awaitItem())

            clockMs.addAndGet(1_000L)
            assertTrue(repository.toggleFavorite("chaoxing_assistant"))
            assertEquals(listOf("gpa_calculator", "chaoxing_assistant"), awaitItem().toList())

            assertFalse(repository.toggleFavorite("gpa_calculator"))
            assertEquals(setOf("chaoxing_assistant"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `record tool usage increments count and orders by most recent timestamp`() = runTest {
        repository.recordToolUsage("gpa_calculator")
        clockMs.addAndGet(5_000L)
        repository.recordToolUsage("qrcode_studio")
        clockMs.addAndGet(5_000L)
        repository.recordToolUsage("gpa_calculator")

        repository.observeRecentToolUsages(limit = 5).test {
            val usages = awaitItem()
            assertEquals(2, usages.size)
            assertEquals("gpa_calculator", usages[0].toolId)
            assertEquals(2, usages[0].useCount)
            assertEquals("qrcode_studio", usages[1].toolId)
            assertEquals(1, usages[1].useCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `audit logs redact sensitive values on write and export`() = runTest {
        repository.logAuditEvent(
            category = "chaoxing",
            action = "login password=supersecret",
            status = "failure",
            summary = "Cookie: vc3=token123\nphone 13800138000 failed",
            toolId = "chaoxing_assistant",
        )

        repository.observeAuditLogs(limit = 10).test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            val entry = entries.first()
            assertFalse(entry.action.contains("supersecret"))
            assertFalse(entry.summary.contains("token123"))
            assertFalse(entry.summary.contains("13800138000"))
            assertTrue(entry.summary.contains("138****8000"))
            assertTrue(entry.summary.contains("<REDACTED>"))
            cancelAndIgnoreRemainingEvents()
        }

        val exported = repository.exportRedactedAuditText()
        assertTrue(exported.contains("CHAOXING [chaoxing_assistant] | FAILURE"))
        assertFalse(exported.contains("supersecret"))
        assertFalse(exported.contains("token123"))
        assertFalse(exported.contains("13800138000"))

        repository.clearAllHistory()
        repository.observeAuditLogs(limit = 10).test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repository.observeRecentToolUsages(limit = 10).test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
