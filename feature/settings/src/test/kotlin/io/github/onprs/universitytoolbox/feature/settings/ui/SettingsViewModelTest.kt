package io.github.onprs.universitytoolbox.feature.settings.ui

import app.cash.turbine.test
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.result.DomainError
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.database.entity.AuditLogEntryEntity
import io.github.onprs.universitytoolbox.core.database.entity.FavoriteToolEntity
import io.github.onprs.universitytoolbox.core.database.entity.RecentToolUsageEntity
import io.github.onprs.universitytoolbox.core.datastore.AppLanguage
import io.github.onprs.universitytoolbox.core.datastore.ThemeMode
import io.github.onprs.universitytoolbox.core.datastore.UserPreferences
import io.github.onprs.universitytoolbox.core.datastore.UserPreferencesRepository
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.update.AppUpdateManager
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.core.update.UpdateDownloadState
import io.github.onprs.universitytoolbox.core.update.UpdateManifest
import io.github.onprs.universitytoolbox.feature.settings.domain.CacheCleanupResult
import io.github.onprs.universitytoolbox.feature.settings.domain.PermissionKind
import io.github.onprs.universitytoolbox.feature.settings.domain.PermissionStatusItem
import io.github.onprs.universitytoolbox.feature.settings.domain.UpdateAssessment
import io.github.onprs.universitytoolbox.feature.settings.platform.AppCacheController
import io.github.onprs.universitytoolbox.feature.settings.platform.AppLocaleController
import io.github.onprs.universitytoolbox.feature.settings.platform.AppVersionInfo
import io.github.onprs.universitytoolbox.feature.settings.platform.AppVersionInfoProvider
import io.github.onprs.universitytoolbox.feature.settings.platform.PermissionStatusProvider
import io.github.onprs.universitytoolbox.feature.settings.platform.SettingsPlatformFacade
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        val state = MutableStateFlow(UserPreferences())
        override val userPreferences: Flow<UserPreferences> = state

        override suspend fun setThemeMode(mode: ThemeMode) {
            state.update { it.copy(themeMode = mode) }
        }

        override suspend fun setUseDynamicColor(enabled: Boolean) {
            state.update { it.copy(useDynamicColor = enabled) }
        }

        override suspend fun setAppLanguage(language: AppLanguage) {
            state.update { it.copy(appLanguage = language) }
        }

        override suspend fun setLastUpdateCheckEpochSec(epochSec: Long) {
            state.update { it.copy(lastUpdateCheckEpochSec = epochSec) }
        }

        override suspend fun clearPreferences() {
            state.value = UserPreferences()
        }
    }

    private class FakeMetadataRepository : ToolboxMetadataRepository {
        val auditLogs = MutableStateFlow(
            listOf(
                AuditLogEntryEntity(
                    id = 1L,
                    timestampEpochMs = 1_700_000_000_000L,
                    category = "AUTH",
                    action = "LOGIN",
                    status = "SUCCESS",
                    summary = "Cookie: vc3=rawSecretCookie",
                ),
            ),
        )

        override fun observeFavoriteToolIds(): Flow<Set<String>> = flowOf(emptySet())

        override fun observeFavoriteTools(): Flow<List<FavoriteToolEntity>> = flowOf(emptyList())

        override suspend fun toggleFavorite(toolId: String): Boolean = false

        override suspend fun setFavorite(toolId: String, isFavorite: Boolean) = Unit

        override fun observeRecentToolUsages(limit: Int): Flow<List<RecentToolUsageEntity>> = flowOf(emptyList())

        override suspend fun recordToolUsage(toolId: String) = Unit

        override fun observeAuditLogs(limit: Int): Flow<List<AuditLogEntryEntity>> = auditLogs

        override suspend fun logAuditEvent(
            category: String,
            action: String,
            status: String,
            summary: String,
            toolId: String?,
        ) = Unit

        override suspend fun exportRedactedAuditText(limit: Int): String = ""

        override suspend fun clearRecentUsages() = Unit

        override suspend fun clearAuditLogs() {
            auditLogs.value = emptyList()
        }

        override suspend fun clearAllHistory() = Unit
    }

    private class FakeGovernanceRepository : RemoteGovernanceRepository {
        override val disabledTools = MutableStateFlow<Map<String, String?>>(emptyMap())
        override val latestManifest = MutableStateFlow<UpdateManifest?>(null)
        var nextFetchResult: AppResult<UpdateManifest> = AppResult.Failure(DomainError.Network("Offline"))

        override fun isToolDisabled(toolId: String): Boolean = false

        override fun disableReason(toolId: String): String? = null

        override suspend fun fetchAndApplyManifest(manifestUrl: String): AppResult<UpdateManifest> {
            if (nextFetchResult is AppResult.Success) {
                latestManifest.value = (nextFetchResult as AppResult.Success).data
            }
            return nextFetchResult
        }

        override suspend fun applyManifest(manifest: UpdateManifest) {
            latestManifest.value = manifest
        }
    }

    private class FakeUpdateManager : AppUpdateManager {
        override val updateState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        var canInstall = true
        val installedFiles = mutableListOf<File>()

        override fun canRequestPackageInstalls(): Boolean = canInstall

        override suspend fun downloadAndVerifyUpdate(manifest: UpdateManifest): AppResult<File> {
            val file = File("mock-update.apk")
            updateState.value = UpdateDownloadState.ReadyToInstall(file, manifest.sha256)
            return AppResult.Success(file)
        }

        override suspend fun installVerifiedApk(apkFile: File): AppResult<Unit> {
            installedFiles += apkFile
            updateState.value = UpdateDownloadState.Installing
            return AppResult.Success(Unit)
        }

        override suspend fun clearCachedUpdates() {
            updateState.value = UpdateDownloadState.Idle
        }
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
    fun `settings view model manages theme language permissions update force check cache and logs`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val engine = object : LicenseEngine {
            override val state: StateFlow<LicenseState> = MutableStateFlow(
                LicenseState.Unlocked(
                    LicenseEntitlement("SN-SET", LicenseTier.PRO, setOf("*"), 5, null, false),
                ),
            )
            override suspend fun getDeviceChallenge(): String = "DEV"
            override suspend fun activate(rawKeyInput: String) = Result.failure<LicenseEntitlement>(Exception())
            override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit
            override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit
            override suspend fun clearLicense() = Unit
        }
        val metadata = FakeMetadataRepository()
        val governance = FakeGovernanceRepository()
        val updateManager = FakeUpdateManager()
        val appliedLanguages = mutableListOf<AppLanguage>()
        var cacheBytes = 2048L
        var locationGranted = false
        var notificationsGranted = false

        val platformFacade = SettingsPlatformFacade(
            versionInfoProvider = object : AppVersionInfoProvider {
                override fun getVersionInfo() = AppVersionInfo("1.0.0", 1_000_000L)
            },
            permissionStatusProvider = object : PermissionStatusProvider {
                override fun checkPermissions() = listOf(
                    PermissionStatusItem(PermissionKind.CAMERA, isGranted = true),
                    PermissionStatusItem(
                        kind = PermissionKind.LOCATION,
                        isGranted = locationGranted,
                        requiresSystemSettings = !locationGranted,
                    ),
                    PermissionStatusItem(
                        kind = PermissionKind.NOTIFICATIONS,
                        isGranted = notificationsGranted,
                        requiresSystemSettings = true,
                    ),
                )
            },
            appLocaleController = object : AppLocaleController {
                override fun applyLanguage(language: AppLanguage) {
                    appliedLanguages += language
                }
            },
            appCacheController = object : AppCacheController {
                override fun measureCacheBytes(): Long = cacheBytes
                override fun clearCache(): CacheCleanupResult {
                    val reclaimed = cacheBytes
                    cacheBytes = 0L
                    return CacheCleanupResult(reclaimed, 0L, 2)
                }
            },
        )

        val viewModel = SettingsViewModel(
            userPreferencesRepository = prefs,
            licenseCoordinator = LicenseCoordinator(engine),
            metadataRepository = metadata,
            remoteGovernanceRepository = governance,
            appUpdateManager = updateManager,
            platformFacade = platformFacade,
            timeProvider = TimeProvider { 1_700_000_000_000L },
        )

        viewModel.uiState.test {
            skipItems(1)
            val initial = awaitItem()
            assertEquals("2.0 KB", initial.cacheSizeFormatted)
            assertTrue(initial.diagnosticReportPreview.contains("<REDACTED>"))
            assertFalse(initial.diagnosticReportPreview.contains("rawSecretCookie"))

            viewModel.setThemeMode(ThemeMode.DARK)
            viewModel.setDynamicColorEnabled(false)
            viewModel.setAppLanguage(AppLanguage.EN)
            advanceUntilIdle()
            val afterPrefs = expectMostRecentItem()
            assertEquals(ThemeMode.DARK, afterPrefs.userPreferences.themeMode)
            assertFalse(afterPrefs.userPreferences.useDynamicColor)
            assertEquals(listOf(AppLanguage.EN), appliedLanguages)

            val forceManifest = UpdateManifest(
                latestVersionCode = 1_020_000L,
                latestVersionName = "1.2.0",
                minSupportedVersionCode = 1_010_000L,
                apkUrl = "https://github.com/onprs/University-ToolBox/releases/download/v1.2.0/app.apk",
                sha256 = "a".repeat(64),
                changelog = "Mandatory security update",
            )
            governance.nextFetchResult = AppResult.Success(forceManifest)
            viewModel.checkForUpdates()
            advanceUntilIdle()
            val afterCheck = expectMostRecentItem()
            assertTrue(afterCheck.updateAssessment is UpdateAssessment.ForceUpdateRequired)

            viewModel.downloadAndInstallUpdate()
            advanceUntilIdle()
            assertEquals(1, updateManager.installedFiles.size)

            viewModel.clearCache()
            advanceUntilIdle()
            val afterCache = expectMostRecentItem()
            assertEquals(0L, afterCache.cacheSizeBytes)
            assertTrue(afterCache.snackbarMessage is SettingsSnackbarMessage.CacheCleared)

            viewModel.clearDiagnosticLogs()
            advanceUntilIdle()
            val afterClearLogs = expectMostRecentItem()
            assertTrue(afterClearLogs.diagnosticReportPreview.contains("(No diagnostic audit entries)"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshRuntimeStatus updates location and notification permissions on resume`() = runTest {
        var locationGranted = false
        var notificationsGranted = false
        val engine = object : LicenseEngine {
            override val state: StateFlow<LicenseState> = MutableStateFlow(LicenseState.Initializing)
            override suspend fun getDeviceChallenge(): String = "DEV"
            override suspend fun activate(rawKeyInput: String) = Result.failure<LicenseEntitlement>(Exception())
            override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) = Unit
            override suspend fun updateRevokedList(revokedIds: Set<String>) = Unit
            override suspend fun clearLicense() = Unit
        }
        val platformFacade = SettingsPlatformFacade(
            versionInfoProvider = object : AppVersionInfoProvider {
                override fun getVersionInfo() = AppVersionInfo("1.0.0", 1_000_000L)
            },
            permissionStatusProvider = object : PermissionStatusProvider {
                override fun checkPermissions() = listOf(
                    PermissionStatusItem(PermissionKind.LOCATION, isGranted = locationGranted),
                    PermissionStatusItem(PermissionKind.NOTIFICATIONS, isGranted = notificationsGranted),
                )
            },
            appLocaleController = object : AppLocaleController {
                override fun applyLanguage(language: AppLanguage) = Unit
            },
            appCacheController = object : AppCacheController {
                override fun measureCacheBytes(): Long = 0L
                override fun clearCache() = CacheCleanupResult(0L, 0L, 0)
            },
        )
        val viewModel = SettingsViewModel(
            userPreferencesRepository = FakeUserPreferencesRepository(),
            licenseCoordinator = LicenseCoordinator(engine),
            metadataRepository = FakeMetadataRepository(),
            remoteGovernanceRepository = FakeGovernanceRepository(),
            appUpdateManager = FakeUpdateManager(),
            platformFacade = platformFacade,
            timeProvider = TimeProvider { 1_700_000_000_000L },
        )

        viewModel.uiState.test {
            skipItems(1)
            val initial = awaitItem()
            assertFalse(initial.permissions.first { it.kind == PermissionKind.LOCATION }.isGranted)
            assertFalse(initial.permissions.first { it.kind == PermissionKind.NOTIFICATIONS }.isGranted)

            locationGranted = true
            notificationsGranted = true
            viewModel.refreshRuntimeStatus()
            advanceUntilIdle()
            val refreshed = expectMostRecentItem()
            assertTrue(refreshed.permissions.first { it.kind == PermissionKind.LOCATION }.isGranted)
            assertTrue(refreshed.permissions.first { it.kind == PermissionKind.NOTIFICATIONS }.isGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
