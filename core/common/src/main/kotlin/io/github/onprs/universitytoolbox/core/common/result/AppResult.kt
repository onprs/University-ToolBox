package io.github.onprs.universitytoolbox.core.common.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * 通用业务执行结果封装，显式区分成功值与领域错误。
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>

    data class Failure(val error: DomainError) : AppResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): DomainError? = when (this) {
        is Success -> null
        is Failure -> error
    }
}

/**
 * 转换成功结果的数据值，失败分支保持原样透传。
 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

/**
 * 链式组合多个返回 [AppResult] 的操作。
 */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

/**
 * 若当前为成功状态则执行回调并返回自身。
 */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) {
        action(data)
    }
    return this
}

/**
 * 若当前为失败状态则执行回调并返回自身。
 */
inline fun <T> AppResult<T>.onFailure(action: (DomainError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) {
        action(error)
    }
    return this
}

/**
 * 将成功或失败结果折叠为单一输出类型。
 */
inline fun <T, R> AppResult<T>.fold(onSuccess: (T) -> R, onFailure: (DomainError) -> R): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    is AppResult.Failure -> onFailure(error)
}

/**
 * 协程安全的结果捕获工具：自动向上抛出 [CancellationException]，其余异常映射为 [DomainError]。
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> runCatchingApp(
    errorMapper: (Throwable) -> DomainError = { throwable ->
        DomainError.Unknown(
            message = throwable.message ?: "Unexpected error",
            cause = throwable,
        )
    },
    block: () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(errorMapper(throwable))
}
