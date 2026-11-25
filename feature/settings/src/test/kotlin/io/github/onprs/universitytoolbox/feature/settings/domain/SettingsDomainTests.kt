package io.github.onprs.universitytoolbox.feature.settings.domain

import java.io.File
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsDomainTests {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `diagnostic log formatter redacts sensitive tokens and formats header`() {
        val report = DiagnosticLogFormatter.formatReport(
            appVersionName = "1.0.0",
            appVersionCode = 1_000_000L,
            licenseTierLabel = "STANDARD",
            isOfficialEngine = false,
            logs = listOf(
                AuditLogRecord(
                    id = 1L,
                    createdAtEpochMs = 1_700_000_000_000L,
                    category = "auth",
                    action = "login",
                    summary = "Cookie: vc3=secretValue token=topSecretValue",
                ),
            ),
            zoneId = ZoneOffset.UTC,
        )

        assertTrue(report.contains("University-ToolBox Diagnostic Report"))
        assertTrue(report.contains("Version: 1.0.0 (1000000)"))
        assertTrue(report.contains("Engine Mode: DEV_STUB"))
        assertTrue(report.contains("<REDACTED>"))
        assertFalse(report.contains("secretValue"))
        assertFalse(report.contains("topSecretValue"))
    }

    @Test
    fun `update policy evaluator distinguishes force optional and up to date states`() {
        val forced = UpdatePolicyEvaluator.evaluate(
            currentVersionCode = 1_000_000L,
            latestVersionCode = 1_020_000L,
            latestVersionName = "1.2.0",
            minSupportedVersionCode = 1_010_000L,
            changelog = "Security patch",
        )
        assertTrue(forced is UpdateAssessment.ForceUpdateRequired)

        val optional = UpdatePolicyEvaluator.evaluate(
            currentVersionCode = 1_010_000L,
            latestVersionCode = 1_020_000L,
            latestVersionName = "1.2.0",
            minSupportedVersionCode = 1_010_000L,
            changelog = "New features",
        )
        assertTrue(optional is UpdateAssessment.OptionalUpdate)

        val upToDate = UpdatePolicyEvaluator.evaluate(
            currentVersionCode = 1_020_000L,
            latestVersionCode = 1_020_000L,
            latestVersionName = "1.2.0",
            minSupportedVersionCode = 1_010_000L,
            changelog = "Current",
        )
        assertEquals(UpdateAssessment.UpToDate, upToDate)
    }

    @Test
    fun `cache storage cleaner measures clears non excluded files and formats size`() {
        val cacheDir = tempFolder.newFolder("cache")
        val subDir = File(cacheDir, "images").apply { mkdirs() }
        val file1 = File(subDir, "img1.bin").apply { writeBytes(ByteArray(1536)) }
        val keepDir = File(cacheDir, "keep_me").apply { mkdirs() }
        val keptFile = File(keepDir, "keep.dat").apply { writeBytes(ByteArray(512)) }

        assertEquals(2048L, CacheStorageCleaner.calculateTotalBytes(listOf(cacheDir)))
        val result = CacheStorageCleaner.clearDirectories(
            directories = listOf(cacheDir),
            excludedNames = setOf("keep_me"),
        )

        assertFalse(file1.exists())
        assertTrue(keptFile.exists())
        assertEquals(1536L, result.reclaimedBytes)
        assertEquals(512L, result.remainingBytes)
        assertEquals(1, result.deletedFilesCount)
        assertEquals("512 B", CacheStorageCleaner.formatByteSize(512L))
        assertEquals("1.5 KB", CacheStorageCleaner.formatByteSize(1536L))
    }
}
