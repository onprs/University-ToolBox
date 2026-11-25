package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit

import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 令牌桶限流器，支持按固定周期补充令牌并在超限时以协程挂起方式平滑流控。
 */
class TokenBucketRateLimiter(
    private val capacity: Int,
    private val refillTokens: Int,
    private val refillPeriodMs: Long,
    private val timeProvider: TimeProvider = TimeProvider.System,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    init {
        require(capacity > 0) { "Capacity must be positive" }
        require(refillTokens > 0) { "Refill tokens must be positive" }
        require(refillPeriodMs > 0L) { "Refill period must be positive" }
    }

    private val mutex = Mutex()
    private var availableTokens: Double = capacity.toDouble()
    private var lastRefillTimeMs: Long = timeProvider.currentTimeMillis()

    /** 非阻塞尝试获取指定数量的令牌，成功返回 true，令牌不足返回 false。 */
    suspend fun tryAcquire(permits: Int = 1): Boolean {
        require(permits in 1..capacity) { "Permits must be within 1..$capacity" }
        return mutex.withLock {
            refillLocked(timeProvider.currentTimeMillis())
            if (availableTokens >= permits) {
                availableTokens -= permits
                true
            } else {
                false
            }
        }
    }

    /** 挂起直至获取指定数量的令牌。 */
    suspend fun acquire(permits: Int = 1) {
        require(permits in 1..capacity) { "Permits must be within 1..$capacity" }
        while (true) {
            val waitMs = mutex.withLock {
                val now = timeProvider.currentTimeMillis()
                refillLocked(now)
                if (availableTokens >= permits) {
                    availableTokens -= permits
                    return@withLock 0L
                }
                val deficit = permits - availableTokens
                val tokensPerMs = refillTokens.toDouble() / refillPeriodMs.toDouble()
                ceil(deficit / tokensPerMs).toLong().coerceAtLeast(1L)
            }
            if (waitMs <= 0L) {
                return
            }
            delayFn(waitMs)
        }
    }

    /** 查询当前可用整数令牌数（仅供状态监测与单测验证）。 */
    suspend fun availablePermits(): Int = mutex.withLock {
        refillLocked(timeProvider.currentTimeMillis())
        availableTokens.toInt()
    }

    private fun refillLocked(nowMs: Long) {
        if (nowMs <= lastRefillTimeMs) {
            return
        }
        val elapsedMs = nowMs - lastRefillTimeMs
        val added = (elapsedMs.toDouble() / refillPeriodMs.toDouble()) * refillTokens.toDouble()
        if (added > 0.0) {
            availableTokens = (availableTokens + added).coerceAtMost(capacity.toDouble())
            lastRefillTimeMs = nowMs
        }
    }
}

/**
 * 随机抖动延迟策略，用于在多账号批量签到间引入随机化间隔以降低风控概率。
 */
fun interface JitterStrategy {
    fun nextDelayMs(minDelayMs: Long, maxDelayMs: Long): Long

    companion object {
        /** 默认基于 [Random.Default] 的随机抖动策略。 */
        val Default: JitterStrategy = JitterStrategy { minDelayMs, maxDelayMs ->
            if (maxDelayMs <= minDelayMs) {
                minDelayMs.coerceAtLeast(0L)
            } else {
                Random.nextLong(minDelayMs.coerceAtLeast(0L), maxDelayMs + 1L)
            }
        }
    }
}

/**
 * 学习通请求限流与多账号随机抖动统一调度器。
 */
@Singleton
class ChaoxingRateLimitCoordinator(
    private val globalLimiter: TokenBucketRateLimiter,
    private val jitterStrategy: JitterStrategy = JitterStrategy.Default,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    @Inject
    constructor(timeProvider: TimeProvider) : this(
        globalLimiter = TokenBucketRateLimiter(
            capacity = DEFAULT_BUCKET_CAPACITY,
            refillTokens = DEFAULT_REFILL_TOKENS,
            refillPeriodMs = DEFAULT_REFILL_PERIOD_MS,
            timeProvider = timeProvider,
        ),
    )

    /** 在执行单次请求前获取限流令牌。 */
    suspend fun acquirePermit() {
        globalLimiter.acquire(1)
    }

    /** 在多账号队列执行间注入随机抖动延迟并获取限流令牌。 */
    suspend fun awaitAccountTurn(
        accountIndex: Int,
        minJitterMs: Long = DEFAULT_MIN_JITTER_MS,
        maxJitterMs: Long = DEFAULT_MAX_JITTER_MS,
    ) {
        if (accountIndex > 0 && maxJitterMs > 0L) {
            val jitter = jitterStrategy.nextDelayMs(minJitterMs, maxJitterMs)
            if (jitter > 0L) {
                delayFn(jitter)
            }
        }
        globalLimiter.acquire(1)
    }

    companion object {
        private const val DEFAULT_BUCKET_CAPACITY = 5
        private const val DEFAULT_REFILL_TOKENS = 2
        private const val DEFAULT_REFILL_PERIOD_MS = 1000L
        private const val DEFAULT_MIN_JITTER_MS = 650L
        private const val DEFAULT_MAX_JITTER_MS = 1850L
    }
}
