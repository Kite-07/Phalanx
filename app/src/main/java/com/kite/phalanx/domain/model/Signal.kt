package com.kite.phalanx.domain.model

/**
 * A security signal detected during message analysis.
 *
 * Per PRD: Signals are individual risk indicators with weights.
 * Multiple signals are combined to produce a final verdict.
 *
 * @property code Unique signal identifier (e.g., "SHORTENER_EXPANDED")
 * @property weight Risk weight contribution (deterministic, not affected by sensitivity)
 * @property metadata Additional context about the signal
 */
data class Signal(
    val code: SignalCode,
    val weight: Int,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Standard signal codes per PRD Phase 1.
 */
enum class SignalCode {
    // URL Expansion signals
    SHORTENER_EXPANDED,          // URL was expanded from known shortener
    EXCESSIVE_REDIRECTS,         // More than 2 redirects in chain (Stage 1B)
    SHORTENER_TO_SUSPICIOUS,     // Shortener redirects to suspicious domain (Stage 1B)

    // Domain signals
    HOMOGLYPH_SUSPECT,           // Domain contains homoglyph characters (ICU UTS-39)
    PUNYCODE_DOMAIN,             // Domain uses punycode (xn--)
    RAW_IP_HOST,                 // Host is raw IP address (IPv4/IPv6)
    BRAND_IMPERSONATION,         // Domain impersonates known brand (Stage 1B)
    HIGH_RISK_TLD,               // Domain uses high-risk/abused TLD (Stage 1B)

    // Scheme/Protocol signals
    HTTP_SCHEME,                 // Uses insecure HTTP

    // Critical signals
    USERINFO_IN_URL,             // Contains user:pass@host (CRITICAL → RED)

    // Reputation signals (Stage 1C)
    SAFE_BROWSING_HIT,           // URL flagged by Google Safe Browsing API
    URLHAUS_LISTED,              // URL found in URLhaus malware database
    // Note: PhishTank removed as registration is no longer available

    // Infrastructure signals
    NON_STANDARD_PORT,           // Port is not 80 or 443

    // Path signals
    SUSPICIOUS_PATH,             // Path contains suspicious keywords

    // Content signals (Phase 1)
    ZERO_WIDTH_CHARS,            // Message contains zero-width characters
    WEIRD_CAPS,                  // Unusual capitalization patterns
    EXCESSIVE_UNICODE,           // Excessive Unicode/emoji usage

    // Sender signals (requires sender intelligence packs - Phase 4)
    SENDER_MISMATCH              // Claimed brand doesn't match actual sender
}
