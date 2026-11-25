package io.github.onprs.universitytoolbox.core.license.stub

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.license.api.LicenseEngine
import io.github.onprs.universitytoolbox.core.license.api.LicenseStorage
import javax.inject.Singleton

/**
 * 开源调试授权桩的 Hilt 绑定模块，仅在未挂载私有密钥仓库的构建变体中装配。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StubLicenseModule {
    @BindsOptionalOf
    abstract fun optionalLicenseStorage(): LicenseStorage

    @Binds
    @Singleton
    abstract fun bindDeviceIdentityProvider(impl: AndroidDeviceIdentityProvider): DeviceIdentityProvider

    @Binds
    @Singleton
    abstract fun bindLicenseEngine(impl: StubLicenseEngine): LicenseEngine

    @Binds
    @Singleton
    abstract fun bindStubDebugController(impl: StubLicenseEngine): StubLicenseDebugController
}
