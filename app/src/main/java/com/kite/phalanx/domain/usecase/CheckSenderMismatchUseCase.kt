package com.kite.phalanx.domain.usecase

import com.kite.phalanx.domain.model.DomainProfile
import com.kite.phalanx.domain.model.Link
import com.kite.phalanx.domain.model.SenderPackEntry
import com.kite.phalanx.domain.model.Signal
import com.kite.phalanx.domain.model.SignalCode
import com.kite.phalanx.domain.repository.SenderPackRepository
import javax.inject.Inject

/**
 * Use case to detect sender mismatch attacks (Phase 4).
 *
 * Compares message content claims against actual sender ID using regional sender packs.
 * Triggers SENDER_MISMATCH signal when:
 * - Message mentions a known brand (e.g., "HDFC Bank", "Airtel")
 * - OR link domain matches a known brand
 * - BUT sender ID doesn't match expected patterns for that brand
 *
 * Example attack scenarios:
 * - Sender: "SPAM123" | Message: "Your HDFC account..." → MISMATCH
 * - Sender: "FAKE-BANK" | Link: paypal.com | Message: "PayPal payment..." → MISMATCH
 * - Sender: "HDFCBK" | Message: "Your HDFC account..." → OK (legitimate)
 *
 * Per PRD Phase 4: "Missed call" intent from non-carrier ID triggers SENDER_MISMATCH.
 */
