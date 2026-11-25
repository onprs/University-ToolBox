package io.github.onprs.universitytoolbox.feature.chaoxing.impl.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.network.RootHttpClient
import io.github.onprs.universitytoolbox.core.tool.api.EntryProviderInstaller
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.tool.api.ToolDescriptor
import io.github.onprs.universitytoolbox.core.tool.api.ToolIconKey
import io.github.onprs.universitytoolbox.feature.chaoxing.api.CHAOXING_TOOL_ID
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.R
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAuditSink
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCourseDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCredentialCipher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingDatabase
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingDeviceSettingsRepository
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingPreferenceDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingPresetLocationDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingSignRecordDao
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.DatabaseChaoxingAuditSink
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.RoomChaoxingDeviceSettingsRepository
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.TinkChaoxingCredentialCipher
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingEndpointConfig
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote.ChaoxingHttpClient
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountCookieStorage
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.AccountSessionInterceptor
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.session.EncryptedAccountCookieStorage
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountSessionBridge
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.AndroidChaoxingLocationService
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.location.ChaoxingLocationService
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.CodeAndGestureSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.LocationSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.NormalSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.PhotoSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.QrCodeSignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.sign.SignActionHandler
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.installChaoxingEntries
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 学习通业务模块 Hilt 依赖装配与工具自注册模块。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChaoxingModule {
    @Binds
    @Singleton
    abstract fun bindAccountCookieStorage(impl: EncryptedAccountCookieStorage): AccountCookieStorage

    @Binds
    @Singleton
    abstract fun bindCredentialCipher(impl: TinkChaoxingCredentialCipher): ChaoxingCredentialCipher

    @Binds
    @Singleton
    abstract fun bindAuditSink(impl: DatabaseChaoxingAuditSink): ChaoxingAuditSink

    @Binds
    @Singleton
    abstract fun bindLocationService(impl: AndroidChaoxingLocationService): ChaoxingLocationService

    @Binds
    @Singleton
    abstract fun bindDeviceSettingsRepository(
        impl: RoomChaoxingDeviceSettingsRepository,
    ): ChaoxingDeviceSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAccountSessionBridge(impl: ChaoxingAccountManager): ChaoxingAccountSessionBridge

    @Binds
    @IntoSet
    abstract fun bindNormalSignHandler(impl: NormalSignActionHandler): SignActionHandler

    @Binds
    @IntoSet
    abstract fun bindPhotoSignHandler(impl: PhotoSignActionHandler): SignActionHandler

    @Binds
    @IntoSet
    abstract fun bindQrCodeSignHandler(impl: QrCodeSignActionHandler): SignActionHandler

    @Binds
    @IntoSet
    abstract fun bindLocationSignHandler(impl: LocationSignActionHandler): SignActionHandler

    @Binds
    @IntoSet
    abstract fun bindCodeAndGestureSignHandler(impl: CodeAndGestureSignActionHandler): SignActionHandler

    companion object {
        @Provides
        @Singleton
        fun provideChaoxingEndpointConfig(): ChaoxingEndpointConfig = ChaoxingEndpointConfig()

        @Provides
        @Singleton
        @ChaoxingHttpClient
        fun provideChaoxingOkHttpClient(
            @RootHttpClient rootHttpClient: OkHttpClient,
            accountSessionInterceptor: AccountSessionInterceptor,
        ): OkHttpClient = rootHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(accountSessionInterceptor)
            .build()

        @Provides
        @Singleton
        fun provideChaoxingDatabase(@ApplicationContext context: Context): ChaoxingDatabase = Room.databaseBuilder(
            context = context,
            klass = ChaoxingDatabase::class.java,
            name = ChaoxingDatabase.DATABASE_NAME,
        )
            .addMigrations(ChaoxingDatabase.MIGRATION_1_2)
            .build()

        @Provides
        fun provideChaoxingAccountDao(database: ChaoxingDatabase): ChaoxingAccountDao = database.accountDao()

        @Provides
        fun provideChaoxingCourseDao(database: ChaoxingDatabase): ChaoxingCourseDao = database.courseDao()

        @Provides
        fun provideChaoxingSignRecordDao(database: ChaoxingDatabase): ChaoxingSignRecordDao = database.signRecordDao()

        @Provides
        fun provideChaoxingPresetLocationDao(database: ChaoxingDatabase): ChaoxingPresetLocationDao =
            database.presetLocationDao()

        @Provides
        fun provideChaoxingPreferenceDao(database: ChaoxingDatabase): ChaoxingPreferenceDao = database.preferenceDao()

        @Provides
        @IntoSet
        fun provideChaoxingToolDescriptor(): ToolDescriptor = ToolDescriptor(
            id = CHAOXING_TOOL_ID,
            titleRes = R.string.chaoxing_tool_title,
            descriptionRes = R.string.chaoxing_tool_description,
            category = ToolCategory.ACADEMIC,
            requiredTier = LicenseTier.STANDARD,
            scopeId = CHAOXING_TOOL_ID,
            entryKey = ChaoxingNavKey.Dashboard,
            searchKeywords = listOf(
                "学习通",
                "超星",
                "签到",
                "签退",
                "课程",
                "chaoxing",
                "xuexitong",
                "attendance",
            ),
            iconKey = ToolIconKey.ATTENDANCE,
            isPinnedByDefault = true,
            sortOrder = 1,
        )

        @Provides
        @IntoSet
        fun provideChaoxingEntryInstaller(): EntryProviderInstaller = {
            installChaoxingEntries()
        }
    }
}
