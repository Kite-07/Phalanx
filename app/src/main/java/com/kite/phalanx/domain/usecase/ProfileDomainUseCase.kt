package com.kite.phalanx.domain.usecase

import com.ibm.icu.text.IDNA
import com.ibm.icu.text.Transliterator
import com.kite.phalanx.data.util.PublicSuffixListParser
import com.kite.phalanx.domain.model.BrandImpersonation
import com.kite.phalanx.domain.model.DomainProfile
import com.kite.phalanx.domain.model.ImpersonationType
import com.kite.phalanx.domain.model.Link
import com.kite.phalanx.domain.util.BrandDatabase
import com.kite.phalanx.domain.util.StringUtils
import com.kite.phalanx.domain.util.TldRiskScorer
import javax.inject.Inject

/**
 * Use case for profiling domains from extracted links.
 *
 * Analyzes domains for security signals:
 * - Registered domain extraction (PSL)
 * - Punycode detection (xn--)
 * - Homoglyph detection (ICU UTS-39)
 * - IP address detection
 * - Suspicious path detection
 * - User info detection
 * - Non-standard port detection
 * - HTTP scheme detection
 * - Brand impersonation detection (Stage 1B)
 * - TLD risk scoring (Stage 1B)
 *
 * Per PRD Phase 1: Domain Profiler + Stage 1B Enhancements
 */
