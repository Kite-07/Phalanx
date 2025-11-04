package com.kite.phalanx.domain.usecase

import com.kite.phalanx.data.util.PublicSuffixListParser
import com.kite.phalanx.domain.model.Link
import com.kite.phalanx.domain.util.BrandDatabase
import com.kite.phalanx.domain.util.TldRiskScorer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ProfileDomainUseCase.
 *
 * Tests cover:
 * - Registered domain extraction (PSL)
 * - Punycode detection
 * - IP address detection
 * - Suspicious path detection
 * - User info detection
 * - Non-standard port detection
 * - HTTP scheme detection
 * - Homoglyph detection (basic)
 *
 * Per PRD Phase 1: Domain Profiler
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileDomainUseCaseTest {

    private lateinit var pslParser: PublicSuffixListParser
    private lateinit var useCase: ProfileDomainUseCase

    @Before
    fun setup() {
        pslParser = mockk()
        // Stage 1B: Mock brandDatabase and tldRiskScorer with relaxed mocking
        val brandDatabase = mockk<BrandDatabase>(relaxed = true)
        val tldRiskScorer = mockk<TldRiskScorer>(relaxed = true)
        useCase = ProfileDomainUseCase(pslParser, brandDatabase, tldRiskScorer)
    }

    @Test
    fun `profile standard domain extracts registered domain`() = runTest {
        val link = createLink(host = "www.example.com")
        every { pslParser.getRegisteredDomain("www.example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertEquals("example.com", profile.registeredDomain)
        assertEquals("www.example.com", profile.originalHost)
        assertFalse(profile.isPunycode)
        assertFalse(profile.isRawIp)
    }

    @Test
    fun `profile multi-level TLD domain`() = runTest {
        val link = createLink(host = "blog.example.co.uk")
        every { pslParser.getRegisteredDomain("blog.example.co.uk") } returns "example.co.uk"

        val profile = useCase.execute(link)

        assertEquals("example.co.uk", profile.registeredDomain)
    }

    @Test
    fun `detect punycode domain`() = runTest {
        val link = createLink(host = "xn--mnchen-3ya.de") // münchen.de in punycode
        every { pslParser.getRegisteredDomain("xn--mnchen-3ya.de") } returns "xn--mnchen-3ya.de"

        val profile = useCase.execute(link)

        assertTrue("Should detect punycode", profile.isPunycode)
        assertEquals("xn--mnchen-3ya.de", profile.registeredDomain)
    }

    @Test
    fun `detect IPv4 address`() = runTest {
        val link = createLink(host = "192.168.1.1")
        every { pslParser.getRegisteredDomain("192.168.1.1") } returns "192.168.1.1"

        val profile = useCase.execute(link)

        assertTrue("Should detect IPv4", profile.isRawIp)
        assertEquals("192.168.1.1", profile.registeredDomain)
    }

    @Test
    fun `detect IPv6 address`() = runTest {
        val link = createLink(host = "[2001:db8::1]")
        every { pslParser.getRegisteredDomain("[2001:db8::1]") } returns "[2001:db8::1]"

        val profile = useCase.execute(link)

        assertTrue("Should detect IPv6", profile.isRawIp)
    }

    @Test
    fun `detect suspicious path - login`() = runTest {
        val link = createLink(
            host = "example.com",
            path = "/login"
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertTrue("Should detect 'login' path", profile.suspiciousPaths.contains("login"))
    }

    @Test
    fun `detect suspicious path - verify`() = runTest {
        val link = createLink(
            host = "example.com",
            path = "/account/verify"
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertTrue("Should detect 'verify' path", profile.suspiciousPaths.contains("verify"))
    }

    @Test
    fun `detect suspicious path - reset password`() = runTest {
        val link = createLink(
            host = "example.com",
            path = "/reset-password"
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertTrue("Should detect 'reset' path", profile.suspiciousPaths.contains("reset"))
        assertTrue("Should detect 'password' path", profile.suspiciousPaths.contains("password"))
    }

    @Test
    fun `detect multiple suspicious paths`() = runTest {
        val link = createLink(
            host = "example.com",
            path = "/verify/otp/login"
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertTrue("Should detect multiple suspicious keywords", profile.suspiciousPaths.size >= 2)
        assertTrue(profile.suspiciousPaths.contains("verify"))
        assertTrue(profile.suspiciousPaths.contains("otp"))
        assertTrue(profile.suspiciousPaths.contains("login"))
    }

    @Test
    fun `no suspicious paths for normal path`() = runTest {
        val link = createLink(
            host = "example.com",
            path = "/products/item123"
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertTrue("Should have no suspicious paths", profile.suspiciousPaths.isEmpty())
    }

    @Test
    fun `detect user info in URL`() = runTest {
        val link = createLink(
            host = "example.com",
            hasUserInfo = true
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertTrue("Should detect user info", profile.hasUserInfo)
    }

    @Test
    fun `detect non-standard port`() = runTest {
        val link = createLink(
            host = "example.com",
            port = 8080
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertEquals("Should capture non-standard port", 8080, profile.port)
    }

    @Test
    fun `standard port 443 not flagged`() = runTest {
        val link = createLink(
            host = "example.com",
            scheme = "https",
            port = 443
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertNull("Standard HTTPS port should not be flagged", profile.port)
    }

    @Test
    fun `standard port 80 not flagged`() = runTest {
        val link = createLink(
            host = "example.com",
            scheme = "http",
            port = 80
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertNull("Standard HTTP port should not be flagged", profile.port)
    }

    @Test
    fun `detect HTTP scheme`() = runTest {
        val link = createLink(
            host = "example.com",
            scheme = "http"
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertEquals("Should capture HTTP scheme", "http", profile.scheme)
    }

    @Test
    fun `detect HTTPS scheme`() = runTest {
        val link = createLink(
            host = "example.com",
            scheme = "https"
        )
        every { pslParser.getRegisteredDomain("example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertEquals("Should capture HTTPS scheme", "https", profile.scheme)
    }

    @Test
    fun `realistic phishing domain with multiple flags`() = runTest {
        // Simulate a phishing URL: http://user:pass@192.168.1.1:8080/login
        val link = createLink(
            host = "192.168.1.1",
            scheme = "http",
            port = 8080,
            path = "/login",
            hasUserInfo = true
        )
        every { pslParser.getRegisteredDomain("192.168.1.1") } returns "192.168.1.1"

        val profile = useCase.execute(link)

        assertTrue("Should detect IP", profile.isRawIp)
        assertTrue("Should detect user info", profile.hasUserInfo)
        assertEquals("Should detect HTTP", "http", profile.scheme)
        assertEquals("Should detect non-standard port", 8080, profile.port)
        assertTrue("Should detect suspicious path", profile.suspiciousPaths.contains("login"))
    }

    @Test
    fun `realistic legitimate URL - no flags`() = runTest {
        val link = createLink(
            host = "www.amazon.com",
            scheme = "https",
            path = "/products/B08N5WRWNW",
            hasUserInfo = false,
            port = null
        )
        every { pslParser.getRegisteredDomain("www.amazon.com") } returns "amazon.com"

        val profile = useCase.execute(link)

        assertEquals("amazon.com", profile.registeredDomain)
        assertFalse("Should not be IP", profile.isRawIp)
        assertFalse("Should not have user info", profile.hasUserInfo)
        assertFalse("Should not be punycode", profile.isPunycode)
        assertEquals("Should be HTTPS", "https", profile.scheme)
        assertNull("Should not have non-standard port", profile.port)
        assertTrue("Should have no suspicious paths", profile.suspiciousPaths.isEmpty())
    }

    @Test
    fun `shortener domain - bit ly`() = runTest {
        val link = createLink(host = "bit.ly")
        every { pslParser.getRegisteredDomain("bit.ly") } returns "bit.ly"

        val profile = useCase.execute(link)

        assertEquals("bit.ly", profile.registeredDomain)
        assertFalse(profile.isRawIp)
    }

    @Test
    fun `subdomain should extract parent registered domain`() = runTest {
        val link = createLink(host = "api.staging.example.com")
        every { pslParser.getRegisteredDomain("api.staging.example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertEquals("example.com", profile.registeredDomain)
        assertEquals("api.staging.example.com", profile.originalHost)
    }

    @Test
    fun `case insensitive domain processing`() = runTest {
        val link = createLink(host = "WWW.EXAMPLE.COM")
        every { pslParser.getRegisteredDomain("www.example.com") } returns "example.com"

        val profile = useCase.execute(link)

        assertEquals("example.com", profile.registeredDomain)
    }

    // Helper function to create Link objects for testing
    private fun createLink(
        host: String,
        scheme: String = "https",
        port: Int? = null,
        path: String = "",
        hasUserInfo: Boolean = false
    ): Link {
        return Link(
            original = "$scheme://$host$path",
            normalized = "$scheme://$host$path".lowercase(),
            finalUrl = null,
            host = host,
            registeredDomain = "", // Will be populated by profiler
            scheme = scheme,
            port = port,
            path = path,
            params = emptyMap(),
            hasUserInfo = hasUserInfo
        )
    }
}
