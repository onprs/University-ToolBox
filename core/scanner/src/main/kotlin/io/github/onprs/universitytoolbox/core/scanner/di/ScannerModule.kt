package io.github.onprs.universitytoolbox.core.scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.scanner.BarcodeScanner
import io.github.onprs.universitytoolbox.core.scanner.DecoderBackend
import io.github.onprs.universitytoolbox.core.scanner.DefaultBarcodeScanner
import io.github.onprs.universitytoolbox.core.scanner.MlKitDecoderBackend
import javax.inject.Singleton

/**
 * 扫码基础设施 Hilt 装配模块。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {
    @Binds
    @Singleton
    abstract fun bindDecoderBackend(impl: MlKitDecoderBackend): DecoderBackend

    @Binds
    @Singleton
    abstract fun bindBarcodeScanner(impl: DefaultBarcodeScanner): BarcodeScanner
}
