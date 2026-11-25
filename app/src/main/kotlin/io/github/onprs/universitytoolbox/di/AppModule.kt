package io.github.onprs.universitytoolbox.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.onprs.universitytoolbox.core.tool.api.DefaultToolRegistry
import io.github.onprs.universitytoolbox.core.tool.api.EntryProviderInstaller
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.core.tool.api.ToolRegistry
import javax.inject.Singleton

/**
 * 宿主应用级 Hilt 装配模块：绑定默认工具注册表并声明多模块集合的多重绑定入口。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindToolRegistry(impl: DefaultToolRegistry): ToolRegistry

    @Multibinds
    abstract fun bindToolDescriptors(): Set<ToolDescriptor>

    @Multibinds
    abstract fun bindEntryProviderInstallers(): Set<EntryProviderInstaller>
}
