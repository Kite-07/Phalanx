package com.kite.phalanx.domain.usecase

import com.kite.phalanx.data.repository.SafeBrowsingRepository
import com.kite.phalanx.data.repository.URLhausRepository
import com.kite.phalanx.domain.model.ReputationResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import timber.log.Timber

/**
 * Use case for checking URL reputation against multiple threat databases.
 *
 * Stage 1C Enhancement: Reputation Services
 * Coordinates checks across Google Safe Browsing and URLhaus in parallel.
 *
 * Note: PhishTank removed as registration is no longer available.
 *
 * @property safeBrowsingRepository Google Safe Browsing API client
 * @property urlhausRepository URLhaus API client
 */
class CheckUrlReputationUseCase @Inject constructor(
    private val safeBrowsingRepository: SafeBrowsingRepository,
    private val urlhausRepository: URLhausRepository
) {
    /**
     * Check a URL against all reputation services in parallel.
     *
     * Returns all results, even if some services fail or are not configured.
     * The caller should check isMalicious on each result.
     *
     * @param url The URL to check
     * @return List of reputation results from all services
     */
    suspend fun execute(url: String): List<ReputationResult> = coroutineScope {
        Timber.d("Checking reputation for: $url")

        // Query all services in parallel for speed
        val safeBrowsingDeferred = async { safeBrowsingRepository.checkUrl(url) }
        val urlhausDeferred = async { urlhausRepository.checkUrl(url) }

        // Wait for all results
        val results = listOf(
            safeBrowsingDeferred.await(),
            urlhausDeferred.await()
        )

        // Log summary
        val maliciousCount = results.count { it.isMalicious }
        if (maliciousCount > 0) {
            Timber.w("URL flagged as malicious by $maliciousCount service(s): $url")
            results.filter { it.isMalicious }.forEach {
                Timber.w("  - ${it.source}: ${it.threatType} (${it.metadata})")
            }
        } else {
            Timber.d("URL clean according to all reputation services: $url")
        }

        results
    }

    /**
     * Check a URL and return true if ANY service flags it as malicious.
     *
     * Convenience method for simple boolean checks.
     */
    suspend fun isMalicious(url: String): Boolean {
        val results = execute(url)
        return results.any { it.isMalicious }
    }

    /**
     * Check a URL and return the most severe threat result (if any).
     *
     * Returns the first malicious result, or null if all services say it's clean.
     */
    suspend fun getMostSevereResult(url: String): ReputationResult? {
        val results = execute(url)
        return results.firstOrNull { it.isMalicious }
    }
}
