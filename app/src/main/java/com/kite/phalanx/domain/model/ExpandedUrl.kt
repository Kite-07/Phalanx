package com.kite.phalanx.domain.model

/**
 * Result of URL expansion (following redirects).
 *
 * @property originalUrl The original URL
 * @property finalUrl The final URL after following redirects
 * @property redirectChain List of intermediate URLs in the redirect chain
 * @property timestamp When this expansion was performed
 */
data class ExpandedUrl(
    val originalUrl: String,
    val finalUrl: String,
    val redirectChain: List<String>,
    val timestamp: Long
) {
    /**
     * Number of redirects in the chain (Stage 1B).
     * Used to detect excessive redirect patterns.
     */
    val redirectCount: Int
        get() = redirectChain.size
}
