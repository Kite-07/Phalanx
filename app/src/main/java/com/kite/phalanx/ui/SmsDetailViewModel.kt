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

    // Cache expanded URLs by message timestamp (to show final destination in UI)
    private val expandedUrlCache = mutableMapOf<Long, Map<String, ExpandedUrl>>()

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
                // Phase 1: Extract links (always run - fast local operation)
                Log.d(TAG, "Phase 1: Extracting links...")
                val links = extractLinksUseCase.execute(messageText)
                Log.d(TAG, "Found ${links.size} links: ${links.map { it.original }}")

                // Phase 2: Profile domains (always run - fast local operation)
                // This ensures domainProfileCache is populated even for cached verdicts
                Log.d(TAG, "Phase 2: Profiling domains...")
                val domainProfiles = links.map { link ->
                    profileDomainUseCase.execute(link)
                }
                Log.d(TAG, "Profiled ${domainProfiles.size} domains")

                // Cache domain profiles for later domain extraction (Trust Domain feature)
                domainProfileCache[messageId] = domainProfiles

                // Check database cache for verdict (skip expensive network operations)
                val cachedVerdict = database.verdictDao().getVerdictForMessage(messageId)
                if (cachedVerdict != null) {
                    Log.d(TAG, "Found cached verdict in database for message $messageId: ${cachedVerdict.level}")
                    val verdict = cachedVerdict.toDomainModel()
                    updateVerdictCache(messageId, verdict, sender)
                    return@launch
                }

                Log.d(TAG, "No cached verdict found, performing full analysis...")

                // If no links, mark as GREEN
                if (links.isEmpty()) {
                    Log.d(TAG, "No links found, marking as GREEN")
                    val verdict = analyzeMessageRiskUseCase.execute(
                        messageId = messageId.toString(),
                        sender = sender,
                        messageBody = messageText,
                        links = emptyList(),
                        domainProfiles = emptyList()
                    )
                    Log.d(TAG, "Verdict: ${verdict.level}, score=${verdict.score}")
                    updateVerdictCache(messageId, verdict, sender)
                    return@launch
                }

                // Phase 3: Expand URLs (with timeout protection)
                // Note: URL expansion failures are non-fatal - we continue with domain profiling
                Log.d(TAG, "Phase 3: Expanding URLs...")
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

                // Cache expanded URLs for UI (to show final destination)
                if (expandedUrls.isNotEmpty()) {
                    expandedUrlCache[messageId] = expandedUrls
                }

                // Phase 4: Check URL reputation (Stage 1C)
                Log.d(TAG, "Phase 4: Checking URL reputation...")
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

                // Phase 5: Analyze risk and generate verdict
                Log.d(TAG, "Phase 5: Analyzing risk...")
                val verdict = analyzeMessageRiskUseCase.execute(
                    messageId = messageId.toString(),
                    sender = sender,
                    messageBody = messageText,
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
                    sender = sender,
                    messageBody = messageText,
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
     * Returns the first non-empty registered domain.
     */
    fun getRegisteredDomain(messageId: Long): String? {
        return domainProfileCache[messageId]?.firstOrNull { it.registeredDomain.isNotBlank() }?.registeredDomain
    }

    /**
     * Get the final expanded URL for a message (from cached expanded URLs).
     * Returns the first expanded URL's final destination.
     */
    fun getFinalUrl(messageId: Long): String? {
        return expandedUrlCache[messageId]?.values?.firstOrNull()?.finalUrl
    }

    /**
     * Update verdicts for all messages containing a trusted domain to GREEN.
     *
     * This is called when a user trusts a domain by adding it to the allow list.
     * The domain has already been added to AllowBlockListRepository, so any future
     * analysis will automatically result in GREEN verdicts due to ALLOW rule precedence.
     *
     * For existing cached verdicts, we update them immediately to GREEN so the UI
     * reflects the trust decision without requiring message reload.
     *
     * Phase 3 - Safety Rails: Trust This Domain now uses Allow/Block List system
     * instead of legacy TrustedDomainsPreferences.
     */
    suspend fun trustDomainAndReanalyze(domain: String) {
        Log.d(TAG, "Trust domain: $domain - Updating verdicts for messages with this domain")

        // Find all messages with this domain
        val messagesToUpdate = mutableListOf<Long>()
        domainProfileCache.forEach { (messageId, profiles) ->
            if (profiles.any { it.registeredDomain.equals(domain, ignoreCase = true) }) {
                messagesToUpdate.add(messageId)
            }
        }

        Log.d(TAG, "Found ${messagesToUpdate.size} messages with domain $domain to update")

        // Update verdicts to GREEN for all messages with this domain
        // The ALLOW rule in AllowBlockListRepository will ensure future analyses also return GREEN
        messagesToUpdate.forEach { messageId ->
            val greenVerdict = Verdict(
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
                Log.d(TAG, "Updated verdict for message $messageId to GREEN")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update verdict in database for message $messageId", e)
            }
        }

        Log.d(TAG, "Successfully updated ${messagesToUpdate.size} messages to GREEN")
    }

    /**
     * Clear the verdict cache (e.g., when leaving conversation).
     */
    fun clearCache() {
        _verdictCache.value = emptyMap()
        domainProfileCache.clear()
        expandedUrlCache.clear()
    }
}
