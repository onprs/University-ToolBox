package io.github.onprs.universitytoolbox.core.common.dispatcher

import javax.inject.Qualifier

/**
 * 用于注入 I/O 密集型协程调度器的限定符。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * 用于注入 CPU 计算密集型协程调度器的限定符。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * 用于注入主线程协程调度器的限定符。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * 用于注入应用级长生命周期协程作用域的限定符。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
