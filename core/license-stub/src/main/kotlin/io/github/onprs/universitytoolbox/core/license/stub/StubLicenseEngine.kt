package io.github.onprs.universitytoolbox.core.license.stub

import io.github.onprs.universitytoolbox.core.common.dispatcher.DefaultDispatcherProvider
import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseStorage
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.license.api.LockReason
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 仅限开发与公开 CI 调试使用的开源授权桩实现（`isOfficialEngine = false`）。
 * 支持固定开发口令解锁、状态切换以及时钟回拨/过期/吊销模拟，不包含任何正式密钥算法。
 */
@Singleton
class StubLicenseEngine internal constructor(
    private val deviceIdentityProvider: DeviceIdentityProvider,
    private val licenseStorage: LicenseStorage,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
    autoInitialize: Boolean,
) : LicenseEngine,
    StubLicenseDebugController {

    @Inject
    constructor(
        deviceIdentityProvider: DeviceIdentityProvider,
        optionalStorage: Optional<LicenseStorage>,
    ) : this(
        deviceIdentityProvider = deviceIdentityProvider,
        licenseStorage = optionalStorage.orElseGet(::InMemoryLicenseStorage),
        timeProvider = TimeProvider.System,
        dispatchers = DefaultDispatcherProvider,
        autoInitialize = true,
    )

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<LicenseState>(LicenseState.Initializing)
    override val state: StateFlow<LicenseState> = mutableState.asStateFlow()

    private var cachedChallenge: String? = null
    private var initialized: Boolean = false
    private var activeEntitlement: LicenseEntitlement? = null

    init {
        if (autoInitialize) {
            CoroutineScope(SupervisorJob() + dispatchers.io).launch {
                ensureInitialized()
            }
        }
    }

    /** 确保引擎已完成初始化与持久化状态还原；测试可显式调用或依赖首次挂起操作触发。 */
    suspend fun ensureInitialized() {
        mutex.withLock {
            ensureInitializedLocked()
        }
    }

    override suspend fun getDeviceChallenge(): String = mutex.withLock {
        ensureInitializedLocked()
        resolveChallengeLocked()
    }

    override suspend fun activate(rawKeyInput: String): Result<LicenseEntitlement> = mutex.withLock {
        ensureInitializedLocked()
        val challenge = resolveChallengeLocked()
        val normalized = rawKeyInput.trim().uppercase()
        val preset = DEV_KEY_PRESETS[normalized]
        if (preset == null) {
            activeEntitlement = null
            mutableState.value = LicenseState.Locked(
                reason = LockReason.INVALID_SIGNATURE,
                deviceChallenge = challenge,
            )
            return@withLock Result.failure(IllegalArgumentException("Unrecognized stub activation key"))
        }

        val entitlement = preset.toEntitlement()
        val revokedIds = licenseStorage.loadRevokedSerialIds()
        if (entitlement.serialId in revokedIds) {
            activeEntitlement = null
            mutableState.value = LicenseState.Locked(
                reason = LockReason.REVOKED,
                deviceChallenge = challenge,
            )
            return@withLock Result.failure(IllegalStateException("Stub serial has been revoked"))
        }

        licenseStorage.saveActivatedKey(normalized)
        activeEntitlement = entitlement
        evaluateAndPublishStateLocked()
        val currentState = mutableState.value
        if (currentState is LicenseState.Unlocked) {
            Result.success(currentState.entitlement)
        } else {
            val lockedReason = (currentState as? LicenseState.Locked)?.reason ?: LockReason.INVALID_SIGNATURE
            Result.failure(IllegalStateException("Stub license locked: $lockedReason"))
        }
    }

    override suspend fun syncTrustedNetworkTime(serverEpochSec: Long) {
        mutex.withLock {
            ensureInitializedLocked()
            val storedHighWater = licenseStorage.loadClockHighWaterMarkEpochSec()
            val updatedHighWater = maxOf(storedHighWater ?: serverEpochSec, serverEpochSec)
            licenseStorage.saveClockHighWaterMarkEpochSec(updatedHighWater)
            evaluateAndPublishStateLocked(referenceEpochSec = serverEpochSec)
        }
    }

    override suspend fun updateRevokedList(revokedIds: Set<String>) {
        mutex.withLock {
            ensureInitializedLocked()
            licenseStorage.saveRevokedSerialIds(revokedIds)
            evaluateAndPublishStateLocked()
        }
    }

    override suspend fun clearLicense() {
        mutex.withLock {
            ensureInitializedLocked()
            activeEntitlement = null
            licenseStorage.clearActivatedKey()
            mutableState.value = LicenseState.Locked(
                reason = LockReason.NOT_ACTIVATED,
                deviceChallenge = resolveChallengeLocked(),
            )
        }
    }

    override suspend fun simulateLock(reason: LockReason) {
        mutex.withLock {
            ensureInitializedLocked()
            val challenge = resolveChallengeLocked()
            if (reason == LockReason.NOT_ACTIVATED) {
                activeEntitlement = null
                licenseStorage.clearActivatedKey()
            }
            mutableState.value = LicenseState.Locked(
                reason = reason,
                deviceChallenge = challenge,
            )
        }
    }

    override suspend fun simulateTimedUnlock(
        tier: LicenseTier,
        expiresAtEpochSec: Long,
        maxSubAccounts: Int,
        grantedScopes: Set<String>,
    ) {
        mutex.withLock {
            ensureInitializedLocked()
            val entitlement = LicenseEntitlement(
                serialId = "STUB-TIMED-${tier.name}",
                tier = tier,
                grantedScopes = grantedScopes,
                maxSubAccounts = maxSubAccounts.coerceAtLeast(0),
                expiresAtEpochSec = expiresAtEpochSec,
                isOfficialEngine = false,
            )
            activeEntitlement = entitlement
            evaluateAndPublishStateLocked()
        }
    }

    private suspend fun ensureInitializedLocked() {
        if (initialized) {
            return
        }
        resolveChallengeLocked()
        val persistedKey = licenseStorage.loadActivatedKey()
        if (persistedKey != null) {
            activeEntitlement = DEV_KEY_PRESETS[persistedKey.trim().uppercase()]?.toEntitlement()
        }
        initialized = true
        evaluateAndPublishStateLocked()
    }

    private suspend fun resolveChallengeLocked(): String {
        cachedChallenge?.let { return it }
        val resolved = withContext(dispatchers.io) {
            deviceIdentityProvider.getStubDeviceChallenge()
        }
        cachedChallenge = resolved
        return resolved
    }

    private suspend fun evaluateAndPublishStateLocked(referenceEpochSec: Long = timeProvider.currentEpochSeconds()) {
        val challenge = resolveChallengeLocked()
        val highWaterMark = licenseStorage.loadClockHighWaterMarkEpochSec()
        val entitlement = activeEntitlement
        if (entitlement == null) {
            mutableState.value = LicenseState.Locked(LockReason.NOT_ACTIVATED, challenge)
            return
        }

        val revokedIds = licenseStorage.loadRevokedSerialIds()
        val lockReason: LockReason? = when {
            entitlement.serialId in revokedIds -> LockReason.REVOKED
            highWaterMark != null &&
                highWaterMark - referenceEpochSec > CLOCK_ROLLBACK_TOLERANCE_SEC ->
                LockReason.CLOCK_ROLLBACK_DETECTED
            entitlement.isExpiredAt(referenceEpochSec) -> LockReason.EXPIRED
            else -> null
        }

        if (lockReason != null) {
            mutableState.value = LicenseState.Locked(lockReason, challenge)
            return
        }

        val nextWaterMark = maxOf(highWaterMark ?: referenceEpochSec, referenceEpochSec)
        licenseStorage.saveClockHighWaterMarkEpochSec(nextWaterMark)
        mutableState.value = LicenseState.Unlocked(entitlement)
    }

    private data class StubPreset(val serialId: String, val tier: LicenseTier, val maxSubAccounts: Int) {
        fun toEntitlement(): LicenseEntitlement = LicenseEntitlement(
            serialId = serialId,
            tier = tier,
            grantedScopes = setOf(LicenseEntitlement.WILDCARD_SCOPE),
            maxSubAccounts = maxSubAccounts,
            expiresAtEpochSec = null,
            isOfficialEngine = false,
        )
    }

    companion object {
        const val KEY_FREE = "DEV-FREE-UNLOCK"
        const val KEY_STANDARD = "DEV-STD-UNLOCK"
        const val KEY_PRO = "DEV-PRO-UNLOCK"
        const val KEY_FULL = "DEV-FULL-UNLOCK"

        private const val CLOCK_ROLLBACK_TOLERANCE_SEC = 300L
        private const val QUOTA_FREE = 1
        private const val QUOTA_STANDARD = 3
        private const val QUOTA_PRO = 5
        private const val QUOTA_FULL = 10

        private val DEV_KEY_PRESETS = mapOf(
            KEY_FREE to StubPreset(
                serialId = "STUB-FREE-0001",
                tier = LicenseTier.FREE,
                maxSubAccounts = QUOTA_FREE,
            ),
            KEY_STANDARD to StubPreset(
                serialId = "STUB-STD-0001",
                tier = LicenseTier.STANDARD,
                maxSubAccounts = QUOTA_STANDARD,
            ),
            KEY_PRO to StubPreset(
                serialId = "STUB-PRO-0001",
                tier = LicenseTier.PRO,
                maxSubAccounts = QUOTA_PRO,
            ),
            KEY_FULL to StubPreset(
                serialId = "STUB-FULL-0001",
                tier = LicenseTier.FULL,
                maxSubAccounts = QUOTA_FULL,
            ),
        )
    }
}
