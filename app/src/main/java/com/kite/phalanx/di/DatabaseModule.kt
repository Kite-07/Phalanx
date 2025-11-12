package com.kite.phalanx.di

import android.content.Context
import androidx.room.Room
import com.kite.phalanx.data.source.local.AppDatabase
import com.kite.phalanx.data.source.local.dao.AllowBlockRuleDao
import com.kite.phalanx.data.source.local.dao.CachedExpansionDao
import com.kite.phalanx.data.source.local.dao.LinkPreviewDao
import com.kite.phalanx.data.source.local.dao.SignalDao
import com.kite.phalanx.data.source.local.dao.TrashedMessageDao
import com.kite.phalanx.data.source.local.dao.VerdictDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Room database dependencies.
 *
 * Phase 1: Security analysis (verdicts, signals, cache)
 * Phase 3: Safety rails (trash vault, allow/block lists)
 * Phase 5: Link preview caching
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration() // Fallback for other migrations during development
            .build()
    }

    // Phase 1 DAOs
    @Provides
    fun provideCachedExpansionDao(database: AppDatabase): CachedExpansionDao {
        return database.cachedExpansionDao()
    }

    @Provides
    fun provideSignalDao(database: AppDatabase): SignalDao {
        return database.signalDao()
    }

    @Provides
    fun provideVerdictDao(database: AppDatabase): VerdictDao {
        return database.verdictDao()
    }

    // Phase 3 DAOs
    @Provides
    fun provideTrashedMessageDao(database: AppDatabase): TrashedMessageDao {
        return database.trashedMessageDao()
    }

    @Provides
    fun provideAllowBlockRuleDao(database: AppDatabase): AllowBlockRuleDao {
        return database.allowBlockRuleDao()
    }

    // Phase 5 DAOs
    @Provides
    fun provideLinkPreviewDao(database: AppDatabase): LinkPreviewDao {
        return database.linkPreviewDao()
    }
}
