package com.kite.phalanx.data.repository

import android.content.Context
import android.util.LruCache
import com.kite.phalanx.SafeBrowsingPreferences
import com.kite.phalanx.domain.model.ReputationResult
import com.kite.phalanx.domain.model.ThreatType
import com.kite.phalanx.domain.repository.ReputationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Google Safe Browsing API v4 integration.
 *
 * Stage 1C Enhancement: Reputation Services
 * Checks URLs against Google's database of malicious sites.
 *
 * Uses custom API key from user settings if provided, otherwise falls back to default key.
 *
 * API Documentation: https://developers.google.com/safe-browsing/v4
 *
 * Free Tier Limits:
 * - 300,000 lookups/day
 * - Update Lookup API: 10,000 requests/day
 */
@Singleton
class SafeBrowsingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : ReputationService {

    companion object {
        private const val API_ENDPOINT = "https://safebrowsing.googleapis.com/v4/threatMatches:find"
        private const val CACHE_SIZE = 1000
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

        // Broadcast action for quota exceeded
        const val ACTION_QUOTA_EXCEEDED = "com.kite.phalanx.SAFE_BROWSING_QUOTA_EXCEEDED"
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

            // Get API key from preferences (custom key or default)
            val apiKey = SafeBrowsingPreferences.getApiKey(context)
            Timber.d("Using API key: ${if (SafeBrowsingPreferences.hasCustomApiKey(context)) "custom" else "default"}")

            // Build request
            val requestBody = buildRequestBody(url)
            val request = Request.Builder()
                .url("$API_ENDPOINT?key=$apiKey")
                .post(requestBody)
                .build()

            // Execute request
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("Safe Browsing API error: ${response.code}")

                // Check if quota exceeded (HTTP 429 or 403 with quota message)
                val isQuotaExceeded = response.code == 429 ||
                    (response.code == 403 && response.body?.string()?.contains("quota", ignoreCase = true) == true)

                if (isQuotaExceeded) {
                    Timber.e("Google Safe Browsing quota exceeded! User should add custom API key.")
                    // Broadcast quota exceeded event
                    broadcastQuotaExceeded()
                }

                return@withContext ReputationResult(
                    isMalicious = false,
                    threatType = null,
                    source = "Google Safe Browsing",
                    metadata = mapOf(
                        "error" to "HTTP ${response.code}",
                        "quotaExceeded" to isQuotaExceeded.toString()
                    )
                )
            }

            // Parse response
            val result = parseResponse(response.body?.string())
            cache.put(url, result)

            Timber.d("Safe Browsing check for $url: ${result.isMalicious}")
            result

        } catch (e: Exception) {
            Timber.e(e, "Error checking URL with Safe Browsing")
            ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "Google Safe Browsing",
                metadata = mapOf("error" to e.message.orEmpty())
            )
        }
    }

    override fun getServiceName(): String = "Google Safe Browsing"

    /**
     * Broadcast quota exceeded event to notify UI.
     */
    private fun broadcastQuotaExceeded() {
        try {
            val intent = android.content.Intent(ACTION_QUOTA_EXCEEDED)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to broadcast quota exceeded event")
        }
    }

    private fun buildRequestBody(url: String): okhttp3.RequestBody {
        val json = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientId", "phalanx-sms")
                put("clientVersion", "1.0.0")
            })
            put("threatInfo", JSONObject().apply {
                put("threatTypes", JSONArray().apply {
                    put("MALWARE")
                    put("SOCIAL_ENGINEERING")
                    put("UNWANTED_SOFTWARE")
                    put("POTENTIALLY_HARMFUL_APPLICATION")
                })
                put("platformTypes", JSONArray().apply {
                    put("ANY_PLATFORM")
                })
                put("threatEntryTypes", JSONArray().apply {
                    put("URL")
                })
                put("threatEntries", JSONArray().apply {
                    put(JSONObject().apply {
                        put("url", url)
                    })
                })
            })
        }

        return json.toString().toRequestBody("application/json".toMediaType())
    }

    private fun parseResponse(responseBody: String?): ReputationResult {
        if (responseBody.isNullOrEmpty()) {
            // Empty response means URL is safe
            return ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "Google Safe Browsing"
            )
        }

        try {
            val json = JSONObject(responseBody)

            // If matches array exists and has entries, URL is malicious
            val matches = json.optJSONArray("matches")
            if (matches != null && matches.length() > 0) {
                val firstMatch = matches.getJSONObject(0)
                val threatTypeStr = firstMatch.optString("threatType", "UNKNOWN")
                val platformType = firstMatch.optString("platformType", "ANY_PLATFORM")

                return ReputationResult(
                    isMalicious = true,
                    threatType = mapThreatType(threatTypeStr),
                    source = "Google Safe Browsing",
                    metadata = mapOf(
                        "threatType" to threatTypeStr,
                        "platformType" to platformType
                    )
                )
            }

            // No matches = safe
            return ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "Google Safe Browsing"
            )

        } catch (e: Exception) {
            Timber.e(e, "Error parsing Safe Browsing response")
            return ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "Google Safe Browsing",
                metadata = mapOf("error" to "Parse error: ${e.message}")
            )
        }
    }

    private fun mapThreatType(threatTypeStr: String): ThreatType {
        return when (threatTypeStr) {
            "MALWARE" -> ThreatType.MALWARE
            "SOCIAL_ENGINEERING" -> ThreatType.SOCIAL_ENGINEERING
            "UNWANTED_SOFTWARE" -> ThreatType.UNWANTED_SOFTWARE
            "POTENTIALLY_HARMFUL_APPLICATION" -> ThreatType.POTENTIALLY_HARMFUL
            else -> ThreatType.UNKNOWN
        }
    }
}