class CheckSenderMismatchUseCase @Inject constructor(
    private val senderPackRepository: SenderPackRepository
) {

    /**
     * Checks for sender mismatch between message content and sender ID.
     *
     * @param senderId The sender ID from the message (e.g., "AX-AIRTEL", "HDFCBK", "+1234567890")
     * @param messageBody The full message text
     * @param links List of links found in the message
     * @param domainProfiles Domain profiles for the links (for brand detection)
     * @return List of SENDER_MISMATCH signals (empty if no mismatch)
     */
    operator fun invoke(
        senderId: String,
        messageBody: String,
        links: List<Link>,
        domainProfiles: List<DomainProfile>
    ): List<Signal> {
        android.util.Log.d(TAG, "=== CheckSenderMismatch START ===")
        android.util.Log.d(TAG, "Sender ID: '$senderId'")
        android.util.Log.d(TAG, "Message: '$messageBody'")

        val signals = mutableListOf<Signal>()
        val pack = senderPackRepository.getCurrentPack()

        if (pack == null) {
            android.util.Log.w(TAG, "No sender pack loaded! Cannot check sender mismatch.")
            return signals
        }

        android.util.Log.d(TAG, "Sender pack loaded: region=${pack.region}, entries=${pack.entries.size}")

        // Find all brands mentioned in the message or linked domains
        val claimedBrands = findClaimedBrands(messageBody, links, domainProfiles, pack.entries)

        android.util.Log.d(TAG, "Claimed brands found: ${claimedBrands.size}")
        claimedBrands.forEach { brand ->
            android.util.Log.d(TAG, "  - ${brand.brand} (${brand.type}) via ${brand.detectionSource}")
        }

        if (claimedBrands.isEmpty()) {
            android.util.Log.d(TAG, "No brand claims detected, no mismatch possible")
            return signals
        }

        // Check if sender ID matches any of the claimed brands
        val matchingSenders = senderPackRepository.findMatchingSenders(senderId)

        android.util.Log.d(TAG, "Sender '$senderId' matches ${matchingSenders.size} known patterns:")
        matchingSenders.forEach { entry ->
            android.util.Log.d(TAG, "  - ${entry.brand} (${entry.type})")
        }

        for (claimedBrand in claimedBrands) {
            val senderMatchesClaim = matchingSenders.any { it.brand.equals(claimedBrand.brand, ignoreCase = true) }

            android.util.Log.d(TAG, "Checking claim '${claimedBrand.brand}': matches=$senderMatchesClaim")

            if (!senderMatchesClaim) {
                // Sender doesn't match claimed brand → MISMATCH
                val weight = calculateMismatchWeight(claimedBrand)
                android.util.Log.w(TAG, "⚠️ SENDER_MISMATCH detected! Claimed: ${claimedBrand.brand}, Sender: $senderId, Weight: $weight")

                signals.add(
                    Signal(
                        code = SignalCode.SENDER_MISMATCH,
                        weight = weight,
                        metadata = mapOf(
                            "claimedBrand" to claimedBrand.brand,
                            "actualSender" to senderId,
                            "brandType" to claimedBrand.type.name,
                            "detectionSource" to claimedBrand.detectionSource
                        )
                    )
                )
            }
        }

        android.util.Log.d(TAG, "=== CheckSenderMismatch END: ${signals.size} signals ===")
        return signals
    }

    companion object {
        private const val TAG = "CheckSenderMismatch"
    }

    /**
     * Checks if a word appears as a whole word in the text (not as a substring).
     *
     * Examples:
     * - containsWholeWord("visit hdfc bank", "hdfc") → true
     * - containsWholeWord("visit hdfc bank", "vi") → false (vi is inside visit)
     * - containsWholeWord("use idea network", "idea") → true
     * - containsWholeWord("ideal solution", "idea") → false (idea is inside ideal)
     *
     * @param text The text to search in (should be lowercase)
     * @param word The word to search for (should be lowercase)
     * @return true if word appears as a complete word
     */
    private fun containsWholeWord(text: String, word: String): Boolean {
        // Use word boundary regex: \b matches position between word and non-word character
        // \b ensures we match whole words only, not substrings
        val pattern = "\\b${Regex.escape(word)}\\b".toRegex()
        return pattern.containsMatchIn(text)
    }

    /**
     * Finds brands that are claimed in the message content or links.
     *
     * Detection methods:
     * 1. Brand keywords in message text
     * 2. Brand official domains in links
     * 3. Brand name variations in message
     */
    private fun findClaimedBrands(
        messageBody: String,
        links: List<Link>,
        domainProfiles: List<DomainProfile>,
        packEntries: List<SenderPackEntry>
    ): List<BrandClaim> {
        android.util.Log.d(TAG, "findClaimedBrands: Checking message for brand mentions...")
        val claims = mutableListOf<BrandClaim>()
        val messageLower = messageBody.lowercase()

        android.util.Log.d(TAG, "Message (lowercase): '$messageLower'")

        // Method 1: Check for brand keywords in message text
        for (entry in packEntries) {
            android.util.Log.v(TAG, "Checking brand: ${entry.brand}, keywords: ${entry.keywords}")

            // Check if brand name appears in message (whole word match)
            val brandLower = entry.brand.lowercase()
            if (containsWholeWord(messageLower, brandLower)) {
                android.util.Log.d(TAG, "✓ Found brand name '${entry.brand}' in message")
                claims.add(
                    BrandClaim(
                        brand = entry.brand,
                        type = entry.type,
                        detectionSource = "brand_name"
                    )
                )
                continue
            }

            // Check for brand-specific keywords (whole word match)
            for (keyword in entry.keywords) {
                val keywordLower = keyword.lowercase()
                if (containsWholeWord(messageLower, keywordLower)) {
                    android.util.Log.d(TAG, "✓ Found keyword '$keyword' for brand '${entry.brand}' in message")
                    claims.add(
                        BrandClaim(
                            brand = entry.brand,
                            type = entry.type,
                            detectionSource = "keyword:$keyword"
                        )
                    )
                    break
                }
            }
        }

        // Method 2: Check for official brand domains in links
        for ((index, link) in links.withIndex()) {
            val domainProfile = domainProfiles.getOrNull(index) ?: continue
            val registeredDomain = domainProfile.registeredDomain ?: continue

            android.util.Log.v(TAG, "Checking link domain: $registeredDomain")

            for (entry in packEntries) {
                // Check if link domain matches official brand domain
                // (This would require adding officialDomains to SenderPackEntry in a future update)
                // For now, we rely on brand impersonation detection from Stage 1B
            }
        }

        val distinctClaims = claims.distinct()
        android.util.Log.d(TAG, "findClaimedBrands result: ${distinctClaims.size} distinct claims")
        return distinctClaims
    }

    /**
     * Calculates mismatch signal weight based on brand type and context.
     *
     * Weights:
     * - BANK: 70 (high severity - financial fraud risk)
     * - GOVERNMENT: 65 (high severity - authority impersonation)
     * - PAYMENT: 65 (high severity - payment fraud risk)
     * - CARRIER: 50 (medium severity - common phishing vector)
     * - ECOMMERCE: 45 (medium severity - account takeover risk)
     * - SERVICE: 40 (medium-low severity)
     * - OTHER: 35 (low-medium severity)
     */
    private fun calculateMismatchWeight(claim: BrandClaim): Int {
        return when (claim.type) {
            com.kite.phalanx.domain.model.SenderType.BANK -> 70
            com.kite.phalanx.domain.model.SenderType.GOVERNMENT -> 65
            com.kite.phalanx.domain.model.SenderType.PAYMENT -> 65
            com.kite.phalanx.domain.model.SenderType.CARRIER -> 50
            com.kite.phalanx.domain.model.SenderType.ECOMMERCE -> 45
            com.kite.phalanx.domain.model.SenderType.SERVICE -> 40
            com.kite.phalanx.domain.model.SenderType.OTHER -> 35
        }
    }

    /**
     * Internal data class representing a brand claim detected in the message
     */
    private data class BrandClaim(
        val brand: String,
        val type: com.kite.phalanx.domain.model.SenderType,
        val detectionSource: String
    )
}
