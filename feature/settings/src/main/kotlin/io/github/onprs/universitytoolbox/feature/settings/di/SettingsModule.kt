package io.github.onprs.universitytoolbox.feature.settings.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.feature.settings.platform.AndroidAppCacheController
import io.github.onprs.universitytoolbox.feature.settings.platform.AndroidAppLocaleController
import io.github.onprs.universitytoolbox.feature.settings.platform.AndroidAppVersionInfoProvider
import io.github.onprs.universitytoolbox.feature.settings.platform.AndroidPermissionStatusProvider
import io.github.onprs.universitytoolbox.feature.settings.platform.AppCacheController
import io.github.onprs.universitytoolbox.feature.settings.platform.AppLocaleController
import io.github.onprs.universitytoolbox.feature.settings.platform.AppVersionInfoProvider
import io.github.onprs.universitytoolbox.feature.settings.platform.PermissionStatusProvider
import javax.inject.Singleton

/**
 * 设置模块平台能力 Hilt 绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindAppVersionInfoProvider(impl: AndroidAppVersionInfoProvider): AppVersionInfoProvider

    @Binds
    @Singleton
    abstract fun bindPermissionStatusProvider(impl: AndroidPermissionStatusProvider): PermissionStatusProvider

    @Binds
    @Singleton
    abstract fun bindAppLocaleController(impl: AndroidAppLocaleController): AppLocaleController

    @Binds
    @Singleton
    abstract fun bindAppCacheController(impl: AndroidAppCacheController): AppCacheController
}
