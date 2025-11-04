package com.kite.phalanx.di

import android.content.Context
import androidx.room.Room
import com.kite.phalanx.data.source.local.AppDatabase
import com.kite.phalanx.data.source.local.dao.CachedExpansionDao
import com.kite.phalanx.data.source.local.dao.SignalDao
import com.kite.phalanx.data.source.local.dao.VerdictDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Room database dependencies.
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
            .fallbackToDestructiveMigration()
            .build()
    }

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
}
