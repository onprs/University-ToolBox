package io.github.onprs.universitytoolbox.core.datastore.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.common.dispatcher.ApplicationScope
import io.github.onprs.universitytoolbox.core.common.dispatcher.DefaultDispatcher
import io.github.onprs.universitytoolbox.core.common.dispatcher.DefaultDispatcherProvider
import io.github.onprs.universitytoolbox.core.common.dispatcher.DispatcherProvider
import io.github.onprs.universitytoolbox.core.common.dispatcher.IoDispatcher
import io.github.onprs.universitytoolbox.core.common.dispatcher.MainDispatcher
import io.github.onprs.universitytoolbox.core.common.time.TimeProvider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 全局唯一的协程调度器、应用级协程作用域与时间源 Hilt 绑定模块。
 * 其他模块必须直接注入这些公共限定符与契约，严禁重复提供绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreBindingsModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@DefaultDispatcher defaultDispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + defaultDispatcher)

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider.System
}
