package com.kite.phalanx.di

import com.kite.phalanx.data.repository.UrlExpansionRepositoryImpl
import com.kite.phalanx.domain.repository.UrlExpansionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for repository bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUrlExpansionRepository(
        impl: UrlExpansionRepositoryImpl
    ): UrlExpansionRepository
}
