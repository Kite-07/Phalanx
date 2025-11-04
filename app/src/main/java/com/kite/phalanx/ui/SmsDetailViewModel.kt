package com.kite.phalanx.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kite.phalanx.NotificationHelper
import com.kite.phalanx.domain.model.ExpandedUrl
import com.kite.phalanx.domain.model.Verdict
import com.kite.phalanx.domain.model.VerdictLevel
import com.kite.phalanx.domain.usecase.AnalyzeMessageRiskUseCase
import com.kite.phalanx.domain.usecase.CheckUrlReputationUseCase
import com.kite.phalanx.domain.usecase.ExtractLinksUseCase
import com.kite.phalanx.domain.usecase.ProfileDomainUseCase
import com.kite.phalanx.domain.repository.UrlExpansionRepository
import com.kite.phalanx.data.source.local.AppDatabase
import com.kite.phalanx.data.source.local.entity.toDomainModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for SmsDetailActivity.
 *
 * Handles security analysis of messages using the Phase 1 pipeline:
 * 1. Extract links from message text
 * 2. Expand shortened URLs
 * 3. Profile domains for security signals
 * 4. Analyze risk and generate verdict
 *
 * Verdicts are cached by messageId (timestamp) to avoid re-analyzing.
 */
@HiltViewModel
class SmsDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractLinksUseCase: ExtractLinksUseCase,
    private val urlExpansionRepository: UrlExpansionRepository,
    private val profileDomainUseCase: ProfileDomainUseCase,
    private val analyzeMessageRiskUseCase: AnalyzeMessageRiskUseCase,
    private val checkUrlReputationUseCase: CheckUrlReputationUseCase,
    private val database: AppDatabase
) : ViewModel() {

    // Cache verdicts by message timestamp (messageId)
    private val _verdictCache = MutableStateFlow<Map<Long, Verdict>>(emptyMap())
    val verdictCache: StateFlow<Map<Long, Verdict>> = _verdictCache.asStateFlow()

    // Cache domain profiles by message timestamp (to extract domain when trusting)
    private val domainProfileCache = mutableMapOf<Long, List<com.kite.phalanx.domain.model.DomainProfile>>()

    /**
     * Analyze a message for security threats.
     *
     * @param messageId Message timestamp (unique identifier)
     * @param messageText The message body text
     * @param sender The sender's phone number or identifier
     */
    fun analyzeMessage(messageId: Long, messageText: String, sender: String) {
        Log.d(TAG, "analyzeMessage called for messageId=$messageId, sender='$sender', text='$messageText'")

        // Skip if already in memory cache
        if (_verdictCache.value.containsKey(messageId)) {
            Log.d(TAG, "Message $messageId already in memory cache, skipping")
            return
        }

        viewModelScope.launch {
            try {
                // Check database first (avoid re-analyzing previously seen messages)
                val cachedVerdict = database.verdictDao().getVerdictForMessage(messageId)
                if (cachedVerdict != null) {
                    Log.d(TAG, "Found cached verdict in database for message $messageId: ${cachedVerdict.level}")
                    val verdict = cachedVerdict.toDomainModel()
                    updateVerdictCache(messageId, verdict, sender)
                    return@launch
                }

                Log.d(TAG, "No cached verdict found, performing full analysis...")

                // Phase 1: Extract links
                Log.d(TAG, "Phase 1: Extracting links...")
                val links = extractLinksUseCase.execute(messageText)
                Log.d(TAG, "Found ${links.size} links: ${links.map { it.original }}")

                // If no links, skip analysis (GREEN by default)
                if (links.isEmpty()) {
                    Log.d(TAG, "No links found, marking as GREEN")
                    val verdict = analyzeMessageRiskUseCase.execute(
                        messageId = messageId.toString(),
                        links = emptyList(),
                        domainProfiles = emptyList()
                    )
                    Log.d(TAG, "Verdict: ${verdict.level}, score=${verdict.score}")
                    updateVerdictCache(messageId, verdict, sender)
                    return@launch
                }

                // Phase 2: Expand URLs (with timeout protection)
                // Note: URL expansion failures are non-fatal - we continue with domain profiling
                Log.d(TAG, "Phase 2: Expanding URLs...")
                val expandedUrls = mutableMapOf<String, ExpandedUrl>()
                links.forEach { link ->
                    try {
                        val expandedUrl = urlExpansionRepository.expandUrl(link.original)
                        if (expandedUrl != null && expandedUrl.finalUrl != link.original) {
                            expandedUrls[link.original] = expandedUrl
                            Log.d(TAG, "Expanded: ${link.original} -> ${expandedUrl.finalUrl}")
                        }
                    } catch (e: Exception) {
                        // URL expansion failed (timeout, network error, etc.)
                        // Continue analysis without expansion - domain profiling will still catch threats
                        Log.w(TAG, "Failed to expand URL ${link.original}: ${e.message}")
                    }
                }

                // Phase 3: Profile domains
                Log.d(TAG, "Phase 3: Profiling domains...")
                val domainProfiles = links.map { link ->
                    profileDomainUseCase.execute(link)
                }
                Log.d(TAG, "Profiled ${domainProfiles.size} domains")

                // Cache domain profiles for later domain extraction
                domainProfileCache[messageId] = domainProfiles

                // Phase 3.5: Check URL reputation (Stage 1C)
                Log.d(TAG, "Phase 3.5: Checking URL reputation...")
                val reputationResults = mutableMapOf<String, List<com.kite.phalanx.domain.model.ReputationResult>>()
                links.forEach { link ->
                    try {
                        // Check both original and expanded URLs
                        val urlToCheck = expandedUrls[link.original]?.finalUrl ?: link.original
                        val results = checkUrlReputationUseCase.execute(urlToCheck)
                        reputationResults[link.original] = results

                        val maliciousCount = results.count { it.isMalicious }
                        if (maliciousCount > 0) {
                            Log.w(TAG, "Reputation check: $urlToCheck flagged by $maliciousCount service(s)")
                        }
                    } catch (e: Exception) {
                        // Reputation check failed (timeout, network error, etc.)
                        // Continue analysis without reputation data - other signals will still catch threats
                        Log.w(TAG, "Failed to check reputation for ${link.original}: ${e.message}")
                    }
                }

                // Phase 4: Analyze risk and generate verdict
                Log.d(TAG, "Phase 4: Analyzing risk...")
                val verdict = analyzeMessageRiskUseCase.execute(
                    messageId = messageId.toString(),
                    links = links,
                    domainProfiles = domainProfiles,
                    expandedUrls = expandedUrls,
                    reputationResults = reputationResults
                )
                Log.d(TAG, "Verdict: ${verdict.level}, score=${verdict.score}, reasons=${verdict.reasons.size}")

                // Cache the verdict
                updateVerdictCache(messageId, verdict, sender)
                Log.d(TAG, "Verdict cached. Cache size: ${_verdictCache.value.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing message", e)
                // On error, mark as GREEN (fail-safe)
                val verdict = analyzeMessageRiskUseCase.execute(
                    messageId = messageId.toString(),
                    links = emptyList(),
                    domainProfiles = emptyList()
                )
                updateVerdictCache(messageId, verdict, sender)
            }
        }
    }

    companion object {
        private const val TAG = "SmsDetailViewModel"
    }

    /**
     * Get verdict for a specific message.
     */
    fun getVerdict(messageId: Long): Verdict? {
        return _verdictCache.value[messageId]
    }

    /**
     * Update the verdict cache.
     *
     * Note: Does NOT send notifications. Notifications are only sent by SmsReceiver
     * when messages are first received, not when viewing existing messages.
     */
    private fun updateVerdictCache(messageId: Long, verdict: Verdict, sender: String) {
        _verdictCache.value = _verdictCache.value + (messageId to verdict)

        // Log the verdict but don't send notification (that's only for new messages in SmsReceiver)
        if (verdict.level == VerdictLevel.AMBER || verdict.level == VerdictLevel.RED) {
            Log.d(TAG, "Threat detected: ${verdict.level} verdict (no notification - message already received)")
        }
    }

    /**
     * Get the registered domain for a message (from cached domain profiles).
     */
    fun getRegisteredDomain(messageId: Long): String? {
        return domainProfileCache[messageId]?.firstOrNull()?.registeredDomain
    }

    /**
     * Re-analyze all messages containing a specific domain and update their verdicts to GREEN.
     * This is called when a user trusts a domain.
     */
    suspend fun trustDomainAndReanalyze(domain: String) {
        Log.d(TAG, "Trust domain: $domain - Re-analyzing all messages with this domain")

        // Find all messages with this domain
        val messagesToUpdate = mutableListOf<Long>()
        domainProfileCache.forEach { (messageId, profiles) ->
            if (profiles.any { it.registeredDomain.equals(domain, ignoreCase = true) }) {
                messagesToUpdate.add(messageId)
            }
        }

        Log.d(TAG, "Found ${messagesToUpdate.size} messages with domain $domain to update")

        // Update verdicts to GREEN for all messages with this domain
        messagesToUpdate.forEach { messageId ->
            val greenVerdict = com.kite.phalanx.domain.model.Verdict(
                messageId = messageId.toString(),
                level = VerdictLevel.GREEN,
                score = 0,
                reasons = emptyList()
            )

            // Update in-memory cache
            _verdictCache.value = _verdictCache.value + (messageId to greenVerdict)

            // Update database
            try {
                database.verdictDao().insert(
                    com.kite.phalanx.data.source.local.entity.VerdictEntity(
                        messageId = messageId,
                        level = "GREEN",
                        score = 0,
                        reasons = "[]",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update verdict in database for message $messageId", e)
            }
        }

        Log.d(TAG, "Successfully re-analyzed ${messagesToUpdate.size} messages")
    }

    /**
     * Clear the verdict cache (e.g., when leaving conversation).
     */
    fun clearCache() {
        _verdictCache.value = emptyMap()
        domainProfileCache.clear()
    }
}
