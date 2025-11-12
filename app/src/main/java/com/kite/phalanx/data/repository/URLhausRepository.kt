package com.kite.phalanx.data.repository

import android.util.LruCache
import com.kite.phalanx.domain.model.ReputationResult
import com.kite.phalanx.domain.model.ThreatType
import com.kite.phalanx.domain.repository.ReputationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * URLhaus API integration.
 *
 * Stage 1C Enhancement: Reputation Services
 * Checks URLs against URLhaus's malware distribution database.
 *
 * API Documentation: https://urlhaus-api.abuse.ch/
 *
 * Free Tier: Completely free, no API key required
 */
@Singleton
class URLhausRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ReputationService {

    companion object {
        private const val API_ENDPOINT = "https://urlhaus-api.abuse.ch/v1/url/"
        private const val CACHE_SIZE = 1000
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    // LRU cache for reputation results (24-hour TTL)
    private val cache = LruCache<String, ReputationResult>(CACHE_SIZE)

    override suspend fun checkUrl(url: String): ReputationResult = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            cache[url]?.let { cached ->
                if (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    Timber.d("Cache hit for $url: ${cached.isMalicious}")
                    return@withContext cached
                }
            }

            // Build request (URLhaus uses POST with form data)
            val formBody = FormBody.Builder()
                .add("url", url)
                .build()

            val request = Request.Builder()
                .url(API_ENDPOINT)
                .post(formBody)
                .build()

            // Execute request
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("URLhaus API error: ${response.code}")
                return@withContext ReputationResult(
                    isMalicious = false,
                    threatType = null,
                    source = "URLhaus",
                    metadata = mapOf("error" to "HTTP ${response.code}")
                )
            }

            // Parse response
            val result = parseResponse(response.body?.string())
            cache.put(url, result)

            Timber.d("URLhaus check for $url: ${result.isMalicious}")
            result

        } catch (e: Exception) {
            Timber.e(e, "Error checking URL with URLhaus")
            ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "URLhaus",
                metadata = mapOf("error" to e.message.orEmpty())
            )
        }
    }

    override fun getServiceName(): String = "URLhaus"

    private fun parseResponse(responseBody: String?): ReputationResult {
        if (responseBody.isNullOrEmpty()) {
            return ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "URLhaus",
                metadata = mapOf("error" to "Empty response")
            )
        }

        try {
            val json = JSONObject(responseBody)

            val queryStatus = json.optString("query_status", "no_results")

            // "ok" means URL is in the database (malicious)
            // "no_results" means URL is not in database (safe)
            val isMalicious = queryStatus == "ok"

            if (isMalicious) {
                val threat = json.optString("threat", "unknown")
                val urlStatus = json.optString("url_status", "unknown")
                val dateAdded = json.optString("date_added", "unknown")

                return ReputationResult(
                    isMalicious = true,
                    threatType = ThreatType.MALWARE,
                    source = "URLhaus",
                    metadata = mapOf(
                        "threat" to threat,
                        "urlStatus" to urlStatus,
                        "dateAdded" to dateAdded,
                        "queryStatus" to queryStatus
                    )
                )
            }

            // Not in database = safe
            return ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "URLhaus",
                metadata = mapOf("queryStatus" to queryStatus)
            )

        } catch (e: Exception) {
            Timber.e(e, "Error parsing URLhaus response")
            return ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "URLhaus",
                metadata = mapOf("error" to "Parse error: ${e.message}")
            )
        }
    }
}
