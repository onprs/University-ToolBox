package io.github.onprs.universitytoolbox.core.network

import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkInterceptorsTest {
    @Test
    fun `redacting logging interceptor masks headers, body secrets, and phone numbers`() {
        val logLines = CopyOnWriteArrayList<String>()
        val interceptor = RedactingLoggingInterceptor(
            level = RedactingLoggingInterceptor.Level.BODY,
            logger = { line -> logLines.add(line) },
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Set-Cookie", "vc3=server-secret-cookie; Path=/")
                    .addHeader("Content-Type", "application/json")
                    .body("""{"token":"resp-secret-token","phone":"13900139000"}""")
                    .build(),
            )
            server.start()

            val client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build()

            val requestBody = """{"password":"req-secret-pwd","contactPhone":"13800138000"}"""
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(server.url("/auth/login?licenseKey=RAW-SECRET-KEY"))
                .addHeader("Cookie", "UID=12345; vc3=client-cookie-secret")
                .addHeader("Authorization", "Bearer top-secret-bearer")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                response.body.string()
            }
        }

        val combinedLogs = logLines.joinToString(separator = "\n")
        assertFalse(combinedLogs.contains("RAW-SECRET-KEY"))
        assertFalse(combinedLogs.contains("client-cookie-secret"))
        assertFalse(combinedLogs.contains("top-secret-bearer"))
        assertFalse(combinedLogs.contains("req-secret-pwd"))
        assertFalse(combinedLogs.contains("server-secret-cookie"))
        assertFalse(combinedLogs.contains("resp-secret-token"))
        assertFalse(combinedLogs.contains("13800138000"))
        assertFalse(combinedLogs.contains("13900139000"))
        assertTrue(combinedLogs.contains("<REDACTED>"))
        assertTrue(combinedLogs.contains("138****8000"))
        assertTrue(combinedLogs.contains("139****9000"))
    }

    @Test
    fun `trusted network time interceptor parses Date header and notifies license engine`() = runTest {
        val syncedEpochDeferred = CompletableDeferred<Long>()
        val fakeEngine = FakeLicenseEngine(
            onTimeSynced = { epochSec ->
                if (!syncedEpochDeferred.isCompleted) {
                    syncedEpochDeferred.complete(epochSec)
                }
            },
        )
        val headerDate = "Wed, 21 Oct 2026 07:28:00 GMT"
        val expectedEpoch = ZonedDateTime.parse(headerDate, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()

        val interceptor = TrustedNetworkTimeInterceptor(
            licenseEngineProvider = Provider { fakeEngine },
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            requireHttps = false,
            minSyncIntervalSec = 30L,
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Date", headerDate)
                    .body("ok")
                    .build(),
            )
            server.start()

            val client = OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor)
                .build()

            val request = Request.Builder()
                .url(server.url("/ping"))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }
        }

        assertEquals(expectedEpoch, syncedEpochDeferred.await())
        assertEquals(listOf(expectedEpoch), fakeEngine.syncedTimes)
    }

    @Test
    fun `trusted network time interceptor skips cleartext HTTP when requireHttps is true`() = runTest {
        val fakeEngine = FakeLicenseEngine()
        val interceptor = TrustedNetworkTimeInterceptor(
            licenseEngineProvider = Provider { fakeEngine },
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            requireHttps = true,
            minSyncIntervalSec = 30L,
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Date", "Wed, 21 Oct 2026 07:28:00 GMT")
                    .body("ok")
                    .build(),
            )
            server.start()

            val client = OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor)
                .build()

            val request = Request.Builder()
                .url(server.url("/ping"))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }
        }

        assertTrue(fakeEngine.syncedTimes.isEmpty())
        assertNull(TrustedNetworkTimeInterceptor.parseHttpDateEpochSeconds("invalid-date"))
    }

    @Test
    fun `image compat interceptor injects browser user agent referer and upgrades chaoxing http urls`() {
        val interceptor = ImageCompatRequestInterceptor()
        assertEquals(
            "https://p.ananas.chaoxing.com/star4/demo/240_130c.jpg",
            ImageCompatRequestInterceptor.upgradeInsecureKnownImageUrl(
                okhttp3.HttpUrl.Builder()
                    .scheme("http")
                    .host("p.ananas.chaoxing.com")
                    .addPathSegments("star4/demo/240_130c.jpg")
                    .build(),
            ).toString(),
        )
        assertTrue(ImageCompatRequestInterceptor.isChaoxingImageHost("p.ananas.chaoxing.com"))
        assertTrue(ImageCompatRequestInterceptor.isChaoxingImageHost("p.cldisk.com"))
        assertFalse(ImageCompatRequestInterceptor.isChaoxingImageHost("localhost"))

        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(200).body("img").build())
            server.start()

            val client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build()

            val request = Request.Builder()
                .url(server.url("/tile.png"))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }

            val recorded = server.takeRequest()
            assertEquals(ImageCompatRequestInterceptor.MOBILE_BROWSER_USER_AGENT, recorded.headers["User-Agent"])
        }
    }

    private class FakeLicenseEngine(private val onTimeSynced: (Long) -> Unit = {}) : LicenseEngine {
        private val mutableState = MutableStateFlow<LicenseState>(
            LicenseState.Locked(LockReason.NOT_ACTIVATED, "TEST-CHALLENGE"),
        )
        override val state: StateFlow<LicenseState> = mutableState
        val syncedTimes = CopyOnWriteArrayList<Long>()

        override suspend fun getDeviceChallenge(): String = "TEST-CHALLENGE"

        override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> =
            Result.failure(UnsupportedOperationException("Not used in test"))

        override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) {
            syncedTimes.add(serverEpochSec)
            onTimeSynced(serverEpochSec)
        }

        override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit

        override suspend fun clearLicense() = Unit
    }
}
