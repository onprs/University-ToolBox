package io.github.onprs.universitytoolbox.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.onprs.universitytoolbox.core.common.result.AppResult
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.datastore.AppLanguage
import io.github.onprs.universitytoolbox.core.datastore.ThemeMode
import io.github.onprs.universitytoolbox.core.datastore.UserPreferences
import io.github.onprs.universitytoolbox.core.datastore.UserPreferencesRepository
import io.github.onprs.universitytoolbox.core.license.api.LicenseCoordinator
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.update.AppUpdateManager
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import io.github.onprs.universitytoolbox.core.update.UpdateDownloadState
import io.github.onprs.universitytoolbox.core.update.UpdateManifest
import io.github.onprs.universitytoolbox.feature.settings.domain.AuditLogRecord
import io.github.onprs.universitytoolbox.feature.settings.domain.CacheStorageCleaner
import io.github.onprs.universitytoolbox.feature.settings.domain.DiagnosticLogFormatter
import io.github.onprs.universitytoolbox.feature.settings.domain.PermissionStatusItem
import io.github.onprs.universitytoolbox.feature.settings.domain.UpdateAssessment
import io.github.onprs.universitytoolbox.feature.settings.domain.UpdatePolicyEvaluator
import io.github.onprs.universitytoolbox.feature.settings.platform.AppVersionInfo
import io.github.onprs.universitytoolbox.feature.settings.platform.SettingsPlatformFacade
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置页瞬态反馈消息。
 */
sealed interface SettingsSnackbarMessage {
    data class CacheCleared(val reclaimedSizeFormatted: String) : SettingsSnackbarMessage

    data object LogsCleared : SettingsSnackbarMessage

    data class UpdateError(val detail: String) : SettingsSnackbarMessage

    data object InstallPermissionRequired : SettingsSnackbarMessage
}

/**
 * 全局设置中心完整界面状态模型。
 */
data class SettingsUiState(
    val userPreferences: UserPreferences = UserPreferences(),
    val licenseState: LicenseState = LicenseState.Initializing,
    val entitlement: LicenseEntitlement? = null,
    val versionInfo: AppVersionInfo = AppVersionInfo(DEFAULT_VERSION_NAME, DEFAULT_VERSION_CODE),
    val permissions: List<PermissionStatusItem> = emptyList(),
    val cacheSizeBytes: Long = 0L,
    val cacheSizeFormatted: String = "0 B",
    val manifestUrlInput: String = DEFAULT_MANIFEST_URL,
    val isCheckingUpdate: Boolean = false,
    val latestManifest: UpdateManifest? = null,
    val updateAssessment: UpdateAssessment = UpdateAssessment.UpToDate,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    val isDiagnosticDialogOpen: Boolean = false,
    val diagnosticReportPreview: String = "",
    val snackbarMessage: SettingsSnackbarMessage? = null,
) {
    companion object {
        const val DEFAULT_VERSION_NAME = "1.0.0"
        const val DEFAULT_VERSION_CODE = 1_000_000L
        const val DEFAULT_MANIFEST_URL =
            "https://github.com/onprs/University-ToolBox/releases/latest/download/update-manifest.json"
    }
}

private data class SettingsLocalState(
    val permissions: List<PermissionStatusItem> = emptyList(),
    val cacheSizeBytes: Long = 0L,
    val manifestUrlInput: String = SettingsUiState.DEFAULT_MANIFEST_URL,
    val isCheckingUpdate: Boolean = false,
    val isDiagnosticDialogOpen: Boolean = false,
    val snackbarMessage: SettingsSnackbarMessage? = null,
)

private data class CoreSignals(
    val prefs: UserPreferences,
    val licenseState: LicenseState,
    val latestManifest: UpdateManifest?,
    val downloadState: UpdateDownloadState,
)

