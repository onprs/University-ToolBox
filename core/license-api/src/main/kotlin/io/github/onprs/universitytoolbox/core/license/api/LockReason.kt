package io.github.onprs.universitytoolbox.core.license.api

/**
 * 授权处于锁定状态的具体原因枚举。
 */
enum class LockReason {
    NOT_ACTIVATED,
    EXPIRED,
    CLOCK_ROLLBACK_DETECTED,
    REVOKED,
    INVALID_SIGNATURE,
}
