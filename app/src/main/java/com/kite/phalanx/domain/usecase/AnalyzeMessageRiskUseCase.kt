package com.kite.phalanx.domain.usecase

import android.content.Context
import com.kite.phalanx.TrustedDomainsPreferences
import com.kite.phalanx.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Use case for analyzing message risk and generating verdicts.
 *
 * Per PRD Phase 1: Risk Engine (rules-only)
 * - Detects security signals from links and domain profiles
 * - Applies deterministic weights to signals
 * - Maps total score to GREEN/AMBER/RED verdict levels
 * - Generates explainable reasons for UI display
 *
 * Signal Weights (deterministic, per PRD):
 * - USERINFO_IN_URL: 100 (CRITICAL → immediate RED)
 * - RAW_IP_HOST: 40
 * - SHORTENER_EXPANDED: 30
 * - HTTP_SCHEME: 25
 * - SUSPICIOUS_PATH: 20
 * - HOMOGLYPH_SUSPECT: 35
 * - PUNYCODE_DOMAIN: 15
 * - NON_STANDARD_PORT: 20
 *
 * Verdict Thresholds (default sensitivity):
 * - GREEN: score < 30
 * - AMBER: 30 <= score < 70
 * - RED: score >= 70 OR any CRITICAL signal
 *
 * @property sensitivityLevel Sensitivity threshold (0-2: low, medium, high)
 */
class AnalyzeMessageRiskUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Signal weights (deterministic, per PRD)
        private const val WEIGHT_USERINFO = 100        // CRITICAL
        private const val WEIGHT_RAW_IP = 40
        private const val WEIGHT_SHORTENER = 30
        private const val WEIGHT_HOMOGLYPH = 35
        private const val WEIGHT_HTTP = 25
        private const val WEIGHT_SUSPICIOUS_PATH = 20
        private const val WEIGHT_NON_STANDARD_PORT = 20
        private const val WEIGHT_PUNYCODE = 15

        // Stage 1B: Enhanced signal weights
        private const val WEIGHT_BRAND_IMPERSONATION_TYPO = 60    // Typosquatting attack
        private const val WEIGHT_BRAND_IMPERSONATION_TLD = 50     // Wrong TLD attack
        private const val WEIGHT_TLD_RISK_CRITICAL = 30           // Free/abused TLDs
        private const val WEIGHT_TLD_RISK_HIGH = 20               // Cheap scam TLDs
        private const val WEIGHT_TLD_RISK_MEDIUM = 10             // Sometimes suspicious
        private const val WEIGHT_EXCESSIVE_REDIRECTS = 25         // >2 redirects
        private const val WEIGHT_SHORTENER_TO_SUSPICIOUS = 40     // Shortener → bad domain

        // Stage 1C: Reputation service weights
        private const val WEIGHT_SAFE_BROWSING_HIT = 90           // Google Safe Browsing flagged
        private const val WEIGHT_URLHAUS_LISTED = 80              // URLhaus malware database hit
        // Note: PhishTank removed as registration is no longer available

        // Verdict thresholds (can be adjusted by sensitivity slider)
        private const val THRESHOLD_GREEN_AMBER = 30
        private const val THRESHOLD_AMBER_RED = 70
    }

    /**
     * Analyze a message with extracted links and domain profiles.
     *
     * @param messageId Unique message identifier
     * @param links List of links extracted from the message
     * @param domainProfiles Domain profiles for each link
     * @param expandedUrls Map of original URLs to ExpandedUrl objects (Stage 1B)
     * @param reputationResults Map of URLs to lists of reputation check results (Stage 1C)
     * @return Verdict with risk level and reasons
     */
    suspend fun execute(
        messageId: String,
        links: List<Link>,
        domainProfiles: List<DomainProfile>,
        expandedUrls: Map<String, ExpandedUrl> = emptyMap(),
        reputationResults: Map<String, List<ReputationResult>> = emptyMap()
    ): Verdict {
        // Check if any domain is in the trusted whitelist
        // If any domain is trusted, bypass all security checks and return GREEN
        val trustedDomains = TrustedDomainsPreferences.getTrustedDomains(context)
        for (profile in domainProfiles) {
            if (trustedDomains.contains(profile.registeredDomain.lowercase())) {
                // Domain is trusted - return GREEN verdict immediately
                return Verdict(
                    messageId = messageId,
                    level = VerdictLevel.GREEN,
                    score = 0,
                    reasons = emptyList()
                )
            }
        }

        // Detect all signals from links and profiles
        val signals = mutableListOf<Signal>()

        links.forEachIndexed { index, link ->
            val profile = domainProfiles.getOrNull(index)
            if (profile != null) {
                // Get reputation results for this URL (check both original and expanded URL)
                val urlReputations = reputationResults[link.original] ?: emptyList()
                signals.addAll(detectSignals(link, profile, expandedUrls, urlReputations))
            }
        }

        // Calculate total risk score
        val totalScore = signals.sumOf { it.weight }

        // Check for critical signals (immediate RED)
        val hasCriticalSignal = signals.any { it.code == SignalCode.USERINFO_IN_URL }

        // Map score to verdict level
        val level = when {
            hasCriticalSignal -> VerdictLevel.RED
            totalScore >= THRESHOLD_AMBER_RED -> VerdictLevel.RED
            totalScore >= THRESHOLD_GREEN_AMBER -> VerdictLevel.AMBER
            else -> VerdictLevel.GREEN
        }

        // Generate human-readable reasons (top signals by weight)
        val reasons = generateReasons(signals)

        return Verdict(
            messageId = messageId,
            level = level,
            score = totalScore,
            reasons = reasons
        )
    }

    /**
     * Detect security signals from a link and its domain profile.
     *
     * Stage 1B: Includes brand impersonation, TLD risk, and redirect chain analysis.
     * Stage 1C: Includes reputation service checks (Safe Browsing, PhishTank, URLhaus).
     */
    private fun detectSignals(
        link: Link,
        profile: DomainProfile,
        expandedUrls: Map<String, ExpandedUrl>,
        reputationResults: List<ReputationResult>
    ): List<Signal> {
        val signals = mutableListOf<Signal>()

        // CRITICAL: User info in URL
        if (profile.hasUserInfo) {
            signals.add(
                Signal(
                    code = SignalCode.USERINFO_IN_URL,
                    weight = WEIGHT_USERINFO,
                    metadata = mapOf("host" to profile.originalHost)
                )
            )
        }

        // Raw IP address
        if (profile.isRawIp) {
            signals.add(
                Signal(
                    code = SignalCode.RAW_IP_HOST,
                    weight = WEIGHT_RAW_IP,
                    metadata = mapOf("host" to profile.originalHost)
                )
            )
        }

        // URL shortener expansion
        val expandedUrl = expandedUrls[link.original]
        if (expandedUrl != null && expandedUrl.finalUrl != link.original) {
            // Check if original was a known shortener
            if (isKnownShortener(link.host)) {
                signals.add(
                    Signal(
                        code = SignalCode.SHORTENER_EXPANDED,
                        weight = WEIGHT_SHORTENER,
                        metadata = mapOf(
                            "original" to link.original,
                            "final" to expandedUrl.finalUrl,
                            "finalDomain" to profile.registeredDomain
                        )
                    )
                )
            }
        }

        // Homoglyph detection
        if (profile.isHomoglyphSuspect) {
            signals.add(
                Signal(
                    code = SignalCode.HOMOGLYPH_SUSPECT,
                    weight = WEIGHT_HOMOGLYPH,
                    metadata = mapOf("host" to profile.originalHost)
                )
            )
        }

        // Insecure HTTP scheme
        if (profile.scheme == "http") {
            signals.add(
                Signal(
                    code = SignalCode.HTTP_SCHEME,
                    weight = WEIGHT_HTTP,
                    metadata = mapOf("url" to link.original)
                )
            )
        }

        // Suspicious paths
        if (profile.suspiciousPaths.isNotEmpty()) {
            signals.add(
                Signal(
                    code = SignalCode.SUSPICIOUS_PATH,
                    weight = WEIGHT_SUSPICIOUS_PATH,
                    metadata = mapOf(
                        "paths" to profile.suspiciousPaths.joinToString(", "),
                        "url" to link.original
                    )
                )
            )
        }

        // Non-standard port
        if (profile.port != null) {
            signals.add(
                Signal(
                    code = SignalCode.NON_STANDARD_PORT,
                    weight = WEIGHT_NON_STANDARD_PORT,
                    metadata = mapOf(
                        "port" to profile.port.toString(),
                        "host" to profile.originalHost
                    )
                )
            )
        }

        // Punycode domain
        if (profile.isPunycode) {
            signals.add(
                Signal(
                    code = SignalCode.PUNYCODE_DOMAIN,
                    weight = WEIGHT_PUNYCODE,
                    metadata = mapOf("host" to profile.originalHost)
                )
            )
        }

        // Stage 1B: Brand impersonation detection
        if (profile.brandImpersonation != null) {
            val impersonation = profile.brandImpersonation
            val weight = when (impersonation.type) {
                ImpersonationType.TYPOSQUATTING -> WEIGHT_BRAND_IMPERSONATION_TYPO
                ImpersonationType.WRONG_TLD, ImpersonationType.KEYWORD_ABUSE -> WEIGHT_BRAND_IMPERSONATION_TLD
            }

            signals.add(
                Signal(
                    code = SignalCode.BRAND_IMPERSONATION,
                    weight = weight,
                    metadata = mapOf(
                        "brand" to impersonation.brandName,
                        "attempted" to impersonation.attemptedDomain,
                        "official" to impersonation.officialDomain,
                        "type" to impersonation.type.name
                    )
                )
            )
        }

        // Stage 1B: TLD risk scoring
        if (profile.tldRiskLevel != TldRiskLevel.LOW) {
            signals.add(
                Signal(
                    code = SignalCode.HIGH_RISK_TLD,
                    weight = profile.tldRiskLevel.weight,
                    metadata = mapOf(
                        "tld" to profile.registeredDomain.substringAfterLast('.'),
                        "riskLevel" to profile.tldRiskLevel.name,
                        "domain" to profile.registeredDomain
                    )
                )
            )
        }

        // Stage 1B: Redirect chain analysis
        if (expandedUrl != null) {
            // Excessive redirects (>2 hops)
            if (expandedUrl.redirectCount > 2) {
                signals.add(
                    Signal(
                        code = SignalCode.EXCESSIVE_REDIRECTS,
                        weight = WEIGHT_EXCESSIVE_REDIRECTS,
                        metadata = mapOf(
                            "count" to expandedUrl.redirectCount.toString(),
                            "chain" to expandedUrl.redirectChain.joinToString(" → ")
                        )
                    )
                )
            }

            // Shortener redirecting to suspicious domain
            if (expandedUrl.redirectCount >= 1 && isKnownShortener(link.host)) {
                // Check if final domain has high-risk signals
                if (profile.tldRiskLevel == TldRiskLevel.CRITICAL ||
                    profile.tldRiskLevel == TldRiskLevel.HIGH ||
                    profile.brandImpersonation != null ||
                    profile.isRawIp
                ) {
                    signals.add(
                        Signal(
                            code = SignalCode.SHORTENER_TO_SUSPICIOUS,
                            weight = WEIGHT_SHORTENER_TO_SUSPICIOUS,
                            metadata = mapOf(
                                "shortener" to link.host,
                                "final" to profile.registeredDomain
                            )
                        )
                    )
                }
            }
        }

        // Stage 1C: Reputation service checks
        reputationResults.forEach { result ->
            if (result.isMalicious) {
                val (code, weight) = when (result.source) {
                    "Google Safe Browsing" -> SignalCode.SAFE_BROWSING_HIT to WEIGHT_SAFE_BROWSING_HIT
                    "URLhaus" -> SignalCode.URLHAUS_LISTED to WEIGHT_URLHAUS_LISTED
                    else -> SignalCode.SAFE_BROWSING_HIT to WEIGHT_SAFE_BROWSING_HIT // Default
                }

                signals.add(
                    Signal(
                        code = code,
                        weight = weight,
                        metadata = mapOf(
                            "source" to result.source,
                            "threatType" to (result.threatType?.name ?: "UNKNOWN"),
                            "url" to link.original
                        ) + result.metadata
                    )
                )
            }
        }

        return signals
    }

    /**
     * Check if a host is a known URL shortener.
     */
    private fun isKnownShortener(host: String): Boolean {
        val shorteners = setOf(
            "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co",
            "is.gd", "buff.ly", "adf.ly", "short.io", "rebrand.ly",
            "cutt.ly", "tiny.cc", "shorturl.at", "s.id", "clck.ru", "v.gd"
        )
        return shorteners.any { shortener ->
            host.equals(shortener, ignoreCase = true) ||
                    host.endsWith(".$shortener", ignoreCase = true)
        }
    }

    /**
     * Generate human-readable reasons from signals.
     *
     * Per PRD: Show top 1-3 reasons in UI (highest weight first).
     */
    private fun generateReasons(signals: List<Signal>): List<Reason> {
        return signals
            .sortedByDescending { it.weight }
            .take(3)
            .map { signal ->
                when (signal.code) {
                    SignalCode.USERINFO_IN_URL -> Reason(
                        code = signal.code,
                        label = "Username in URL",
                        details = "This URL contains login credentials (user:pass@), which is a common phishing technique. Legitimate sites never ask for credentials in URLs."
                    )

                    SignalCode.RAW_IP_HOST -> Reason(
                        code = signal.code,
                        label = "IP Address Link",
                        details = "The link goes to an IP address (${signal.metadata["host"]}) instead of a domain name. Legitimate businesses use domain names, not raw IPs."
                    )

                    SignalCode.SHORTENER_EXPANDED -> Reason(
                        code = signal.code,
                        label = "Shortened URL",
                        details = "This link was shortened to hide its true destination: ${signal.metadata["finalDomain"]}. Be cautious of where it leads."
                    )

                    SignalCode.HOMOGLYPH_SUSPECT -> Reason(
                        code = signal.code,
                        label = "Look-alike Characters",
                        details = "The domain ${signal.metadata["host"]} uses characters that look similar to legitimate letters. This is a common phishing trick."
                    )

                    SignalCode.HTTP_SCHEME -> Reason(
                        code = signal.code,
                        label = "Insecure Connection",
                        details = "This link uses HTTP instead of HTTPS, meaning your data won't be encrypted. Legitimate sites use HTTPS."
                    )

                    SignalCode.SUSPICIOUS_PATH -> Reason(
                        code = signal.code,
                        label = "Suspicious URL Path",
                        details = "The URL contains suspicious keywords: ${signal.metadata["paths"]}. These are often used in phishing attacks."
                    )

                    SignalCode.NON_STANDARD_PORT -> Reason(
                        code = signal.code,
                        label = "Unusual Port",
                        details = "The URL uses port ${signal.metadata["port"]}, which is non-standard. Legitimate websites typically use standard ports."
                    )

                    SignalCode.PUNYCODE_DOMAIN -> Reason(
                        code = signal.code,
                        label = "International Domain",
                        details = "This domain uses international characters (punycode). Verify this is the legitimate site for the claimed sender."
                    )

                    // Stage 1B signals
                    SignalCode.BRAND_IMPERSONATION -> {
                        val brand = signal.metadata["brand"] ?: "unknown"
                        val type = signal.metadata["type"] ?: "unknown"
                        val typeLabel = when (type) {
                            "TYPOSQUATTING" -> "Typosquatting"
                            "WRONG_TLD" -> "Wrong Domain"
                            else -> "Impersonation"
                        }
                        Reason(
                            code = signal.code,
                            label = "$brand $typeLabel",
                            details = "This domain appears to impersonate $brand (${signal.metadata["attempted"]}). The official domain is ${signal.metadata["official"]}. This is a common phishing technique."
                        )
                    }

                    SignalCode.HIGH_RISK_TLD -> {
                        val tld = signal.metadata["tld"] ?: "unknown"
                        val riskLevel = signal.metadata["riskLevel"] ?: "UNKNOWN"
                        val description = when (riskLevel) {
                            "CRITICAL" -> "This domain uses .$tld, a free domain extension heavily abused by scammers."
                            "HIGH" -> "This domain uses .$tld, an extension commonly used in phishing attacks."
                            "MEDIUM" -> "This domain uses .$tld, which is sometimes associated with suspicious activity."
                            else -> "This domain uses a potentially suspicious extension."
                        }
                        Reason(
                            code = signal.code,
                            label = "High-Risk Domain (.$tld)",
                            details = description
                        )
                    }

                    SignalCode.EXCESSIVE_REDIRECTS -> Reason(
                        code = signal.code,
                        label = "Too Many Redirects",
                        details = "This link redirects ${signal.metadata["count"]} times, which is suspicious. Scammers use multiple redirects to hide the true destination."
                    )

                    SignalCode.SHORTENER_TO_SUSPICIOUS -> Reason(
                        code = signal.code,
                        label = "Shortened Link to Suspicious Site",
                        details = "This shortened link (${signal.metadata["shortener"]}) redirects to a suspicious domain: ${signal.metadata["final"]}. Be very cautious."
                    )

                    // Stage 1C: Reputation service signals
                    SignalCode.SAFE_BROWSING_HIT -> {
                        val threatType = signal.metadata["threatType"] ?: "UNKNOWN"
                        val description = when (threatType) {
                            "MALWARE" -> "Google Safe Browsing has flagged this link as malware. Opening it could infect your device."
                            "SOCIAL_ENGINEERING" -> "Google Safe Browsing has identified this as a phishing site designed to steal your information."
                            "UNWANTED_SOFTWARE" -> "Google Safe Browsing warns this site may install unwanted software on your device."
                            "POTENTIALLY_HARMFUL" -> "Google Safe Browsing has flagged this link as potentially harmful."
                            else -> "Google Safe Browsing has flagged this link as dangerous."
                        }
                        Reason(
                            code = signal.code,
                            label = "Dangerous Link (Google)",
                            details = description
                        )
                    }

                    SignalCode.URLHAUS_LISTED -> Reason(
                        code = signal.code,
                        label = "Malware Distribution Site",
                        details = "URLhaus has identified this link as distributing malware. Opening it could compromise your device security."
                    )

                    else -> Reason(
                        code = signal.code,
                        label = signal.code.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        details = "Security flag detected. Exercise caution with this link."
                    )
                }
            }
    }
}
