package com.kite.phalanx.domain.model

/**
 * Result of checking a URL against a reputation service.
 *
 * Stage 1C Enhancement: Reputation Services
 * Returned by Google Safe Browsing, PhishTank, and URLhaus APIs.
 */
data class ReputationResult(
    /**
     * Whether the URL is known to be malicious.
     */
    val isMalicious: Boolean,

    /**
     * Type of threat detected (if any).
     */
    val threatType: ThreatType?,

    /**
     * Source service that provided this result (e.g., "Google Safe Browsing", "PhishTank").
     */
    val source: String,

    /**
     * Timestamp when this result was obtained.
     */
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Additional metadata about the threat (optional).
     */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Types of threats detected by reputation services.
 */
enum class ThreatType {
    /**
     * Malware distribution site.
     */
    MALWARE,

    /**
     * Phishing/social engineering site.
     */
    SOCIAL_ENGINEERING,

    /**
     * Unwanted software distribution.
     */
    UNWANTED_SOFTWARE,

    /**
     * Potentially harmful application.
     */
    POTENTIALLY_HARMFUL,

    /**
     * Unknown threat type.
     */
    UNKNOWN
}
