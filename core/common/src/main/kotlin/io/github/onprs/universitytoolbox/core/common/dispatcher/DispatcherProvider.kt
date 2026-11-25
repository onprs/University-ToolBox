package io.github.onprs.universitytoolbox.core.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 纯 Kotlin 协程调度器抽象，便于无 Android 依赖的单元测试替换调度器。
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
    val mainImmediate: CoroutineDispatcher
}

/**
 * 默认调度器实现，直接委托给 Kotlin 标准 [Dispatchers]。
 */
object DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher
        get() = Dispatchers.IO

    override val default: CoroutineDispatcher
        get() = Dispatchers.Default

    override val main: CoroutineDispatcher
        get() = Dispatchers.Main

    override val mainImmediate: CoroutineDispatcher
        get() = Dispatchers.Main.immediate
}
