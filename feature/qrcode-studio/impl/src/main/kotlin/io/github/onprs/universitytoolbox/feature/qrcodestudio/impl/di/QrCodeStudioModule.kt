package io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.tool.api.EntryProviderInstaller
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.core.tool.api.ToolIconKey
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QR_CODE_STUDIO_TOOL_ID
import io.github.onprs.universitytoolbox.feature.qrcodestudio.api.QrCodeStudioNavKey
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.R
import io.github.onprs.universitytoolbox.feature.qrcodestudio.impl.ui.QrCodeStudioRoute

/**
 * 条码检视工作台模块 Hilt 绑定，注册 STANDARD 授权门禁描述符与 Navigation 3 路由。
 */
@Module
@InstallIn(SingletonComponent::class)
object QrCodeStudioModule {
    private const val QR_STUDIO_SORT_ORDER = 20

    @Provides
    @IntoSet
    fun provideQrCodeStudioToolDescriptor(): ToolDescriptor = ToolDescriptor(
        id = QR_CODE_STUDIO_TOOL_ID,
        titleRes = R.string.feature_qrcodestudio_tool_title,
        descriptionRes = R.string.feature_qrcodestudio_tool_description,
        category = ToolCategory.MEDIA_UTILITY,
        requiredTier = LicenseTier.STANDARD,
        scopeId = QR_CODE_STUDIO_TOOL_ID,
        entryKey = QrCodeStudioNavKey,
        searchKeywords = listOf("qrcode", "barcode", "scanner", "inspect", "wifi", "erweima", "saoma"),
        iconKey = ToolIconKey.QR_CODE,
        isPinnedByDefault = false,
        sortOrder = QR_STUDIO_SORT_ORDER,
    )

    @Provides
    @IntoSet
    fun provideQrCodeStudioEntryInstaller(): EntryProviderInstaller = {
        entry<QrCodeStudioNavKey> {
            QrCodeStudioRoute()
        }
    }
}
