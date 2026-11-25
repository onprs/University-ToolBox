package io.github.onprs.universitytoolbox.core.network.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.network.RedactingLoggingInterceptor
import io.github.onprs.universitytoolbox.core.network.RootHttpClient
import io.github.onprs.universitytoolbox.core.network.TrustedNetworkTimeInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * 网络基础设施 Hilt 装配模块：提供共享连接池的全局根 [OkHttpClient] 与宽容序列化配置 [Json]。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val LOG_TAG = "ToolboxHttp"
    private const val MAX_IDLE_CONNECTIONS = 10
    private const val KEEP_ALIVE_MINUTES = 5L
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val WRITE_TIMEOUT_SECONDS = 20L

    @Provides
    @Singleton
    fun provideConnectionPool(): ConnectionPool = ConnectionPool(
        maxIdleConnections = MAX_IDLE_CONNECTIONS,
        keepAliveDuration = KEEP_ALIVE_MINUTES,
        timeUnit = TimeUnit.MINUTES,
    )

    @Provides
    @Singleton
    fun provideRedactingLoggingInterceptor(): RedactingLoggingInterceptor = RedactingLoggingInterceptor(
        level = RedactingLoggingInterceptor.Level.BASIC,
        logger = { message -> Log.d(LOG_TAG, message) },
    )

    @Provides
    @Singleton
    @RootHttpClient
    fun provideRootOkHttpClient(
        connectionPool: ConnectionPool,
        trustedNetworkTimeInterceptor: TrustedNetworkTimeInterceptor,
        redactingLoggingInterceptor: RedactingLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addNetworkInterceptor(trustedNetworkTimeInterceptor)
        .addInterceptor(redactingLoggingInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideLenientJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }
}
