package io.github.onprs.universitytoolbox.core.license.api

/**
 * 应用全局授权状态机模型。
 */
sealed interface LicenseState {
    /** 冷启动或异步校验尚未完成时的过渡态。 */
    data object Initializing : LicenseState

    /** 未激活或校验受阻时的锁定态，向界面提供锁定原因与当前设备质询码。 */
    data class Locked(val reason: LockReason, val deviceChallenge: String) : LicenseState

    /** 已激活且通过校验的解锁态。 */
    data class Unlocked(val entitlement: LicenseEntitlement) : LicenseState
}
