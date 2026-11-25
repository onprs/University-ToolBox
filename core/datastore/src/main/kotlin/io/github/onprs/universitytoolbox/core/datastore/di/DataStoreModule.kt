package io.github.onprs.universitytoolbox.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.common.dispatcher.ApplicationScope
import io.github.onprs.universitytoolbox.core.datastore.DataStoreUserPreferencesRepository
import io.github.onprs.universitytoolbox.core.datastore.EncryptedLicenseStorage
import io.github.onprs.universitytoolbox.core.datastore.UserPreferencesRepository
import io.github.onprs.universitytoolbox.core.license.api.LicenseStorage
import java.io.File
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * 持久化存储 Hilt 装配模块：唯一绑定 [LicenseStorage] 与 [UserPreferencesRepository]。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds
    @Singleton
    abstract fun bindLicenseStorage(impl: EncryptedLicenseStorage): LicenseStorage

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository

    companion object {
        private const val PREFERENCES_FILE_NAME = "user_preferences.preferences_pb"

        @Provides
        @Singleton
        @Named(DataStoreUserPreferencesRepository.PREFERENCES_DATASTORE_NAME)
        fun provideUserPreferencesDataStore(
            @ApplicationContext context: Context,
            @ApplicationScope applicationScope: CoroutineScope,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            migrations = emptyList(),
            scope = applicationScope,
            produceFile = {
                File(context.applicationContext.filesDir, "datastore/$PREFERENCES_FILE_NAME").apply {
                    parentFile?.mkdirs()
                }
            },
        )
    }
}
