package io.github.onprs.universitytoolbox.core.license.stub

import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason

/**
 * 开发调试控制器：仅存在于开源 `:core:license-stub` 中，方便开发者与 UI 测试切换各类授权或锁定态。
 */
interface StubLicenseDebugController {
    /** 强制将引擎切换至指定锁定状态，便于验证未激活、过期、回拨、吊销等 UI 表现。 */
    suspend fun simulateLock(reason: LockReason)

    /** 模拟一个带有有限有效期的测试激活状态。 */
    suspend fun simulateTimedUnlock(
        tier: LicenseTier,
        expiresAtEpochSec: Long,
        maxSubAccounts: Int = DEFAULT_SIMULATED_SUB_ACCOUNTS,
        grantedScopes: Set<String> = setOf("*"),
    )

    companion object {
        const val DEFAULT_SIMULATED_SUB_ACCOUNTS = 3
    }
}