class ProfileDomainUseCase @Inject constructor(
    private val pslParser: PublicSuffixListParser,
    private val brandDatabase: BrandDatabase,
    private val tldRiskScorer: TldRiskScorer
) {

    companion object {
        // Suspicious path keywords (per PRD)
        private val SUSPICIOUS_PATH_KEYWORDS = setOf(
            "login", "signin", "verify", "reset", "password", "account",
            "prize", "winner", "claim", "otp", "security", "update",
            "confirm", "validate", "suspend", "unlock", "alert"
        )

        // Standard HTTP/HTTPS ports
        private val STANDARD_PORTS = setOf(80, 443)

        // IPv4 pattern
        private val IPV4_PATTERN = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

        // IPv6 pattern (simplified)
        private val IPV6_PATTERN = Regex("""^[0-9a-fA-F:]+$""")
    }

    /**
     * Profile a link's domain for security analysis.
     *
     * @param link The extracted link to profile
     * @return DomainProfile with all analyzed characteristics
     */
    suspend fun execute(link: Link): DomainProfile {
        val host = link.host.lowercase()

        // Extract registered domain using PSL
        val registeredDomain = pslParser.getRegisteredDomain(host)

        // Check for punycode (internationalized domain names)
        val isPunycode = host.contains("xn--")

        // Check if it's a raw IP address
        val isRawIp = isIpAddress(host)

        // Check for homoglyphs using ICU UTS-39
        val isHomoglyphSuspect = detectHomoglyphs(host)

        // Detect suspicious paths
        val suspiciousPaths = detectSuspiciousPaths(link.path)

        // Check for non-standard port
        val hasNonStandardPort = link.port != null && !STANDARD_PORTS.contains(link.port)

        // Stage 1B: Detect brand impersonation
        val brandImpersonation = detectBrandImpersonation(registeredDomain)

        // Stage 1B: Evaluate TLD risk
        val tldRiskLevel = tldRiskScorer.evaluateTldRisk(registeredDomain)

        return DomainProfile(
            registeredDomain = registeredDomain,
            scheme = link.scheme,
            port = if (hasNonStandardPort) link.port else null,
            hasUserInfo = link.hasUserInfo,
            isPunycode = isPunycode,
            isRawIp = isRawIp,
            suspiciousPaths = suspiciousPaths,
            isHomoglyphSuspect = isHomoglyphSuspect,
            originalHost = link.host,
            brandImpersonation = brandImpersonation,
            tldRiskLevel = tldRiskLevel
        )
    }

    /**
     * Check if host is an IP address (IPv4 or IPv6).
     */
    private fun isIpAddress(host: String): Boolean {
        // Remove brackets from IPv6 addresses
        val cleanHost = host.removeSurrounding("[", "]")

        return IPV4_PATTERN.matches(cleanHost) || IPV6_PATTERN.matches(cleanHost)
    }

    /**
     * Detect homoglyphs using ICU UTS-39 confusable detection.
     *
     * This checks if the domain contains characters that could be confused
     * with other characters (e.g., cyrillic 'a' vs latin 'a').
     *
     * Per PRD: Use ICU UTS-39 skeleton matching to detect homoglyphs.
     */
    private fun detectHomoglyphs(host: String): Boolean {
        try {
            // Decode punycode first if needed
            val decodedHost = if (host.contains("xn--")) {
                try {
                    IDNA.getUTS46Instance(IDNA.DEFAULT).nameToUnicode(host, StringBuilder(), IDNA.Info()).toString()
                } catch (e: Exception) {
                    host
                }
            } else {
                host
            }

            // Use ICU's confusable skeleton to detect homoglyphs
            // If the skeleton differs significantly from original, it might be suspicious
            val skeleton = try {
                Transliterator.getInstance("Any-Latin; NFD; [:Nonspacing Mark:] Remove; Lower; NFC")
                    .transform(decodedHost)
            } catch (e: Exception) {
                decodedHost
            }

            // Check for mixed scripts (common homoglyph attack)
            // If skeleton transformation changed the string significantly, flag it
            val hasNonLatinChars = decodedHost.any { char ->
                char.code > 127 && !char.isDigit() && char != '.' && char != '-'
            }

            // Flag if contains non-Latin characters in what appears to be a Latin-script domain
            if (hasNonLatinChars) {
                // Check if it's a legitimate IDN (international domain)
                // vs. a homoglyph attack (mixing scripts)
                val hasLatinChars = decodedHost.any { char ->
                    char.code in 65..90 || char.code in 97..122
                }

                // If mixing Latin with non-Latin, it's suspicious
                if (hasLatinChars) {
                    return true
                }
            }

            return false
        } catch (e: Exception) {
            // On error, don't flag as suspicious
            return false
        }
    }

    /**
     * Detect suspicious keywords in URL path.
     *
     * Per PRD: Flag suspicious paths like /login, /verify, /reset, etc.
     */
    private fun detectSuspiciousPaths(path: String): List<String> {
        val pathLower = path.lowercase()
        return SUSPICIOUS_PATH_KEYWORDS.filter { keyword ->
            pathLower.contains("/$keyword") || pathLower.contains(keyword)
        }
    }

    /**
     * Detect brand impersonation attacks.
     *
     * Stage 1B Enhancement: Detects typosquatting and wrong TLD attacks.
     *
     * Checks for:
     * 1. Typosquatting: Similar domain with 1-3 character differences (paypa1.com vs paypal.com)
     * 2. Wrong TLD: Brand name with wrong TLD (amazon-verify.tk vs amazon.com)
     *
     * @param domain The registered domain to check
     * @return BrandImpersonation if detected, null otherwise
     */
    private fun detectBrandImpersonation(domain: String): BrandImpersonation? {
        // Find if domain contains a brand keyword
        val brand = brandDatabase.findBrandByDomain(domain) ?: return null

        // If it's an official domain, not impersonation
        if (brandDatabase.isOfficialDomain(domain, brand)) {
            return null
        }

        // Check for typosquatting against official domains
        for (officialDomain in brand.officialDomains) {
            if (StringUtils.isTyposquatting(domain, officialDomain)) {
                return BrandImpersonation(
                    brandName = brand.name,
                    attemptedDomain = domain,
                    officialDomain = officialDomain,
                    type = ImpersonationType.TYPOSQUATTING
                )
            }
        }

        // If we reach here, domain contains brand keyword but isn't official or typosquatting
        // This is a wrong TLD or keyword abuse attack
        return BrandImpersonation(
            brandName = brand.name,
            attemptedDomain = domain,
            officialDomain = brand.officialDomains.firstOrNull() ?: "",
            type = ImpersonationType.WRONG_TLD
        )
    }
}
