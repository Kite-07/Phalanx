package com.kite.phalanx.domain.model

/**
 * Sender Intelligence Pack - Contains verified sender patterns for a specific region.
 * Used to detect SENDER_MISMATCH when message content claims to be from a known entity
 * but the sender doesn't match expected patterns.
 *
 * Format: Signed JSON with Ed25519 signature for authenticity.
 */
data class SenderPack(
    /**
     * Region code (e.g., "IN" for India, "US" for United States)
     * Determines which pack to load based on user's region setting
     */
    val region: String,

    /**
     * Pack version number (increments with updates)
     * Format: YYYYMMDD (e.g., 20250108 for January 8, 2025)
     */
    val version: Long,

    /**
     * List of verified sender entries (carriers, banks, government, etc.)
     */
    val entries: List<SenderPackEntry>,

    /**
     * Ed25519 signature for pack authenticity
     * Hex-encoded signature bytes
     */
    val signature: String,

    /**
     * Unix timestamp (milliseconds) when pack was created
     */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Single entry in a sender pack representing a known sender
 */
data class SenderPackEntry(
    /**
     * Regex pattern matching the expected sender ID
     * Examples:
     * - Carrier: "^[A-Z]{2}-[A-Z]{6}$" (e.g., "AX-AIRTEL")
     * - Bank: "^[A-Z]{6}$" (e.g., "HDFCBK", "ICICIB")
     * - Government: "^GOVJOB$", "^EPFOHO$"
     */
    val pattern: String,

    /**
     * Human-readable brand name
     * Examples: "Airtel", "HDFC Bank", "ICICI Bank", "Indian Railways"
     */
    val brand: String,

    /**
     * Sender type for categorization
     */
    val type: SenderType,

    /**
     * Optional keywords that might appear in legitimate messages from this sender
     * Helps reduce false positives when message mentions the brand
     */
    val keywords: List<String> = emptyList()
)

/**
 * Categories of known senders
 */
enum class SenderType {
    /**
     * Mobile network carriers (e.g., Airtel, Jio, Vodafone)
     */
    CARRIER,

    /**
     * Banks and financial institutions
     */
    BANK,

    /**
     * Government agencies and departments
     */
    GOVERNMENT,

    /**
     * E-commerce platforms
     */
    ECOMMERCE,

    /**
     * Food delivery and ride-sharing services
     */
    SERVICE,

    /**
     * Payment gateways and wallets
     */
    PAYMENT,

    /**
     * Other verified senders
     */
    OTHER
}

/**
 * Result of sender pack signature verification
 */
data class PackVerificationResult(
    /**
     * Whether the signature is valid
     */
    val isValid: Boolean,

    /**
     * Error message if verification failed
     */
    val errorMessage: String? = null,

    /**
     * Verified pack (null if invalid)
     */
    val pack: SenderPack? = null
)
