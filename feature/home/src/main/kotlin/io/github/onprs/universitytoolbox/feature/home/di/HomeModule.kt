package io.github.onprs.universitytoolbox.feature.home.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.feature.home.domain.AndroidToolTextResolver
import io.github.onprs.universitytoolbox.feature.home.domain.ToolTextResolver
import io.github.onprs.universitytoolbox.feature.home.shortcut.AndroidToolShortcutPublisher
import io.github.onprs.universitytoolbox.feature.home.shortcut.ToolShortcutPublisher
import javax.inject.Singleton

/**
 * 首页模块 Hilt 绑定配置。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindToolTextResolver(impl: AndroidToolTextResolver): ToolTextResolver

    @Binds
    @Singleton
    abstract fun bindToolShortcutPublisher(impl: AndroidToolShortcutPublisher): ToolShortcutPublisher
}
