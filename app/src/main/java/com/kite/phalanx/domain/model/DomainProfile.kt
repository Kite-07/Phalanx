package com.kite.phalanx.domain.model

/**
 * Profile of a domain with risk signals.
 *
 * @property registeredDomain The registered domain (from PSL)
 * @property scheme URL scheme (http, https, etc.)
 * @property port Port number (if non-standard)
 * @property hasUserInfo Whether URL contains user:pass@ component
 * @property isPunycode Whether domain uses punycode (xn--)
 * @property isRawIp Whether host is a raw IP address
 * @property suspiciousPaths List of suspicious path segments detected
 * @property isHomoglyphSuspect Whether domain contains homoglyph characters
 * @property originalHost The original host string
 * @property brandImpersonation Brand impersonation details (Stage 1B)
 * @property tldRiskLevel Risk level of the TLD (Stage 1B)
 */
data class DomainProfile(
    val registeredDomain: String,
    val scheme: String,
    val port: Int?,
    val hasUserInfo: Boolean,
    val isPunycode: Boolean,
    val isRawIp: Boolean,
    val suspiciousPaths: List<String>,
    val isHomoglyphSuspect: Boolean,
    val originalHost: String,
    val brandImpersonation: BrandImpersonation? = null,
    val tldRiskLevel: TldRiskLevel = TldRiskLevel.LOW
)

/**
 * Brand impersonation detection result.
 *
 * @property brandName Name of the impersonated brand
 * @property attemptedDomain The suspicious domain attempting impersonation
 * @property officialDomain The official domain being impersonated
 * @property type Type of impersonation (typosquatting, wrong TLD, etc.)
 */
data class BrandImpersonation(
    val brandName: String,
    val attemptedDomain: String,
    val officialDomain: String,
    val type: ImpersonationType
)

/**
 * Type of brand impersonation attack.
 */
enum class ImpersonationType {
    TYPOSQUATTING,    // Similar domain with typos (paypa1.com)
    WRONG_TLD,        // Brand name with wrong TLD (amazon-verify.tk)
    KEYWORD_ABUSE     // Brand keyword in suspicious domain
}

/**
 * TLD risk level based on abuse patterns.
 */
enum class TldRiskLevel(val weight: Int) {
    CRITICAL(30),  // Free TLDs heavily abused (.tk, .ml, .ga, .cf, .gq)
    HIGH(20),      // Often used in phishing (.xyz, .top, .club)
    MEDIUM(10),    // Sometimes suspicious (.info, .biz)
    LOW(0)         // Generally safe (.com, .org, .gov, .edu)
}
