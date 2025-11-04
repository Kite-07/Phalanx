package com.kite.phalanx.data.repository

import android.util.LruCache
import com.kite.phalanx.data.source.local.dao.CachedExpansionDao
import com.kite.phalanx.data.source.local.entity.CachedExpansionEntity
import com.kite.phalanx.domain.model.ExpandedUrl
import com.kite.phalanx.domain.repository.UrlExpansionException
import com.kite.phalanx.domain.repository.UrlExpansionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of UrlExpansionRepository with two-tier caching.
 *
 * Caching Strategy:
 * - Level 1: In-memory LRU cache (fast, limited size)
 * - Level 2: Room database (persistent, larger capacity)
 *
 * Expansion Strategy:
 * - HEAD request first (faster, less data)
 * - Fallback to GET if HEAD fails
 * - Manual redirect following (max 4 hops per PRD)
 * - 1.5s timeout per PRD
 */
@Singleton
class UrlExpansionRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cachedExpansionDao: CachedExpansionDao
) : UrlExpansionRepository {

    companion object {
        private const val MAX_REDIRECTS = 4
        private const val CACHE_EXPIRY_DAYS = 7L
        private const val LRU_CACHE_SIZE = 200 // Maximum 200 entries in memory
    }

    // In-memory LRU cache for fast lookups
    private val memoryCache = LruCache<String, ExpandedUrl>(LRU_CACHE_SIZE)

    override suspend fun expandUrl(url: String): ExpandedUrl = withContext(Dispatchers.IO) {
        // Check memory cache first
        memoryCache.get(url)?.let { return@withContext it }

        // Check database cache
        getCachedExpansion(url)?.let { cached ->
            // Populate memory cache
            memoryCache.put(url, cached)
            return@withContext cached
        }

        // Not cached - perform expansion
        val expandedUrl = performExpansion(url)

        // Cache the result
        cacheExpansion(expandedUrl)

        expandedUrl
    }

    override suspend fun getCachedExpansion(url: String): ExpandedUrl? = withContext(Dispatchers.IO) {
        val entity = cachedExpansionDao.getByOriginalUrl(url) ?: return@withContext null

        // Check if expired
        if (System.currentTimeMillis() > entity.expiresAt) {
            cachedExpansionDao.deleteExpired(System.currentTimeMillis())
            return@withContext null
        }

        // Deserialize redirect chain
        val redirectChain = try {
            Json.decodeFromString<List<String>>(entity.redirectChain)
        } catch (e: Exception) {
            emptyList()
        }

        ExpandedUrl(
            originalUrl = entity.originalUrl,
            finalUrl = entity.finalUrl,
            redirectChain = redirectChain,
            timestamp = entity.timestamp
        )
    }

    override suspend fun clearExpiredCache(): Unit = withContext(Dispatchers.IO) {
        cachedExpansionDao.deleteExpired(System.currentTimeMillis())
        Unit
    }

    override suspend fun clearAllCache(): Unit = withContext(Dispatchers.IO) {
        cachedExpansionDao.deleteAll()
        memoryCache.evictAll()
    }

    /**
     * Perform actual URL expansion by following redirects.
     */
    private suspend fun performExpansion(url: String): ExpandedUrl {
        val redirectChain = mutableListOf<String>()
        var currentUrl = url
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val response = try {
                // Try HEAD request first
                val request = Request.Builder()
                    .url(currentUrl)
                    .head()
                    .build()

                okHttpClient.newCall(request).execute()
            } catch (e: SocketTimeoutException) {
                throw UrlExpansionException.TimeoutError("URL expansion timed out after 1.5s: $currentUrl")
            } catch (e: IOException) {
                // HEAD failed, try GET
                try {
                    val request = Request.Builder()
                        .url(currentUrl)
                        .get()
                        .build()

                    okHttpClient.newCall(request).execute()
                } catch (e: SocketTimeoutException) {
                    throw UrlExpansionException.TimeoutError("URL expansion timed out after 1.5s: $currentUrl")
                } catch (e: IOException) {
                    throw UrlExpansionException.NetworkError("Failed to expand URL: ${e.message}", e)
                }
            } catch (e: Exception) {
                throw UrlExpansionException.InvalidUrl("Invalid URL: $currentUrl")
            }

            val result = response.use {
                if (it.isRedirect) {
                    val location = it.header("Location")
                        ?: throw UrlExpansionException.NetworkError("Redirect without Location header")

                    redirectChain.add(currentUrl)

                    // Resolve relative redirects
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        // Relative URL - resolve against current URL
                        val baseUrl = currentUrl.substringBeforeLast('/')
                        if (location.startsWith("/")) {
                            // Absolute path
                            val protocol = currentUrl.substringBefore("://")
                            val host = currentUrl.substringAfter("://").substringBefore("/")
                            "$protocol://$host$location"
                        } else {
                            // Relative path
                            "$baseUrl/$location"
                        }
                    }

                    redirectCount++
                    null // Continue loop
                } else {
                    // No more redirects - we've reached the final URL
                    val finalUrl = it.request.url.toString()

                    ExpandedUrl(
                        originalUrl = url,
                        finalUrl = finalUrl,
                        redirectChain = redirectChain,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }

            // If we got a result (non-null), return it
            if (result != null) {
                return result
            }
        }

        // Too many redirects
        throw UrlExpansionException.TooManyRedirects(
            "URL exceeded maximum redirect limit ($MAX_REDIRECTS): $url"
        )
    }

    /**
     * Cache an expanded URL in both memory and database.
     */
    private suspend fun cacheExpansion(expandedUrl: ExpandedUrl) {
        // Add to memory cache
        memoryCache.put(expandedUrl.originalUrl, expandedUrl)

        // Add to database cache
        val entity = CachedExpansionEntity(
            originalUrl = expandedUrl.originalUrl,
            finalUrl = expandedUrl.finalUrl,
            redirectChain = Json.encodeToString(expandedUrl.redirectChain),
            timestamp = expandedUrl.timestamp,
            expiresAt = expandedUrl.timestamp + TimeUnit.DAYS.toMillis(CACHE_EXPIRY_DAYS)
        )

        cachedExpansionDao.insert(entity)
    }
}
