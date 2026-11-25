package io.github.onprs.universitytoolbox.core.security.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.security.AeadCryptoEngine
import io.github.onprs.universitytoolbox.core.security.TinkAeadCryptoEngine
import javax.inject.Singleton

/**
 * 安全基础设施 Hilt 装配模块，提供基于 Android Keystore 的默认 Tink AEAD 引擎。
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideAeadCryptoEngine(@ApplicationContext context: Context): AeadCryptoEngine =
        TinkAeadCryptoEngine.createAndroidKeystore(context)
}
