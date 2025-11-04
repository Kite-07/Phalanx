package com.kite.phalanx.domain.repository

import com.kite.phalanx.domain.model.ReputationResult

/**
 * Service interface for checking URL reputation against various threat databases.
 *
 * Stage 1C Enhancement: Reputation Services
 * Integrates with Google Safe Browsing, PhishTank, and URLhaus for known-bad URL detection.
 */
interface ReputationService {
    /**
     * Check if a URL is known to be malicious.
     *
     * @param url The URL to check
     * @return ReputationResult with threat information
     */
    suspend fun checkUrl(url: String): ReputationResult

    /**
     * Get the name of this reputation service (for logging/debugging).
     */
    fun getServiceName(): String
}
