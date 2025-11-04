package com.kite.phalanx.domain.util

import com.kite.phalanx.domain.model.TldRiskLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TLD risk scoring system for phishing detection.
 *
 * Stage 1B Enhancement: High-Risk TLD Scoring
 * Flags domains using TLDs commonly abused for phishing.
 */
@Singleton
class TldRiskScorer @Inject constructor() {

    companion object {
        /**
         * TLD risk mapping based on abuse patterns and phishing statistics.
         *
         * Sources:
         * - Spamhaus TLD reputation data
         * - APWG phishing activity trends
         * - Free TLD abuse patterns
         */
        private val TLD_RISK_MAP = mapOf(
            // CRITICAL: Free TLDs heavily abused by scammers
            "tk" to TldRiskLevel.CRITICAL,      // Tokelau (free)
            "ml" to TldRiskLevel.CRITICAL,      // Mali (free)
            "ga" to TldRiskLevel.CRITICAL,      // Gabon (free)
            "cf" to TldRiskLevel.CRITICAL,      // Central African Republic (free)
            "gq" to TldRiskLevel.CRITICAL,      // Equatorial Guinea (free)

            // HIGH: Cheap TLDs popular with scammers
            "xyz" to TldRiskLevel.HIGH,
            "top" to TldRiskLevel.HIGH,
            "club" to TldRiskLevel.HIGH,
            "online" to TldRiskLevel.HIGH,
            "site" to TldRiskLevel.HIGH,
            "live" to TldRiskLevel.HIGH,
            "click" to TldRiskLevel.HIGH,
            "link" to TldRiskLevel.HIGH,
            "work" to TldRiskLevel.HIGH,
            "buzz" to TldRiskLevel.HIGH,
            "kim" to TldRiskLevel.HIGH,
            "country" to TldRiskLevel.HIGH,
            "stream" to TldRiskLevel.HIGH,
            "download" to TldRiskLevel.HIGH,
            "bid" to TldRiskLevel.HIGH,
            "win" to TldRiskLevel.HIGH,
            "racing" to TldRiskLevel.HIGH,
            "accountant" to TldRiskLevel.HIGH,
            "science" to TldRiskLevel.HIGH,
            "party" to TldRiskLevel.HIGH,
            "gdn" to TldRiskLevel.HIGH,

            // MEDIUM: Sometimes suspicious but also legitimate uses
            "info" to TldRiskLevel.MEDIUM,
            "biz" to TldRiskLevel.MEDIUM,
            "pw" to TldRiskLevel.MEDIUM,
            "cc" to TldRiskLevel.MEDIUM,
            "ws" to TldRiskLevel.MEDIUM,

            // LOW: Established, trusted TLDs
            "com" to TldRiskLevel.LOW,
            "org" to TldRiskLevel.LOW,
            "net" to TldRiskLevel.LOW,
            "gov" to TldRiskLevel.LOW,
            "edu" to TldRiskLevel.LOW,
            "mil" to TldRiskLevel.LOW,
            "int" to TldRiskLevel.LOW,

            // Country code TLDs (generally low risk)
            "us" to TldRiskLevel.LOW,
            "uk" to TldRiskLevel.LOW,
            "ca" to TldRiskLevel.LOW,
            "au" to TldRiskLevel.LOW,
            "de" to TldRiskLevel.LOW,
            "fr" to TldRiskLevel.LOW,
            "jp" to TldRiskLevel.LOW,
            "cn" to TldRiskLevel.LOW,
            "in" to TldRiskLevel.LOW,
            "br" to TldRiskLevel.LOW,

            // New gTLDs (low risk if established)
            "app" to TldRiskLevel.LOW,
            "dev" to TldRiskLevel.LOW,
            "io" to TldRiskLevel.LOW,
            "co" to TldRiskLevel.LOW
        )
    }

    /**
     * Evaluate TLD risk level for a domain.
     *
     * @param domain The domain to check (e.g., "example.com")
     * @return TldRiskLevel (CRITICAL, HIGH, MEDIUM, or LOW)
     */
    fun evaluateTldRisk(domain: String): TldRiskLevel {
        val tld = extractTld(domain)
        return TLD_RISK_MAP[tld] ?: TldRiskLevel.LOW
    }

    /**
     * Extract TLD from domain.
     *
     * @param domain The domain (e.g., "example.co.uk" or "test.com")
     * @return TLD string (e.g., "uk" or "com")
     */
    private fun extractTld(domain: String): String {
        val parts = domain.lowercase().split(".")
        return if (parts.isNotEmpty()) parts.last() else ""
    }

    /**
     * Check if TLD is high-risk (CRITICAL or HIGH).
     *
     * @param domain The domain to check
     * @return true if TLD is CRITICAL or HIGH risk
     */
    fun isHighRiskTld(domain: String): Boolean {
        val risk = evaluateTldRisk(domain)
        return risk == TldRiskLevel.CRITICAL || risk == TldRiskLevel.HIGH
    }
}
