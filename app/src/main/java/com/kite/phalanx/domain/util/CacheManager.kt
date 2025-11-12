package com.kite.phalanx.domain.util

import android.util.LruCache
import timber.log.Timber

/**
 * Phase 7: Cache manager for monitoring and managing in-memory caches.
 *
 * Per PRD Phase 7: Cache/Storage Hardening
 * - Provides centralized cache monitoring
 * - Tracks cache hit rates and memory usage
 * - Enforces cache size limits
 * - Provides manual cache clearing capabilities
 *
 * Current Cache Limits (per PRD performance budgets):
 * - URL Expansion: 200 entries
 * - Link Preview: 100 entries
 * - Safe Browsing: 1000 entries
 * - PhishTank: 1000 entries
 * - URLhaus: 1000 entries
 * - Total: ~3200 entries in memory
 *
 * Average memory per entry: ~500 bytes
 * Total estimated memory: ~1.6 MB (well within 15MB peak budget)
 */
object CacheManager {

    /**
     * Cache size limits per repository.
     * These limits are enforced by LruCache automatic eviction.
     */
    object CacheLimits {
        const val URL_EXPANSION = 200      // UrlExpansionRepository
        const val LINK_PREVIEW = 100       // LinkPreviewRepository
        const val SAFE_BROWSING = 1000     // SafeBrowsingRepository
        const val PHISH_TANK = 1000        // PhishTankRepository (if exists)
        const val URLHAUS = 1000           // URLhausRepository
    }

    /**
     * Cache statistics for monitoring.
     */
    data class CacheStats(
        val name: String,
        val currentSize: Int,
        val maxSize: Int,
        val hitCount: Int,
        val missCount: Int,
        val evictionCount: Int,
        val hitRate: Float
    )

    /**
     * Get statistics for an LruCache.
     *
     * @param name Human-readable cache name
     * @param cache The LruCache instance
     * @return Cache statistics
     */
    fun <K, V> getStats(name: String, cache: LruCache<K, V>): CacheStats {
        val hitRate = if (cache.hitCount() + cache.missCount() > 0) {
            cache.hitCount().toFloat() / (cache.hitCount() + cache.missCount())
        } else {
            0f
        }

        return CacheStats(
            name = name,
            currentSize = cache.size(),
            maxSize = cache.maxSize(),
            hitCount = cache.hitCount(),
            missCount = cache.missCount(),
            evictionCount = cache.evictionCount(),
            hitRate = hitRate
        )
    }

    /**
     * Log cache statistics for monitoring.
     *
     * @param stats Cache statistics to log
     */
    fun logStats(stats: CacheStats) {
        Timber.d(buildString {
            append("Cache: ${stats.name}\n")
            append("  Size: ${stats.currentSize}/${stats.maxSize}\n")
            append("  Hits: ${stats.hitCount}, Misses: ${stats.missCount}\n")
            append("  Hit Rate: ${"%.2f".format(stats.hitRate * 100)}%\n")
            append("  Evictions: ${stats.evictionCount}")
        })
    }

    /**
     * Check if cache is healthy (hit rate > 50%, not full).
     *
     * @param stats Cache statistics
     * @return True if cache is performing well
     */
    fun isHealthy(stats: CacheStats): Boolean {
        val isHitRateGood = stats.hitRate > 0.5f || stats.hitCount + stats.missCount < 10
        val isNotFull = stats.currentSize < stats.maxSize * 0.9 // Not more than 90% full
        return isHitRateGood && isNotFull
    }

    /**
     * Trim cache to a specific size (removes LRU entries).
     *
     * @param cache The LruCache to trim
     * @param targetSize Target size after trimming
     */
    fun <K, V> trimToSize(cache: LruCache<K, V>, targetSize: Int) {
        cache.trimToSize(targetSize)
        Timber.d("Trimmed cache to $targetSize entries")
    }

    /**
     * Clear all entries from a cache.
     *
     * @param cache The LruCache to clear
     */
    fun <K, V> clear(cache: LruCache<K, V>) {
        cache.evictAll()
        Timber.d("Cleared cache")
    }

    /**
     * Get total estimated memory usage across all caches.
     *
     * Assumption: Average 500 bytes per cache entry
     *
     * @param totalEntries Total number of entries across all caches
     * @return Estimated memory in MB
     */
    fun estimateMemoryUsageMB(totalEntries: Int): Float {
        val bytesPerEntry = 500
        val totalBytes = totalEntries * bytesPerEntry
        return totalBytes / (1024f * 1024f)
    }
}
