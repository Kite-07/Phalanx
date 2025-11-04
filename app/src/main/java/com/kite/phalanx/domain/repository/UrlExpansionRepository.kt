package com.kite.phalanx.domain.repository

import com.kite.phalanx.domain.model.ExpandedUrl

/**
 * Repository for URL expansion operations.
 * Follows shortened URLs to their final destination with caching.
 */
interface UrlExpansionRepository {

    /**
     * Expand a URL by following redirects to the final destination.
     *
     * @param url The original URL to expand
     * @return ExpandedUrl containing the final destination and redirect chain
     * @throws UrlExpansionException if expansion fails
     */
    suspend fun expandUrl(url: String): ExpandedUrl

    /**
     * Get cached expansion if available.
     *
     * @param url The original URL
     * @return Cached ExpandedUrl or null if not cached
     */
    suspend fun getCachedExpansion(url: String): ExpandedUrl?

    /**
     * Clear expired cache entries.
     */
    suspend fun clearExpiredCache()

    /**
     * Clear all cached expansions.
     */
    suspend fun clearAllCache()
}

/**
 * Exception thrown when URL expansion fails.
 */
sealed class UrlExpansionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError(message: String, cause: Throwable? = null) : UrlExpansionException(message, cause)
    class TimeoutError(message: String) : UrlExpansionException(message)
    class TooManyRedirects(message: String) : UrlExpansionException(message)
    class InvalidUrl(message: String) : UrlExpansionException(message)
}