/**
 * 全局设置中心 ViewModel：统一管理主题/动态取色、语言切换、权限核验、脱敏日志导出、缓存清理及自更新流程。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val licenseCoordinator: LicenseCoordinator,
    private val metadataRepository: ToolboxMetadataRepository,
    private val remoteGovernanceRepository: RemoteGovernanceRepository,
    private val appUpdateManager: AppUpdateManager,
    private val platformFacade: SettingsPlatformFacade,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val versionInfo = platformFacade.versionInfoProvider.getVersionInfo()
    private val localState = MutableStateFlow(
        SettingsLocalState(
            permissions = platformFacade.permissionStatusProvider.checkPermissions(),
            cacheSizeBytes = platformFacade.appCacheController.measureCacheBytes(),
        ),
    )

    private val coreSignals = combine(
        userPreferencesRepository.userPreferences,
        licenseCoordinator.state,
        remoteGovernanceRepository.latestManifest,
        appUpdateManager.updateState,
    ) { prefs, licenseState, manifest, downloadState ->
        CoreSignals(
            prefs = prefs,
            licenseState = licenseState,
            latestManifest = manifest,
            downloadState = downloadState,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        coreSignals,
        metadataRepository.observeAuditLogs(),
        localState,
    ) { core, auditEntities, local ->
        val entitlement = (core.licenseState as? LicenseState.Unlocked)?.entitlement
        val assessment = core.latestManifest?.let { manifest ->
            UpdatePolicyEvaluator.evaluate(
                currentVersionCode = versionInfo.versionCode,
                latestVersionCode = manifest.latestVersionCode,
                latestVersionName = manifest.latestVersionName,
                minSupportedVersionCode = manifest.minSupportedVersionCode,
                changelog = manifest.changelog,
            )
        } ?: UpdateAssessment.UpToDate

        val auditRecords = auditEntities.map { entity ->
            AuditLogRecord(
                id = entity.id,
                createdAtEpochMs = entity.timestampEpochMs,
                category = entity.category,
                action = entity.action,
                summary = entity.summary,
            )
        }
        val report = DiagnosticLogFormatter.formatReport(
            appVersionName = versionInfo.versionName,
            appVersionCode = versionInfo.versionCode,
            licenseTierLabel = entitlement?.tier?.name ?: "LOCKED",
            isOfficialEngine = entitlement?.isOfficialEngine ?: false,
            logs = auditRecords,
        )

        SettingsUiState(
            userPreferences = core.prefs,
            licenseState = core.licenseState,
            entitlement = entitlement,
            versionInfo = versionInfo,
            permissions = local.permissions,
            cacheSizeBytes = local.cacheSizeBytes,
            cacheSizeFormatted = CacheStorageCleaner.formatByteSize(local.cacheSizeBytes),
            manifestUrlInput = local.manifestUrlInput,
            isCheckingUpdate = local.isCheckingUpdate,
            latestManifest = core.latestManifest,
            updateAssessment = assessment,
            downloadState = core.downloadState,
            isDiagnosticDialogOpen = local.isDiagnosticDialogOpen,
            diagnosticReportPreview = report,
            snackbarMessage = local.snackbarMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(versionInfo = versionInfo),
    )

    fun refreshRuntimeStatus() {
        localState.update {
            it.copy(
                permissions = platformFacade.permissionStatusProvider.checkPermissions(),
                cacheSizeBytes = platformFacade.appCacheController.measureCacheBytes(),
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseDynamicColor(enabled)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            userPreferencesRepository.setAppLanguage(language)
            platformFacade.appLocaleController.applyLanguage(language)
        }
    }

    fun updateManifestUrl(url: String) {
        localState.update { it.copy(manifestUrlInput = url) }
    }

    fun checkForUpdates() {
        val targetUrl = localState.value.manifestUrlInput.trim()
        viewModelScope.launch {
            localState.update { it.copy(isCheckingUpdate = true) }
            when (val result = remoteGovernanceRepository.fetchAndApplyManifest(targetUrl)) {
                is AppResult.Success -> {
                    userPreferencesRepository.setLastUpdateCheckEpochSec(timeProvider.currentEpochSeconds())
                    metadataRepository.logAuditEvent(
                        category = "UPDATE",
                        action = "CHECK_MANIFEST",
                        status = "SUCCESS",
                        summary = "Checked update manifest v${result.data.latestVersionName}",
                    )
                }
                is AppResult.Failure -> {
                    localState.update {
                        it.copy(snackbarMessage = SettingsSnackbarMessage.UpdateError(result.error.message))
                    }
                    metadataRepository.logAuditEvent(
                        category = "UPDATE",
                        action = "CHECK_MANIFEST",
                        status = "FAILED",
                        summary = "Update check failed: ${result.error.message}",
                    )
                }
            }
            localState.update { it.copy(isCheckingUpdate = false) }
        }
    }

    fun downloadAndInstallUpdate() {
        val manifest = uiState.value.latestManifest ?: return
        viewModelScope.launch {
            when (val downloadResult = appUpdateManager.downloadAndVerifyUpdate(manifest)) {
                is AppResult.Success -> {
                    localState.update {
                        it.copy(cacheSizeBytes = platformFacade.appCacheController.measureCacheBytes())
                    }
                    if (appUpdateManager.canRequestPackageInstalls()) {
                        appUpdateManager.installVerifiedApk(downloadResult.data)
                    } else {
                        localState.update {
                            it.copy(snackbarMessage = SettingsSnackbarMessage.InstallPermissionRequired)
                        }
                    }
                }
                is AppResult.Failure -> {
                    localState.update {
                        it.copy(snackbarMessage = SettingsSnackbarMessage.UpdateError(downloadResult.error.message))
                    }
                }
            }
        }
    }

    fun installDownloadedApk() {
        val readyState = uiState.value.downloadState as? UpdateDownloadState.ReadyToInstall ?: return
        if (!appUpdateManager.canRequestPackageInstalls()) {
            localState.update {
                it.copy(snackbarMessage = SettingsSnackbarMessage.InstallPermissionRequired)
            }
            return
        }
        viewModelScope.launch {
            val result = appUpdateManager.installVerifiedApk(readyState.apkFile)
            if (result is AppResult.Failure) {
                localState.update {
                    it.copy(snackbarMessage = SettingsSnackbarMessage.UpdateError(result.error.message))
                }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            appUpdateManager.clearCachedUpdates()
            val cleanup = platformFacade.appCacheController.clearCache()
            val formatted = CacheStorageCleaner.formatByteSize(cleanup.reclaimedBytes)
            localState.update {
                it.copy(
                    cacheSizeBytes = cleanup.remainingBytes,
                    snackbarMessage = SettingsSnackbarMessage.CacheCleared(formatted),
                )
            }
            metadataRepository.logAuditEvent(
                category = "STORAGE",
                action = "CLEAR_CACHE",
                status = "SUCCESS",
                summary = "Cleared cache ($formatted reclaimed)",
            )
        }
    }

    fun openDiagnosticPreview() {
        localState.update { it.copy(isDiagnosticDialogOpen = true) }
    }

    fun closeDiagnosticPreview() {
        localState.update { it.copy(isDiagnosticDialogOpen = false) }
    }

    fun clearDiagnosticLogs() {
        viewModelScope.launch {
            metadataRepository.clearAuditLogs()
            localState.update {
                it.copy(
                    isDiagnosticDialogOpen = false,
                    snackbarMessage = SettingsSnackbarMessage.LogsCleared,
                )
            }
        }
    }

    fun consumeSnackbarMessage() {
        localState.update { it.copy(snackbarMessage = null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
