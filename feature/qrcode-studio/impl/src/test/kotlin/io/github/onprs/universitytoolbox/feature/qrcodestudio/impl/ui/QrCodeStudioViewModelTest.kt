package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.ui

import android.graphics.Bitmap
import android.net.Uri
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.database.entity.AuditLogEntryEntity
import io.github.onprs.universitytoolbox.core.database.entity.FavoriteToolEntity
import io.github.onprs.universitytoolbox.core.database.entity.RecentToolUsageEntity
import io.github.onprs.universitytoolbox.core.scanner.BarcodeFormat
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanResult
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScannerMode
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QR_CODE_STUDIO_TOOL_ID
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.domain.QrPayloadCategory
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QrCodeStudioViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeBarcodeScanner : BarcodeScanner {
        var nextResult: AppResult<List<BarcodeScanResult>> = AppResult.Success(emptyList())

        override suspend fun scanImageUri(uri: Uri): AppResult<List<BarcodeScanResult>> = nextResult

        override suspend fun scanBitmap(bitmap: Bitmap): AppResult<List<BarcodeScanResult>> = nextResult
    }

    private class FakeMetadataRepository : ToolboxMetadataRepository {
        val recordedToolIds = mutableListOf<String>()
        val auditSummaries = mutableListOf<String>()

        override fun observeFavoriteToolIds(): Flow<Set<String>> = flowOf(emptySet())

        override fun observeFavoriteTools(): Flow<List<FavoriteToolEntity>> = flowOf(emptyList())

        override suspend fun toggleFavorite(toolId: String): Boolean = true

        override suspend fun setFavorite(toolId: String, isFavorite: Boolean) = Unit

        override fun observeRecentToolUsages(limit: Int): Flow<List<RecentToolUsageEntity>> = flowOf(emptyList())

        override suspend fun recordToolUsage(toolId: String) {
            recordedToolIds += toolId
        }

        override fun observeAuditLogs(limit: Int): Flow<List<AuditLogEntryEntity>> = flowOf(emptyList())

        override suspend fun logAuditEvent(
            category: String,
            action: String,
            status: String,
            summary: String,
            toolId: String?,
        ) {
            auditSummaries += summary
        }

        override suspend fun exportRedactedAuditText(limit: Int): String = ""

        override suspend fun clearRecentUsages() = Unit

        override suspend fun clearAuditLogs() = Unit

        override suspend fun clearAllHistory() = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `manual inspection live scan and history deduplication update state and metadata safely`() = runTest {
        val scanner = FakeBarcodeScanner()
        val metadata = FakeMetadataRepository()
        var nowMs = 1_700_000_000_000L
        val viewModel = QrCodeStudioViewModel(
            barcodeScanner = scanner,
            metadataRepository = metadata,
            timeProvider = TimeProvider { nowMs },
        )

        viewModel.updateManualInput("https://portal.university.edu/course?token=hidden123")
        viewModel.inspectManualInput()
        advanceUntilIdle()

        val first = viewModel.uiState.value.selectedPayload
        assertNotNull(first)
        assertEquals(QrPayloadCategory.URL, first?.category)
        assertEquals("", viewModel.uiState.value.manualInput)
        assertEquals(listOf(QR_CODE_STUDIO_TOOL_ID), metadata.recordedToolIds)
        assertTrue(metadata.auditSummaries.single().none { false })
        assertFalse(metadata.auditSummaries.single().contains("hidden123"))

        nowMs += 1_000L
        viewModel.openScanner()
        assertTrue(viewModel.uiState.value.isScannerOpen)
        viewModel.onLiveBarcodeDetected(
            BarcodeScanResult(
                rawValue = "WIFI:T:WPA;S:CampusNet;P:SecretPass;;",
                displayValue = "CampusNet",
                format = BarcodeFormat.QR_CODE,
                detectedAtEpochMs = nowMs,
                sourceMode = BarcodeScannerMode.LIVE_CAMERA,
            ),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isScannerOpen)
        assertEquals(2, viewModel.uiState.value.history.size)
        assertEquals(QrPayloadCategory.WIFI, viewModel.uiState.value.selectedPayload?.category)

        viewModel.toggleFieldVisibility("field-0")
        assertTrue("field-0" in viewModel.uiState.value.revealedFieldIds)
        viewModel.toggleFieldVisibility("field-0")
        assertFalse("field-0" in viewModel.uiState.value.revealedFieldIds)

        viewModel.clearHistory()
        assertTrue(viewModel.uiState.value.history.isEmpty())
        assertNull(viewModel.uiState.value.selectedPayload)
    }

    @Test
    fun `image scan handles success empty result and scanner failure cleanly`() = runTest {
        val scanner = FakeBarcodeScanner()
        val metadata = FakeMetadataRepository()
        val viewModel = QrCodeStudioViewModel(
            barcodeScanner = scanner,
            metadataRepository = metadata,
            timeProvider = TimeProvider { 1_700_000_000_000L },
        )
        val dummyUri = mockk<Uri>(relaxed = true)

        scanner.nextResult = AppResult.Success(emptyList())
        viewModel.scanImageUri(dummyUri)
        advanceUntilIdle()
        assertEquals(StudioNoticeMessage.NO_BARCODE_IN_IMAGE, viewModel.uiState.value.noticeMessage)

        scanner.nextResult = AppResult.Success(
            listOf(
                BarcodeScanResult(
                    rawValue = "https://campus.edu/check",
                    displayValue = null,
                    format = BarcodeFormat.QR_CODE,
                    detectedAtEpochMs = 1_700_000_000_100L,
                    sourceMode = BarcodeScannerMode.STATIC_IMAGE,
                ),
            ),
        )
        viewModel.scanImageUri(dummyUri)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.noticeMessage)
        assertEquals(QrPayloadCategory.URL, viewModel.uiState.value.selectedPayload?.category)

        scanner.nextResult = AppResult.Failure(DomainError.Unknown("Decode error"))
        viewModel.scanImageUri(dummyUri)
        advanceUntilIdle()
        assertEquals(StudioNoticeMessage.IMAGE_SCAN_FAILED, viewModel.uiState.value.noticeMessage)
    }
}
