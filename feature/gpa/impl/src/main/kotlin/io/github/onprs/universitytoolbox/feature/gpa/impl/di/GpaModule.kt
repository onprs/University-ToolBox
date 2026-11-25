package io.github.onprs.universitytoolbox.feature.gpa.impl.di

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
import io.github.onprs.universitytoolbox.feature.gpa.api.GPA_TOOL_ID
import io.github.onprs.universitytoolbox.feature.gpa.api.GpaNavKey
import io.github.onprs.universitytoolbox.feature.gpa.impl.R
import io.github.onprs.universitytoolbox.feature.gpa.impl.ui.GpaRoute

/**
 * GPA 工具模块 Hilt 注册表绑定：向全局工具目录与 Navigation 3 注册路由及描述信息。
 */
@Module
@InstallIn(SingletonComponent::class)
object GpaModule {
    private const val GPA_SORT_ORDER = 10

    @Provides
    @IntoSet
    fun provideGpaToolDescriptor(): ToolDescriptor = ToolDescriptor(
        id = GPA_TOOL_ID,
        titleRes = R.string.feature_gpa_tool_title,
        descriptionRes = R.string.feature_gpa_tool_description,
        category = ToolCategory.ACADEMIC,
        requiredTier = LicenseTier.FREE,
        scopeId = GPA_TOOL_ID,
        entryKey = GpaNavKey,
        searchKeywords = listOf("gpa", "grade", "credit", "pku", "wes", "jidian", "xuefen"),
        iconKey = ToolIconKey.CALCULATOR,
        isPinnedByDefault = true,
        sortOrder = GPA_SORT_ORDER,
    )

    @Provides
    @IntoSet
    fun provideGpaEntryInstaller(): EntryProviderInstaller = {
        entry<GpaNavKey> {
            GpaRoute()
        }
    }
}
