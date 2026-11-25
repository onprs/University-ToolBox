package io.github.onprs.universitytoolbox.core.common.result

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {
    @Test
    fun `success result supports map flatMap and fold`() {
        val initial: AppResult<Int> = AppResult.Success(21)
        val mapped = initial.map { it * 2 }
        val chained = mapped.flatMap { value -> AppResult.Success(value.toString()) }

        assertTrue(chained.isSuccess)
        assertFalse(chained.isFailure)
        assertEquals("42", chained.getOrNull())
        assertNull(chained.errorOrNull())

        val folded = chained.fold(
            onSuccess = { "ok:$it" },
            onFailure = { "err:${it.message}" },
        )
        assertEquals("ok:42", folded)
    }

    @Test
    fun `failure result short-circuits transformations`() {
        val error = DomainError.Unauthorized("session expired")
        val initial: AppResult<Int> = AppResult.Failure(error)
        var transformInvoked = false
        val mapped = initial.map {
            transformInvoked = true
            it + 1
        }

        assertFalse(transformInvoked)
        assertTrue(mapped.isFailure)
        assertNull(mapped.getOrNull())
        assertEquals(error, mapped.errorOrNull())
    }

    @Test
    fun `runCatchingApp captures regular exceptions as failure`() {
        val result = runCatchingApp(
            errorMapper = { DomainError.Network(message = it.message ?: "net", httpCode = 503, cause = it) },
        ) {
            error("service unavailable")
        }

        assertTrue(result.isFailure)
        val domainError = result.errorOrNull() as DomainError.Network
        assertEquals(503, domainError.httpCode)
        assertEquals("service unavailable", domainError.message)
    }

    @Test(expected = CancellationException::class)
    fun `runCatchingApp rethrows CancellationException without swallowing`() {
        runCatchingApp {
            throw CancellationException("cancelled by caller")
        }
    }
}
