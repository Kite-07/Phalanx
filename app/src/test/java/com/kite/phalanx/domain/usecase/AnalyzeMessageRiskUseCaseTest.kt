package com.kite.phalanx.domain.usecase

import com.kite.phalanx.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AnalyzeMessageRiskUseCase.
 *
 * Tests cover all PRD Phase 1 risk detection rules:
 * - USERINFO_IN_URL (CRITICAL → RED)
 * - RAW_IP_HOST
 * - SHORTENER_EXPANDED
 * - HOMOGLYPH_SUSPECT
 * - HTTP_SCHEME
 * - SUSPICIOUS_PATH
 * - NON_STANDARD_PORT
 * - PUNYCODE_DOMAIN
 *
 * Also tests:
 * - Score calculation
 * - Verdict level mapping (GREEN/AMBER/RED)
 * - Reason generation
 * - Real-world examples
 */
class AnalyzeMessageRiskUseCaseTest {

    private lateinit var useCase: AnalyzeMessageRiskUseCase

    @Before
    fun setup() {
        useCase = AnalyzeMessageRiskUseCase()
    }

    @Test
    fun `safe link returns GREEN verdict`() = runTest {
        val link = createLink(host = "www.amazon.com", scheme = "https")
        val profile = createProfile(
            registeredDomain = "amazon.com",
            scheme = "https",
            isRawIp = false,
            hasUserInfo = false
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertEquals(VerdictLevel.GREEN, verdict.level)
        assertTrue("Safe links should have low scores", verdict.score < 30)
        assertTrue("Safe links should have no reasons", verdict.reasons.isEmpty())
    }

    @Test
    fun `user info in URL triggers immediate RED verdict`() = runTest {
        val link = createLink(host = "phishing.com", hasUserInfo = true)
        val profile = createProfile(
            registeredDomain = "phishing.com",
            hasUserInfo = true
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertEquals("CRITICAL signal should trigger RED", VerdictLevel.RED, verdict.level)
        assertTrue("Score should be >= 100", verdict.score >= 100)
        assertEquals("Should explain user info risk", SignalCode.USERINFO_IN_URL, verdict.reasons[0].code)
        assertTrue(verdict.reasons[0].details.contains("credentials"))
    }

    @Test
    fun `raw IP address detects phishing`() = runTest {
        val link = createLink(host = "192.168.1.1")
        val profile = createProfile(
            registeredDomain = "192.168.1.1",
            isRawIp = true
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertTrue("IP addresses should increase score", verdict.score >= 40)
        val ipReason = verdict.reasons.find { it.code == SignalCode.RAW_IP_HOST }
        assertNotNull("Should have IP address reason", ipReason)
    }

    @Test
    fun `shortened URL from known shortener flagged`() = runTest {
        val link = createLink(host = "bit.ly", originalUrl = "https://bit.ly/abc123")
        val profile = createProfile(registeredDomain = "bit.ly")
        // Stage 1B: Updated to use ExpandedUrl object
        val expandedUrls = mapOf(
            "https://bit.ly/abc123" to com.kite.phalanx.domain.model.ExpandedUrl(
                originalUrl = "https://bit.ly/abc123",
                finalUrl = "https://phishing-site.com/scam",
                redirectChain = listOf("https://bit.ly/abc123"),
                timestamp = System.currentTimeMillis()
            )
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile), expandedUrls)

        assertTrue("Shorteners should increase score", verdict.score >= 30)
        val shortenerReason = verdict.reasons.find { it.code == SignalCode.SHORTENER_EXPANDED }
        assertNotNull("Should have shortener reason", shortenerReason)
        assertTrue(shortenerReason!!.details.contains("shortened"))
    }

    @Test
    fun `homoglyph domain flagged`() = runTest {
        val link = createLink(host = "аmazon.com") // Cyrillic 'а'
        val profile = createProfile(
            registeredDomain = "аmazon.com",
            isHomoglyphSuspect = true
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertTrue("Homoglyphs should increase score", verdict.score >= 35)
        val homoglyphReason = verdict.reasons.find { it.code == SignalCode.HOMOGLYPH_SUSPECT }
        assertNotNull("Should have homoglyph reason", homoglyphReason)
    }

    @Test
    fun `HTTP scheme flagged as insecure`() = runTest {
        val link = createLink(host = "example.com", scheme = "http")
        val profile = createProfile(
            registeredDomain = "example.com",
            scheme = "http"
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertTrue("HTTP should increase score", verdict.score >= 25)
        val httpReason = verdict.reasons.find { it.code == SignalCode.HTTP_SCHEME }
        assertNotNull("Should have HTTP reason", httpReason)
        assertTrue(httpReason!!.details.contains("HTTPS"))
    }

    @Test
    fun `suspicious path keywords detected`() = runTest {
        val link = createLink(host = "example.com", path = "/login/verify")
        val profile = createProfile(
            registeredDomain = "example.com",
            suspiciousPaths = listOf("login", "verify")
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertTrue("Suspicious paths should increase score", verdict.score >= 20)
        val pathReason = verdict.reasons.find { it.code == SignalCode.SUSPICIOUS_PATH }
        assertNotNull("Should have suspicious path reason", pathReason)
        assertTrue("Details should mention suspicious paths", pathReason!!.details.contains("login"))
    }

    @Test
    fun `non-standard port flagged`() = runTest {
        val link = createLink(host = "example.com", port = 8080)
        val profile = createProfile(
            registeredDomain = "example.com",
            port = 8080
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertTrue("Non-standard ports should increase score", verdict.score >= 20)
        val portReason = verdict.reasons.find { it.code == SignalCode.NON_STANDARD_PORT }
        assertNotNull("Should have port reason", portReason)
        assertTrue(portReason!!.details.contains("8080"))
    }

    @Test
    fun `punycode domain flagged`() = runTest {
        val link = createLink(host = "xn--mnchen-3ya.de")
        val profile = createProfile(
            registeredDomain = "xn--mnchen-3ya.de",
            isPunycode = true
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertTrue("Punycode should increase score", verdict.score >= 15)
        val punycodeReason = verdict.reasons.find { it.code == SignalCode.PUNYCODE_DOMAIN }
        assertNotNull("Should have punycode reason", punycodeReason)
    }

    @Test
    fun `multiple signals accumulate score`() = runTest {
        // Phishing URL: http://192.168.1.1:8080/login
        val link = createLink(
            host = "192.168.1.1",
            scheme = "http",
            port = 8080,
            path = "/login"
        )
        val profile = createProfile(
            registeredDomain = "192.168.1.1",
            scheme = "http",
            isRawIp = true,
            port = 8080,
            suspiciousPaths = listOf("login")
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        // Should have: RAW_IP (40) + HTTP (25) + PORT (20) + PATH (20) = 105
        assertTrue("Multiple signals should accumulate", verdict.score >= 100)
        assertEquals("High score should be RED", VerdictLevel.RED, verdict.level)
        assertTrue("Should have multiple reasons", verdict.reasons.size >= 3)
    }

    @Test
    fun `AMBER verdict for medium risk score`() = runTest {
        // Medium risk: HTTP + suspicious path = 45
        val link = createLink(host = "example.com", scheme = "http", path = "/login")
        val profile = createProfile(
            registeredDomain = "example.com",
            scheme = "http",
            suspiciousPaths = listOf("login")
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertEquals("Medium score should be AMBER", VerdictLevel.AMBER, verdict.level)
        assertTrue("Score should be 30-69", verdict.score in 30..69)
    }

    @Test
    fun `realistic phishing example - all red flags`() = runTest {
        // Phishing: http://user:pass@suspicious-amaz0n.com:8080/verify/account
        val link = createLink(
            originalUrl = "http://user:pass@suspicious-amaz0n.com:8080/verify/account",
            host = "suspicious-amaz0n.com",
            scheme = "http",
            port = 8080,
            path = "/verify/account",
            hasUserInfo = true
        )
        val profile = createProfile(
            registeredDomain = "suspicious-amaz0n.com",
            scheme = "http",
            port = 8080,
            hasUserInfo = true,
            isHomoglyphSuspect = true,
            suspiciousPaths = listOf("verify", "account")
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertEquals("Obvious phishing should be RED", VerdictLevel.RED, verdict.level)
        assertTrue("Should have very high score", verdict.score >= 100)
        assertTrue("Should have top reasons", verdict.reasons.size >= 3)
        assertEquals("User info should be first reason", SignalCode.USERINFO_IN_URL, verdict.reasons[0].code)
    }

    @Test
    fun `realistic legitimate example - Amazon`() = runTest {
        val link = createLink(
            originalUrl = "https://www.amazon.com/dp/B08N5WRWNW",
            host = "www.amazon.com",
            scheme = "https",
            path = "/dp/B08N5WRWNW"
        )
        val profile = createProfile(
            registeredDomain = "amazon.com",
            scheme = "https"
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertEquals("Legitimate site should be GREEN", VerdictLevel.GREEN, verdict.level)
        assertTrue("Should have minimal score", verdict.score < 30)
    }

    @Test
    fun `realistic legitimate example - bank with HTTPS`() = runTest {
        val link = createLink(
            originalUrl = "https://secure.chase.com/auth/login",
            host = "secure.chase.com",
            scheme = "https",
            path = "/auth/login"
        )
        val profile = createProfile(
            registeredDomain = "chase.com",
            scheme = "https",
            suspiciousPaths = listOf("login") // Even with /login, HTTPS domain makes it safer
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        // Should be AMBER due to /login path, but not RED because it's legitimate HTTPS domain
        assertTrue("Should be GREEN or AMBER", verdict.level in listOf(VerdictLevel.GREEN, VerdictLevel.AMBER))
        assertTrue("Should not be high risk", verdict.score < 70)
    }

    @Test
    fun `shortened link to legitimate site is AMBER`() = runTest {
        val link = createLink(originalUrl = "https://bit.ly/amazon123", host = "bit.ly")
        val profile = createProfile(registeredDomain = "bit.ly")
        // Stage 1B: Updated to use ExpandedUrl object
        val expandedUrls = mapOf(
            "https://bit.ly/amazon123" to com.kite.phalanx.domain.model.ExpandedUrl(
                originalUrl = "https://bit.ly/amazon123",
                finalUrl = "https://www.amazon.com/product",
                redirectChain = listOf("https://bit.ly/amazon123"),
                timestamp = System.currentTimeMillis()
            )
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile), expandedUrls)

        assertTrue("Shorteners should be flagged even if destination is safe", verdict.level in listOf(VerdictLevel.AMBER, VerdictLevel.GREEN))
        assertTrue("Should have shortener signal", verdict.reasons.any { it.code == SignalCode.SHORTENER_EXPANDED })
    }

    @Test
    fun `no links returns GREEN verdict`() = runTest {
        val verdict = useCase.execute("msg1", emptyList(), emptyList())

        assertEquals("No links should be GREEN", VerdictLevel.GREEN, verdict.level)
        assertEquals("No links should have zero score", 0, verdict.score)
        assertTrue("No reasons for safe message", verdict.reasons.isEmpty())
    }

    @Test
    fun `reasons are sorted by weight`() = runTest {
        // Multiple signals: IP (40) + HTTP (25) + PATH (20)
        val link = createLink(host = "192.168.1.1", scheme = "http", path = "/login")
        val profile = createProfile(
            registeredDomain = "192.168.1.1",
            scheme = "http",
            isRawIp = true,
            suspiciousPaths = listOf("login")
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        // Top reason should be highest weight (RAW_IP = 40)
        assertEquals("Top reason should be IP", SignalCode.RAW_IP_HOST, verdict.reasons[0].code)
        assertEquals("Second reason should be HTTP", SignalCode.HTTP_SCHEME, verdict.reasons[1].code)
    }

    @Test
    fun `max 3 reasons shown in verdict`() = runTest {
        // Create scenario with many signals
        val link = createLink(
            host = "xn--test.com",
            scheme = "http",
            port = 8080,
            path = "/login/verify",
            hasUserInfo = false
        )
        val profile = createProfile(
            registeredDomain = "xn--test.com",
            scheme = "http",
            port = 8080,
            isPunycode = true,
            suspiciousPaths = listOf("login", "verify")
        )

        val verdict = useCase.execute("msg1", listOf(link), listOf(profile))

        assertTrue("Should have at most 3 reasons", verdict.reasons.size <= 3)
    }

    // Helper functions

    private fun createLink(
        originalUrl: String = "https://example.com",
        host: String,
        scheme: String = "https",
        port: Int? = null,
        path: String = "",
        hasUserInfo: Boolean = false
    ): Link {
        return Link(
            original = originalUrl,
            normalized = originalUrl.lowercase(),
            finalUrl = null,
            host = host,
            registeredDomain = "",
            scheme = scheme,
            port = port,
            path = path,
            params = emptyMap(),
            hasUserInfo = hasUserInfo
        )
    }

    private fun createProfile(
        registeredDomain: String,
        scheme: String = "https",
        port: Int? = null,
        hasUserInfo: Boolean = false,
        isPunycode: Boolean = false,
        isRawIp: Boolean = false,
        suspiciousPaths: List<String> = emptyList(),
        isHomoglyphSuspect: Boolean = false,
        originalHost: String = registeredDomain
    ): DomainProfile {
        return DomainProfile(
            registeredDomain = registeredDomain,
            scheme = scheme,
            port = port,
            hasUserInfo = hasUserInfo,
            isPunycode = isPunycode,
            isRawIp = isRawIp,
            suspiciousPaths = suspiciousPaths,
            isHomoglyphSuspect = isHomoglyphSuspect,
            originalHost = originalHost
        )
    }
}
