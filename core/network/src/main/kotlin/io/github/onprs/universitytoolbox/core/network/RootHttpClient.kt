package io.github.onprs.universitytoolbox.core.network

import javax.inject.Qualifier

/**
 * 全局共享根 [okhttp3.OkHttpClient] 注入限定符。
 * 各业务模块应注入此客户端并通过 `newBuilder()` 派生独立配置的隔离实例，共享底层连接池。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RootHttpClient
