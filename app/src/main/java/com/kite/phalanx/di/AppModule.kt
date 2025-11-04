package com.kite.phalanx.di

import android.content.Context
import com.kite.phalanx.data.util.PublicSuffixListParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun providePublicSuffixListParser(@ApplicationContext context: Context): PublicSuffixListParser {
        return PublicSuffixListParser(context)
    }
}
