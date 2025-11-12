package com.kite.phalanx.data.repository

import android.util.LruCache
import com.kite.phalanx.data.source.local.dao.LinkPreviewDao
import com.kite.phalanx.data.source.local.entity.LinkPreviewEntity
import com.kite.phalanx.domain.model.LinkPreview
import com.kite.phalanx.domain.repository.LinkPreviewRepository
import com.kite.phalanx.domain.usecase.FetchLinkPreviewUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LinkPreviewRepository with two-tier caching.
 *
 * Per PRD Phase 5:
 * - Level 1: In-memory LRU cache (fast, limited size)
 * - Level 2: Room database (persistent, larger capacity)
 * - 7-day cache expiry
 * - Uses FetchLinkPreviewUseCase for network fetching
 */
@Singleton
class LinkPreviewRepositoryImpl @Inject constructor(
    private val fetchLinkPreviewUseCase: FetchLinkPreviewUseCase,
    private val linkPreviewDao: LinkPreviewDao
) : LinkPreviewRepository {

    companion object {
        private const val CACHE_EXPIRY_DAYS = 7L
        private const val LRU_CACHE_SIZE = 100 // Maximum 100 preview entries in memory
    }

    // In-memory LRU cache for fast lookups
    private val memoryCache = LruCache<String, LinkPreview>(LRU_CACHE_SIZE)

    override suspend fun getPreview(url: String): LinkPreview = withContext(Dispatchers.IO) {
        // Check memory cache first
        memoryCache.get(url)?.let { return@withContext it }

        // Check database cache
        getCachedPreview(url)?.let { cached ->
            // Populate memory cache
            memoryCache.put(url, cached)
            return@withContext cached
        }

        // Not cached - fetch preview
        val preview = fetchLinkPreviewUseCase.execute(url)

        // Cache the result
        cachePreview(preview)

        preview
    }

    override suspend fun getCachedPreview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        val entity = linkPreviewDao.getByUrl(url) ?: return@withContext null

        // Check if expired
        if (System.currentTimeMillis() > entity.expiresAt) {
            linkPreviewDao.deleteExpired(System.currentTimeMillis())
            return@withContext null
        }

        // Convert entity to domain model
        LinkPreview(
            url = entity.url,
            title = entity.title,
            faviconData = entity.faviconData,
            fetchedAt = entity.fetchedAt,
            error = entity.error
        )
    }

    override suspend fun cleanExpiredCache(): Unit = withContext(Dispatchers.IO) {
        linkPreviewDao.deleteExpired(System.currentTimeMillis())
        Unit
    }

    /**
     * Cache a link preview in both memory and database.
     */
    private suspend fun cachePreview(preview: LinkPreview) {
        // Add to memory cache
        memoryCache.put(preview.url, preview)

        // Persist to database
        val expiresAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(CACHE_EXPIRY_DAYS)
        val entity = LinkPreviewEntity(
            url = preview.url,
            title = preview.title,
            faviconData = preview.faviconData,
            fetchedAt = preview.fetchedAt,
            expiresAt = expiresAt,
            error = preview.error
        )

        linkPreviewDao.insert(entity)
    }
}
