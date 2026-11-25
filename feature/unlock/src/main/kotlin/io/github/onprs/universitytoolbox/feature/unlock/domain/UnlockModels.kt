package io.github.onprs.universitytoolbox.feature.unlock.domain

import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LockReason

/**
 * 授权激活界面操作结果反馈分类。
 */
enum class UnlockFeedbackKind {
    CHALLENGE_COPIED,
    ACTIVATION_SUCCEEDED,
    ACTIVATION_FAILED_EMPTY,
    ACTIVATION_FAILED_INVALID,
    LICENSE_CLEARED,
    SCAN_CANCELLED,
    SCAN_FAILED,
}

/**
 * 授权引导与详情管理页面状态模型。
 */
data class UnlockUiState(
    val isInitializing: Boolean = true,
    val isSubmitting: Boolean = false,
    val isClearing: Boolean = false,
    val isScannerOpen: Boolean = false,
    val torchEnabled: Boolean = false,
    val isRebinding: Boolean = false,
    val isClearConfirmOpen: Boolean = false,
    val deviceChallenge: String = "",
    val keyInput: String = "",
    val lockReason: LockReason? = null,
    val entitlement: LicenseEntitlement? = null,
    val feedbackKind: UnlockFeedbackKind? = null,
    val errorDetail: String? = null,
    val manageMode: Boolean = false,
) {
    val isUnlocked: Boolean
        get() = entitlement != null && lockReason == null
}
