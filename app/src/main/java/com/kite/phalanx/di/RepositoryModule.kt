package com.kite.phalanx.di

import com.kite.phalanx.data.repository.AllowBlockListRepositoryImpl
import com.kite.phalanx.data.repository.LinkPreviewRepositoryImpl
import com.kite.phalanx.data.repository.SenderPackRepositoryImpl
import com.kite.phalanx.data.repository.TrashVaultRepositoryImpl
import com.kite.phalanx.data.repository.UrlExpansionRepositoryImpl
import com.kite.phalanx.domain.repository.AllowBlockListRepository
import com.kite.phalanx.domain.repository.LinkPreviewRepository
import com.kite.phalanx.domain.repository.SenderPackRepository
import com.kite.phalanx.domain.repository.TrashVaultRepository
import com.kite.phalanx.domain.repository.UrlExpansionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for repository bindings.
 *
 * Phase 1: URL expansion repository
 * Phase 3: Trash vault and allow/block list repositories
 * Phase 4: Sender pack repository
 * Phase 5: Link preview repository
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Phase 1
    @Binds
    @Singleton
    abstract fun bindUrlExpansionRepository(
        impl: UrlExpansionRepositoryImpl
    ): UrlExpansionRepository

    // Phase 3
    @Binds
    @Singleton
    abstract fun bindTrashVaultRepository(
        impl: TrashVaultRepositoryImpl
    ): TrashVaultRepository

    @Binds
    @Singleton
    abstract fun bindAllowBlockListRepository(
        impl: AllowBlockListRepositoryImpl
    ): AllowBlockListRepository

    // Phase 4
    @Binds
    @Singleton
    abstract fun bindSenderPackRepository(
        impl: SenderPackRepositoryImpl
    ): SenderPackRepository

    // Phase 5
    @Binds
    @Singleton
    abstract fun bindLinkPreviewRepository(
        impl: LinkPreviewRepositoryImpl
    ): LinkPreviewRepository
}
