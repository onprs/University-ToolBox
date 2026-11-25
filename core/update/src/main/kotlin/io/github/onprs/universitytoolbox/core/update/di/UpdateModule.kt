package io.github.onprs.universitytoolbox.core.update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.update.AndroidAppUpdateManager
import io.github.onprs.universitytoolbox.core.update.AppUpdateManager
import io.github.onprs.universitytoolbox.core.update.DefaultRemoteGovernanceRepository
import io.github.onprs.universitytoolbox.core.update.RemoteGovernanceRepository
import javax.inject.Singleton

/**
 * 远程治理与安全自更新 Hilt 装配模块。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindRemoteGovernanceRepository(impl: DefaultRemoteGovernanceRepository): RemoteGovernanceRepository

    @Binds
    @Singleton
    abstract fun bindAppUpdateManager(impl: AndroidAppUpdateManager): AppUpdateManager
}
