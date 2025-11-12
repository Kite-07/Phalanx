package com.kite.phalanx.domain.repository

import com.kite.phalanx.domain.model.LinkPreview

/**
 * Repository for fetching and caching link previews.
 *
 * Per PRD Phase 5:
 * - Fetches safe previews (title, favicon) for URLs
 * - Caches results to avoid repeated network requests
 * - 7-day cache expiry
 */
interface LinkPreviewRepository {

    /**
     * Get a link preview for the given URL.
     * Checks cache first, then fetches if not cached.
     *
     * @param url The URL to get a preview for
     * @return LinkPreview with title/favicon or error
     */
    suspend fun getPreview(url: String): LinkPreview

    /**
     * Get cached preview if available and not expired.
     *
     * @param url The URL to check cache for
     * @return Cached LinkPreview or null if not cached/expired
     */
    suspend fun getCachedPreview(url: String): LinkPreview?

    /**
     * Clear expired preview cache entries.
     */
    suspend fun cleanExpiredCache()
}
