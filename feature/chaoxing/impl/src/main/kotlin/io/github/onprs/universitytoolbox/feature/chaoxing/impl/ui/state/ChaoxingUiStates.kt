package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state

import io.github.onprs.universitytoolbox.core.license.api.AccessDecision
import io.github.onprs.universitytoolbox.core.license.api.AccessDenialReason
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCourseEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingPresetLocationEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingSignRecordEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActiveDetail
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingActivity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingQrLoginSession
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingSchoolOption
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.BatchSignSummary
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountShareBundle
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.ResolvedPlace

/**
 * 内存二进制图片载体，避免在状态数据类中直接声明数组触发等价性警告。
 */
class ImageBytesHolder(val bytes: ByteArray, val fileName: String = "image.jpg")

/**
 * 驱动地图选点弹窗强制跳转回正的单调序号指令（避免重复点击相同坐标时被状态判等吞掉）。
 */
data class MapJumpCommand(val sequence: Long, val place: ResolvedPlace)

data class ChaoxingDashboardUiState(
    val licenseState: LicenseState = LicenseState.Initializing,
    val accessDecision: AccessDecision = AccessDecision.Denied(AccessDenialReason.EngineInitializing),
    val isRemoteDisabled: Boolean = false,
    val disableReason: String? = null,
    val accounts: List<ChaoxingAccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val courses: List<ChaoxingCourseEntity> = emptyList(),
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val isLoadingCourses: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedAccount: ChaoxingAccountEntity?
        get() = accounts.firstOrNull { it.accountId == selectedAccountId } ?: accounts.firstOrNull()

    val filteredCourses: List<ChaoxingCourseEntity>
        get() {
            val normalized = searchQuery.trim().lowercase()
            return courses.filter { course ->
                val matchArchive = showArchived || !course.isArchived
                val matchKeyword = normalized.isEmpty() ||
                    course.name.lowercase().contains(normalized) ||
                    course.teacherName.lowercase().contains(normalized) ||
                    course.className.lowercase().contains(normalized)
                matchArchive && matchKeyword
            }
        }
}

enum class ChaoxingLoginTab {
    PASSWORD,
    QR_CODE,
    IMPORT_SHARED,
}

data class ChaoxingAccountsUiState(
    val licenseState: LicenseState = LicenseState.Initializing,
    val accounts: List<ChaoxingAccountEntity> = emptyList(),
    val maxSubAccounts: Int = 0,
    val currentSubAccounts: Int = 0,
    val canBindMore: Boolean = false,
    val activeTab: ChaoxingLoginTab = ChaoxingLoginTab.PASSWORD,
    val accountInput: String = "",
    val passwordInput: String = "",
    val selectedSchool: ChaoxingSchoolOption? = null,
    val schoolKeyword: String = "",
    val schoolSuggestions: List<ChaoxingSchoolOption> = emptyList(),
    val isSearchingSchools: Boolean = false,
    val captchaPromptMessage: String? = null,
    val captchaValidateInput: String = "",
    val isSubmittingLogin: Boolean = false,
    val qrSession: ChaoxingQrLoginSession? = null,
    val qrImage: ImageBytesHolder? = null,
    val qrStatusText: String? = null,
    val importShareInput: String = "",
    val isImportScannerOpen: Boolean = false,
    val activeShareBundle: ChaoxingAccountShareBundle? = null,
    val isDeviceInfoDialogOpen: Boolean = false,
    val hideDeviceCodeBadge: Boolean = false,
    val repairingAccountId: String? = null,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false,
)

enum class ChaoxingActivityFilter {
    ALL,
    SIGN_ONLY,
    IN_PROGRESS_ONLY,
}

data class ChaoxingActivitiesUiState(
    val accountId: String,
    val courseId: Long,
    val clazzId: Long,
    val cpi: Long,
    val courseName: String,
    val activities: List<ChaoxingActivity> = emptyList(),
    val filter: ChaoxingActivityFilter = ChaoxingActivityFilter.SIGN_ONLY,
    val isAutoMonitorEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val lastUpdatedAtMs: Long? = null,
    val errorMessage: String? = null,
)

data class ChaoxingSignExecutionUiState(
    val accountId: String,
    val courseId: Long,
    val clazzId: Long,
    val activeId: Long,
    val detail: ChaoxingActiveDetail? = null,
    val isLoadingDetail: Boolean = true,
    val loadError: String? = null,
    val availableAccounts: List<ChaoxingAccountEntity> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
    val maxSubAccountsQuota: Int = 0,
    val qrRawInput: String = "",
    val parsedQrEnc: String? = null,
    val signCodeInput: String = "",
    val latitudeInput: String = "",
    val longitudeInput: String = "",
    val addressInput: String = "",
    val photo: ImageBytesHolder? = null,
    val captchaValidateInput: String = "",
    val pendingCaptchaId: String? = null,
    val pendingCaptchaMessage: String? = null,
    val isScannerSheetOpen: Boolean = false,
    val isPresetSheetOpen: Boolean = false,
    val isMapPickerOpen: Boolean = false,
    val isLocatingCurrent: Boolean = false,
    val presetLocations: List<ChaoxingPresetLocationEntity> = emptyList(),
    val mapSearchQuery: String = "",
    val isSearchingMapPlaces: Boolean = false,
    val hasCompletedMapSearch: Boolean = false,
    val mapSearchResults: List<ResolvedPlace> = emptyList(),
    val mapJumpCommand: MapJumpCommand? = null,
    val mapReverseGeocodedAddress: String? = null,
    val mapStatusMessage: String? = null,
    val isExecuting: Boolean = false,
    val batchSummary: BatchSignSummary? = null,
    val inputHintMessage: String? = null,
    val hideDeviceCodeBadge: Boolean = false,
    val isDeviceInfoDialogOpen: Boolean = false,
)

data class ChaoxingSignRecordsUiState(
    val records: List<ChaoxingSignRecordEntity> = emptyList(),
    val filterAccountId: String? = null,
)
