package io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.ratelimit

import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBucketRateLimiterTest {
    @Test
    fun `token bucket consumes refills over time and computes wait delay`() = runTest {
        var nowMs = 1_000_000L
        val recordedDelays = ArrayList<Long>()
        val limiter = TokenBucketRateLimiter(
            capacity = 2,
            refillTokens = 1,
            refillPeriodMs = 500L,
            timeProvider = TimeProvider { nowMs },
            delayFn = { ms ->
                recordedDelays += ms
                nowMs += ms
            },
        )

        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
        assertEquals(0, limiter.availablePermits())

        // acquire 将触发 500ms 等待并推进假时钟，随即拿到令牌
        limiter.acquire()
        assertEquals(listOf(500L), recordedDelays)

        // 经过 2000ms，令牌应恢复但不得超过容量上限 2
        nowMs += 2_000L
        assertEquals(2, limiter.availablePermits())
    }

    @Test
    fun `rate limit coordinator applies jitter only after first account`() = runTest {
        var nowMs = 5_000L
        val delays = ArrayList<Long>()
        val coordinator = ChaoxingRateLimitCoordinator(
            globalLimiter = TokenBucketRateLimiter(
                capacity = 10,
                refillTokens = 10,
                refillPeriodMs = 1000L,
                timeProvider = TimeProvider { nowMs },
            ),
            jitterStrategy = JitterStrategy { min, max -> (min + max) / 2 },
            delayFn = { ms ->
                delays += ms
                nowMs += ms
            },
        )

        coordinator.awaitAccountTurn(accountIndex = 0, minJitterMs = 400L, maxJitterMs = 800L)
        coordinator.awaitAccountTurn(accountIndex = 1, minJitterMs = 400L, maxJitterMs = 800L)
        coordinator.awaitAccountTurn(accountIndex = 2, minJitterMs = 400L, maxJitterMs = 800L)

        assertEquals(listOf(600L, 600L), delays)
    }
}
