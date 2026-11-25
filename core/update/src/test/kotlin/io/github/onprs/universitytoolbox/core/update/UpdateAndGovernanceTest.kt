package io.github.onprs.universitytoolbox.core.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdateAndGovernanceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun `manifest deserializes governance fields and checks version thresholds`() {
        val rawJson = """
            {
              "channel": "stable",
              "latestVersionCode": 1020000,
              "latestVersionName": "1.2.0",
              "minSupportedVersionCode": 1000500,
              "apkUrl": "https://cdn.example.org/releases/app-1.2.0.apk",
              "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
              "changelog": "1. Improved scanner stability",
              "disabledTools": {
                "chaoxing_assistant": "Upstream maintenance",
                "qrcode_studio": null
              },
              "revokedSerialIds": ["KEY-001", "KEY-002"],
              "unknownExtra": true
            }
        """.trimIndent()

        val manifest = lenientJson.decodeFromString<UpdateManifest>(rawJson)
        assertEquals("stable", manifest.channel)
        assertEquals(1_020_000L, manifest.latestVersionCode)
        assertEquals("1.2.0", manifest.latestVersionName)
        assertEquals(1_000_500L, manifest.minSupportedVersionCode)
        assertEquals("Upstream maintenance", manifest.disabledTools["chaoxing_assistant"])
        assertTrue("qrcode_studio" in manifest.disabledTools)
        assertEquals(listOf("KEY-001", "KEY-002"), manifest.revokedSerialIds)
        assertTrue(manifest.isUpdateAvailable(1_010_000L))
        assertFalse(manifest.isUpdateAvailable(1_020_000L))
        assertTrue(manifest.isForceUpdateRequired(1_000_000L))
        assertFalse(manifest.isForceUpdateRequired(1_000_500L))
    }

    @Test
    fun `remote governance fetches manifest, syncs revoked list, and degrades safely on failure`() = runTest {
        val fakeEngine = FakeLicenseEngine()
        val repository = DefaultRemoteGovernanceRepository(
            okHttpClient = OkHttpClient(),
            json = lenientJson,
            licenseEngine = fakeEngine,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            allowCleartextForTesting = true,
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(
                        """
                        {
                          "latestVersionCode": 200,
                          "latestVersionName": "2.0.0",
                          "minSupportedVersionCode": 150,
                          "apkUrl": "https://cdn.example.org/app-2.0.0.apk",
                          "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                          "disabledTools": {"chaoxing_assistant": "Scheduled upgrade"},
                          "revokedSerialIds": ["REV-100", "REV-200"]
                        }
                        """.trimIndent(),
                    )
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(503)
                    .body("Service Unavailable")
                    .build(),
            )
            server.start()

            val manifestUrl = server.url("/update-manifest.json").toString()
            val firstResult = repository.fetchAndApplyManifest(manifestUrl)
            assertTrue(firstResult is AppResult.Success)

            repository.disabledTools.test {
                val map = awaitItem()
                assertTrue(repository.isToolDisabled("chaoxing_assistant"))
                assertEquals("Scheduled upgrade", repository.disableReason("chaoxing_assistant"))
                assertFalse(repository.isToolDisabled("gpa_calculator"))
                assertEquals(1, map.size)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(setOf("REV-100", "REV-200"), fakeEngine.lastRevokedIds)

            // 服务端失败时保留原熔断配置安全降级
            val secondResult = repository.fetchAndApplyManifest(manifestUrl)
            assertTrue(secondResult is AppResult.Failure)
            assertTrue(repository.isToolDisabled("chaoxing_assistant"))
            assertEquals(setOf("REV-100", "REV-200"), fakeEngine.lastRevokedIds)
        }
    }

    @Test
    fun `download verifies matching sha256 and rejects mismatched sha256 deleting temp file`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = tempFolder.newFolder("update-cache")
        val mockApkBytes = "FAKE-APK-BINARY-PAYLOAD-FOR-DIGEST-CHECK".toByteArray(Charsets.UTF_8)
        val validSha256 = Sha256Verifier.computeSha256Hex(mockApkBytes)
        val wrongSha256 = "0".repeat(64)

        val manager = AndroidAppUpdateManager(
            context = context,
            okHttpClient = OkHttpClient(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            cacheRootDir = cacheDir,
            signatureVerifier = { AppResult.Success(Unit) },
            allowCleartextForTesting = true,
            applicationScope = backgroundScope,
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(String(mockApkBytes, Charsets.UTF_8))
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(String(mockApkBytes, Charsets.UTF_8))
                    .build(),
            )
            server.start()

            val apkUrl = server.url("/releases/toolbox.apk").toString()

            val validManifest = UpdateManifest(
                latestVersionCode = 2001L,
                latestVersionName = "2.0.1",
                apkUrl = apkUrl,
                sha256 = validSha256,
            )
            val successResult = manager.downloadAndVerifyUpdate(validManifest)
            assertTrue(successResult is AppResult.Success)
            val downloadedFile = (successResult as AppResult.Success).data
            assertTrue(downloadedFile.exists())
            assertTrue(manager.updateState.value is UpdateDownloadState.ReadyToInstall)

            // 校验 SHA-256 不匹配时必须拒绝并删除缓存文件
            val invalidManifest = validManifest.copy(
                latestVersionCode = 2002L,
                sha256 = wrongSha256,
            )
            val failureResult = manager.downloadAndVerifyUpdate(invalidManifest)
            assertTrue(failureResult is AppResult.Failure)
            assertTrue((failureResult as AppResult.Failure).error is DomainError.Security)
            val rejectedFile = File(cacheDir, "updates/update-2002.apk")
            val rejectedPart = File(cacheDir, "updates/update-2002.apk.part")
            assertFalse(rejectedFile.exists())
            assertFalse(rejectedPart.exists())
            assertTrue(manager.updateState.value is UpdateDownloadState.Failed)
        }
    }

    @Test
    fun `download rejects apk when signature verification fails and deletes file`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = tempFolder.newFolder("sig-cache")
        val mockApkBytes = "TAMPERED-SIGNATURE-APK-BYTES".toByteArray(Charsets.UTF_8)
        val validSha256 = Sha256Verifier.computeSha256Hex(mockApkBytes)

        val manager = AndroidAppUpdateManager(
            context = context,
            okHttpClient = OkHttpClient(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            cacheRootDir = cacheDir,
            signatureVerifier = {
                AppResult.Failure(DomainError.Security("Signature fingerprint mismatch"))
            },
            allowCleartextForTesting = true,
            applicationScope = backgroundScope,
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(String(mockApkBytes, Charsets.UTF_8))
                    .build(),
            )
            server.start()

            val manifest = UpdateManifest(
                latestVersionCode = 3001L,
                latestVersionName = "3.0.1",
                apkUrl = server.url("/releases/repacked.apk").toString(),
                sha256 = validSha256,
            )

            val result = manager.downloadAndVerifyUpdate(manifest)
            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is DomainError.Security)
            assertFalse(File(cacheDir, "updates/update-3001.apk").exists())
        }

        assertTrue(ApkSignatureVerifier.signaturesMatch(setOf("abc"), setOf("abc", "def")))
        assertFalse(ApkSignatureVerifier.signaturesMatch(setOf("abc"), setOf("xyz")))
        assertFalse(ApkSignatureVerifier.signaturesMatch(emptySet(), emptySet()))
    }

    private class FakeLicenseEngine : LicenseEngine {
        private val mutableState = MutableStateFlow<LicenseState>(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "TEST-CHALLENGE"),
        )
        override val state: StateFlow<LicenseState> = mutableState
        var lastRevokedIds: Set<String> = emptySet()

        override suspend fun getDeviceChallenge(): String = "TEST-CHALLENGE"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException("Not used in test"))

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit

        override suspend fun updateRevokedList(revokedIds: Set<String>) {
            lastRevokedIds = revokedIds.toSet()
        }

        override suspend fun clearLicense() = Unit
    }
}
