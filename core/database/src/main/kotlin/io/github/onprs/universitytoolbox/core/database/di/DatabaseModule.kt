package io.github.onprs.universitytoolbox.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.onprs.universitytoolbox.core.database.RoomToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.database.ToolboxDatabase
import io.github.onprs.universitytoolbox.core.database.ToolboxMetadataRepository
import io.github.onprs.universitytoolbox.core.database.dao.AuditLogDao
import io.github.onprs.universitytoolbox.core.database.dao.FavoriteToolDao
import io.github.onprs.universitytoolbox.core.database.dao.RecentToolDao
import javax.inject.Singleton

/**
 * 宿主元数据数据库 Hilt 装配模块。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    @Singleton
    abstract fun bindToolboxMetadataRepository(impl: RoomToolboxMetadataRepository): ToolboxMetadataRepository

    companion object {
        private const val DATABASE_NAME = "university_toolbox.db"

        @Provides
        @Singleton
        fun provideToolboxDatabase(@ApplicationContext context: Context): ToolboxDatabase = Room.databaseBuilder(
            context,
            ToolboxDatabase::class.java,
            DATABASE_NAME,
        ).build()

        @Provides
        fun provideFavoriteToolDao(database: ToolboxDatabase): FavoriteToolDao = database.favoriteToolDao()

        @Provides
        fun provideRecentToolDao(database: ToolboxDatabase): RecentToolDao = database.recentToolDao()

        @Provides
        fun provideAuditLogDao(database: ToolboxDatabase): AuditLogDao = database.auditLogDao()
    }
}
