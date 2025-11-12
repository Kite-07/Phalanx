package com.kite.phalanx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kite.phalanx.domain.model.VerdictLevel
import com.kite.phalanx.domain.usecase.AnalyzeMessageRiskUseCase
import com.kite.phalanx.domain.usecase.CheckUrlReputationUseCase
import com.kite.phalanx.domain.usecase.ExtractLinksUseCase
import com.kite.phalanx.domain.usecase.ProfileDomainUseCase
import com.kite.phalanx.domain.repository.UrlExpansionRepository
import com.kite.phalanx.data.source.local.AppDatabase
import com.kite.phalanx.data.source.local.entity.toEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

/**
 * Broadcast receiver that listens for incoming SMS messages.
 * When a new SMS is received, it triggers a notification and analyzes for security threats.
 *
 * Stage 1B Enhancement: Now analyzes messages for phishing on arrival.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var extractLinksUseCase: ExtractLinksUseCase

    @Inject
    lateinit var profileDomainUseCase: ProfileDomainUseCase

    @Inject
    lateinit var analyzeRiskUseCase: AnalyzeMessageRiskUseCase

    @Inject
    lateinit var urlExpansionRepository: UrlExpansionRepository

    @Inject
    lateinit var checkUrlReputationUseCase: CheckUrlReputationUseCase

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context, intent: Intent) {
        // When the app is the default SMS app, it receives SMS_DELIVER instead of SMS_RECEIVED
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        // Use goAsync to handle suspend functions in BroadcastReceiver
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Process each SMS message
                for (smsMessage in messages) {
                    val sender = smsMessage.displayOriginatingAddress ?: continue
                    val messageBody = smsMessage.messageBody ?: ""
                    val timestamp = smsMessage.timestampMillis

                    // Write the message to the system SMS database
                    // This is required for default SMS apps - the system no longer writes messages automatically
                    SmsOperations.writeIncomingSms(
                        context = context,
                        sender = sender,
                        messageBody = messageBody,
                        timestamp = timestamp
                    )

                    // Stage 1B: Analyze message for security threats FIRST
                    // Then show appropriate notification based on verdict
                    analyzeMessageSecurity(context, sender, messageBody, timestamp)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Analyze message for security threats and show threat notification if needed.
     *
     * Stage 1B: Runs security pipeline on received messages.
     */
    private suspend fun analyzeMessageSecurity(
        context: Context,
        sender: String,
        messageBody: String,
        timestamp: Long
    ) {
        try {
            Timber.d("Analyzing message from $sender for security threats...")

            // Skip if message has no body
            if (messageBody.isBlank()) return

            // Phase 1: Extract links
            val links = extractLinksUseCase.execute(messageBody)
            if (links.isEmpty()) {
                Timber.d("No links found, skipping analysis")
                return
            }

            Timber.d("Found ${links.size} links: ${links.map { it.original }}")

            // Phase 2: Expand URLs (with timeout protection)
            val expandedUrls = mutableMapOf<String, com.kite.phalanx.domain.model.ExpandedUrl>()
            links.forEach { link ->
                try {
                    val expandedUrl = urlExpansionRepository.expandUrl(link.original)
                    if (expandedUrl != null && expandedUrl.finalUrl != link.original) {
                        expandedUrls[link.original] = expandedUrl
                        Timber.d("Expanded: ${link.original} -> ${expandedUrl.finalUrl}")
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to expand URL ${link.original}: ${e.message}")
                }
            }

            // Phase 3: Profile domains
            val domainProfiles = links.map { link ->
                profileDomainUseCase.execute(link)
            }

            // Phase 3.5: Check URL reputation (Stage 1C)
            val reputationResults = mutableMapOf<String, List<com.kite.phalanx.domain.model.ReputationResult>>()
            links.forEach { link ->
                try {
                    // Check both original and expanded URLs
                    val urlToCheck = expandedUrls[link.original]?.finalUrl ?: link.original
                    val results = checkUrlReputationUseCase.execute(urlToCheck)
                    reputationResults[link.original] = results

                    val maliciousCount = results.count { it.isMalicious }
                    if (maliciousCount > 0) {
                        Timber.w("Reputation check: $urlToCheck flagged by $maliciousCount service(s)")
                    }
                } catch (e: Exception) {
                    // Reputation check failed (timeout, network error, etc.)
                    // Continue analysis without reputation data - other signals will still catch threats
                    Timber.w("Failed to check reputation for ${link.original}: ${e.message}")
                }
            }

            // Phase 4: Analyze risk
            val verdict = analyzeRiskUseCase.execute(
                messageId = timestamp.toString(),
                sender = sender,
                messageBody = messageBody,
                links = links,
                domainProfiles = domainProfiles,
                expandedUrls = expandedUrls,
                reputationResults = reputationResults
            )

            Timber.d("Verdict: ${verdict.level}, score=${verdict.score}, reasons=${verdict.reasons.size}")

            // Save verdict to database for future reference
            try {
                database.verdictDao().insert(verdict.toEntity(messageId = timestamp))
                Timber.d("Verdict saved to database for message $timestamp")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save verdict to database")
                // Continue - don't let database errors prevent notification
            }

            // Phase 3: Check if message was blocked by user rule
            val isUserBlocked = verdict.reasons.any { it.label == "Blocked by User Rule" }

            // Show appropriate notification based on verdict level
            if (isUserBlocked) {
                // Suppress notification for user-blocked messages
                Timber.i("Message blocked by user rule - notification suppressed")
            } else if (verdict.level == VerdictLevel.AMBER || verdict.level == VerdictLevel.RED) {
                // Show security threat notification for dangerous messages
                val topReason = verdict.reasons.firstOrNull()?.label ?: "Unknown reason"
                Timber.i("THREAT DETECTED! Level=${verdict.level}, reason=$topReason")

                NotificationHelper.showSecurityThreatNotification(
                    context = context,
                    sender = sender,
                    messageBody = messageBody,
                    messageTimestamp = timestamp,
                    verdictLevel = verdict.level.name,
                    topReason = topReason
                )
            } else {
                // Show normal message notification for safe messages (respects mute settings)
                NotificationHelper.showMessageNotification(
                    context = context,
                    sender = sender,
                    message = messageBody,
                    timestamp = timestamp
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing message security")
            // Don't crash - fail gracefully
        }
    }
}
