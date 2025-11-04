package com.kite.phalanx.domain.usecase

import com.ibm.icu.text.IDNA
import com.kite.phalanx.domain.model.Link
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Use case for extracting and normalizing URLs from SMS message text.
 *
 * Features:
 * - Detects URLs with and without schemes
 * - Unicode normalization using ICU4J
 * - Handles scheme-less URLs (e.g., bit.ly/abc)
 * - Multiple links per message
 * - Punycode handling
 *
 * Target: ≥98% recall, 0 false positives on plain text
 */
class ExtractLinksUseCase @Inject constructor() {

    companion object {
        // URL patterns
        private const val SCHEME_PATTERN = """(?:https?|ftp)://"""
        private const val USERINFO_PATTERN = """(?:[a-zA-Z0-9._~!$&'()*+,;=:-]+(?::[a-zA-Z0-9._~!$&'()*+,;=:-]*)?@)?"""
        private const val HOST_PATTERN = """(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"""
        private const val IP_PATTERN = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""
        private const val IPV6_PATTERN = """\[(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\]"""
        private const val PORT_PATTERN = """(?::\d{1,5})?"""
        private const val PATH_PATTERN = """(?:/[^\s]*)?"""

        // Complete URL pattern (with scheme)
        private val URL_WITH_SCHEME_REGEX = Regex(
            """$SCHEME_PATTERN$USERINFO_PATTERN(?:$HOST_PATTERN|$IP_PATTERN|$IPV6_PATTERN)$PORT_PATTERN$PATH_PATTERN""",
            RegexOption.IGNORE_CASE
        )

        // Scheme-less URL pattern (e.g., bit.ly/abc, example.com)
        // Must have TLD and at least one path component or www prefix to avoid false positives
        private val SCHEMELESS_URL_REGEX = Regex(
            """(?:^|[\s])((?:www\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)|(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+(?:com|org|net|edu|gov|co|io|ly|me|info|biz|app|dev|ai|tech|online|xyz|link|click|tk|ml|ga|cf|gq|gl|de)))(?:/[^\s]*)?""",
            RegexOption.IGNORE_CASE
        )

        // Common URL shorteners for additional detection
        private val SHORTENER_DOMAINS = setOf(
            "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co", "is.gd", "buff.ly",
            "adf.ly", "short.io", "rebrand.ly", "cutt.ly", "tiny.cc", "shorturl.at",
            "s.id", "clck.ru", "v.gd"
        )
    }

    /**
     * Extract all URLs from the given message text.
     *
     * @param messageText The SMS message body
     * @return List of extracted and normalized Link objects
     */
    suspend fun execute(messageText: String): List<Link> {
        if (messageText.isBlank()) return emptyList()

        val links = mutableListOf<Link>()

        // Extract URLs with schemes
        URL_WITH_SCHEME_REGEX.findAll(messageText).forEach { match ->
            val originalUrl = match.value.trim()
            try {
                val normalized = normalizeUrl(originalUrl)
                val link = parseUrl(originalUrl, normalized)
                links.add(link)
            } catch (e: Exception) {
                // Skip malformed URLs
            }
        }

        // Extract scheme-less URLs
        SCHEMELESS_URL_REGEX.findAll(messageText).forEach { match ->
            // Use capturing group to exclude leading whitespace
            val originalUrl = match.groupValues.getOrNull(1)?.trim() ?: match.value.trim()
            if (originalUrl.isNotEmpty()) {
                try {
                    // Add https:// scheme for parsing
                    val withScheme = if (originalUrl.startsWith("www.")) {
                        "https://$originalUrl"
                    } else {
                        "https://$originalUrl"
                    }
                    val normalized = normalizeUrl(withScheme)
                    val link = parseUrl(originalUrl, normalized)
                    links.add(link)
                } catch (e: Exception) {
                    // Skip malformed URLs
                }
            }
        }

        return links.distinctBy { it.normalized }
    }

    /**
     * Normalize a URL using Unicode normalization and URL decoding.
     */
    private fun normalizeUrl(url: String): String {
        try {
            // Decode percent-encoded characters
            var normalized = try {
                URLDecoder.decode(url, "UTF-8")
            } catch (e: Exception) {
                url
            }

            // First convert Unicode host to punycode if needed
            // Extract host from URL before parsing
            val hostRegex = Regex("""://(?:[^@]+@)?([^/:]+)""")
            val hostMatch = hostRegex.find(normalized)
            val punycodeHost = if (hostMatch != null) {
                val originalHost = hostMatch.groupValues[1]
                try {
                    IDNA.getUTS46Instance(IDNA.DEFAULT).nameToASCII(originalHost, StringBuilder(), IDNA.Info()).toString()
                } catch (e: Exception) {
                    originalHost
                }
            } else {
                null
            }

            // Replace the host with punycode version if different
            if (punycodeHost != null && hostMatch != null) {
                normalized = normalized.replaceRange(hostMatch.groups[1]!!.range, punycodeHost)
            }

            // Parse URI to extract components
            val uri = URI(normalized)

            // Reconstruct normalized URL
            normalized = buildString {
                append(uri.scheme ?: "https")
                append("://")
                if (uri.userInfo != null) {
                    append(uri.userInfo)
                    append("@")
                }
                append(uri.host ?: "")
                if (uri.port > 0 && uri.port != 80 && uri.port != 443) {
                    append(":")
                    append(uri.port)
                }
                append(uri.path ?: "")
                if (uri.query != null) {
                    append("?")
                    append(uri.query)
                }
                if (uri.fragment != null) {
                    append("#")
                    append(uri.fragment)
                }
            }

            return normalized.lowercase()
        } catch (e: Exception) {
            return url.lowercase()
        }
    }

    /**
     * Parse a URL into a Link object with all components.
     */
    private fun parseUrl(originalUrl: String, normalizedUrl: String): Link {
        val uri = try {
            URI(normalizedUrl)
        } catch (e: Exception) {
            // Fallback for scheme-less URLs
            URI("https://$normalizedUrl")
        }

        val host = uri.host ?: ""
        val scheme = uri.scheme ?: "https"
        val port = if (uri.port > 0) uri.port else null
        val path = uri.path ?: ""
        val hasUserInfo = uri.userInfo != null

        // Parse query parameters
        val params = mutableMapOf<String, String>()
        uri.query?.split("&")?.forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                params[parts[0]] = parts[1]
            } else {
                params[parts[0]] = ""
            }
        }

        return Link(
            original = originalUrl,
            normalized = normalizedUrl,
            finalUrl = null, // Will be populated by URL expander
            host = host,
            registeredDomain = "", // Will be populated by domain profiler
            scheme = scheme,
            port = port,
            path = path,
            params = params,
            hasUserInfo = hasUserInfo
        )
    }

    /**
     * Check if a URL is from a known shortener service.
     */
    fun isShortenerUrl(host: String): Boolean {
        return SHORTENER_DOMAINS.any { shortener ->
            host.equals(shortener, ignoreCase = true) ||
                    host.endsWith(".$shortener", ignoreCase = true)
        }
    }
}
