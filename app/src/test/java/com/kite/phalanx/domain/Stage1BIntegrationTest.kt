package com.kite.phalanx.domain

import android.content.Context
import com.kite.phalanx.data.util.PublicSuffixListParser
import com.kite.phalanx.domain.model.*
import com.kite.phalanx.domain.usecase.AnalyzeMessageRiskUseCase
import com.kite.phalanx.domain.usecase.CheckAllowBlockRulesUseCase
import com.kite.phalanx.domain.usecase.ExtractLinksUseCase
import com.kite.phalanx.domain.usecase.ProfileDomainUseCase
import com.kite.phalanx.domain.util.BrandDatabase
import com.kite.phalanx.domain.util.TldRiskScorer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration test for Stage 1B brand impersonation detection.
 *
 * Tests the complete pipeline: Extract Links → Profile Domain → Analyze Risk
 *
 * Phase 3 Integration: Mocks CheckAllowBlockRulesUseCase to return null (no rules).
 */
class Stage1BIntegrationTest {

    private lateinit var extractLinksUseCase: ExtractLinksUseCase
    private lateinit var profileDomainUseCase: ProfileDomainUseCase
    private lateinit var analyzeRiskUseCase: AnalyzeMessageRiskUseCase
    private lateinit var pslParser: PublicSuffixListParser
    private lateinit var brandDatabase: BrandDatabase
    private lateinit var tldRiskScorer: TldRiskScorer

    @Before
    fun setup() {
        // Real implementations
        extractLinksUseCase = ExtractLinksUseCase()
        brandDatabase = BrandDatabase()
        tldRiskScorer = TldRiskScorer()

        // Mock dependencies
        val mockContext = mockk<Context>(relaxed = true)
        val mockCheckAllowBlockRulesUseCase = mockk<CheckAllowBlockRulesUseCase>()
        coEvery { mockCheckAllowBlockRulesUseCase.execute(any(), any(), any()) } returns null

        // Mock CheckSenderMismatchUseCase to return empty list (no sender mismatch)
        val mockCheckSenderMismatchUseCase = mockk<com.kite.phalanx.domain.usecase.CheckSenderMismatchUseCase>()
        every { mockCheckSenderMismatchUseCase.invoke(any(), any(), any(), any()) } returns emptyList()

        analyzeRiskUseCase = AnalyzeMessageRiskUseCase(mockContext, mockCheckAllowBlockRulesUseCase, mockCheckSenderMismatchUseCase)

        // Mock PSL parser
        pslParser = mockk()
        every { pslParser.getRegisteredDomain(any()) } answers {
            val domain = firstArg<String>()
            // Simple mock: return the domain itself
            domain
        }

        profileDomainUseCase = ProfileDomainUseCase(pslParser, brandDatabase, tldRiskScorer)
    }

    @Test
    fun `paypa1 dot com detected as PayPal typosquatting`() = runTest {
        // Simulate receiving message: "Check your account at https://paypa1.com"
        val messageText = "Check your account at https://paypa1.com"

        // Step 1: Extract links
        val links = extractLinksUseCase.execute(messageText)
        println("Extracted links: ${links.map { it.original }}")
        assertEquals(1, links.size)
        assertEquals("https://paypa1.com", links[0].original)
        assertEquals("paypa1.com", links[0].host)

        // Step 2: Profile domain
        val profile = profileDomainUseCase.execute(links[0])
        println("Registered domain: ${profile.registeredDomain}")
        println("Brand impersonation: ${profile.brandImpersonation}")
        println("TLD risk: ${profile.tldRiskLevel}")

        // Verify brand impersonation detected
        assertNotNull("Brand impersonation should be detected", profile.brandImpersonation)
        assertEquals("PayPal", profile.brandImpersonation?.brandName)
        assertEquals("paypa1.com", profile.brandImpersonation?.attemptedDomain)
        assertEquals("paypal.com", profile.brandImpersonation?.officialDomain)
        assertEquals(ImpersonationType.TYPOSQUATTING, profile.brandImpersonation?.type)

        // Step 3: Analyze risk
        val verdict = analyzeRiskUseCase.execute(
            messageId = "test-msg-1",
            sender = "+1234567890",
            messageBody = messageText,
            links = links,
            domainProfiles = listOf(profile),
            expandedUrls = emptyMap()
        )

        println("Verdict level: ${verdict.level}")
        println("Verdict score: ${verdict.score}")
        println("Reasons: ${verdict.reasons.map { it.label }}")

        // Verify verdict
        assertTrue("Verdict should be AMBER or RED", verdict.level in listOf(VerdictLevel.AMBER, VerdictLevel.RED))
        assertTrue("Score should be >= 60 (brand impersonation weight)", verdict.score >= 60)

        // Verify reason is included
        val brandReason = verdict.reasons.find { it.code == SignalCode.BRAND_IMPERSONATION }
        assertNotNull("Should have brand impersonation reason", brandReason)
        assertTrue("Reason should mention PayPal", brandReason!!.label.contains("PayPal"))
        assertTrue("Reason should mention typosquatting or impersonation",
            brandReason.label.contains("Typo", ignoreCase = true) ||
            brandReason.details.contains("impersonate", ignoreCase = true))
    }

    @Test
    fun `amazon-verify dot tk detected as brand impersonation + high-risk TLD`() = runTest {
        val messageText = "Verify your account: https://amazon-verify.tk/confirm"

        // Extract + Profile + Analyze
        val links = extractLinksUseCase.execute(messageText)
        val profile = profileDomainUseCase.execute(links[0])
        val verdict = analyzeRiskUseCase.execute(
            messageId = "test-msg-2",
            sender = "+1234567890",
            messageBody = messageText,
            links = links,
            domainProfiles = listOf(profile)
        )

        println("Profile: brandImpersonation=${profile.brandImpersonation}, tldRisk=${profile.tldRiskLevel}")
        println("Verdict: ${verdict.level}, score=${verdict.score}")

        // Should detect both brand impersonation AND high-risk TLD
        assertNotNull(profile.brandImpersonation)
        assertEquals("Amazon", profile.brandImpersonation?.brandName)
        assertEquals(TldRiskLevel.CRITICAL, profile.tldRiskLevel)

        // Combined score should be high (50 + 30 = 80)
        assertTrue("Score should be >= 70 (RED threshold)", verdict.score >= 70)
        assertEquals(VerdictLevel.RED, verdict.level)

        // Should have both signals
        assertTrue(verdict.reasons.any { it.code == SignalCode.BRAND_IMPERSONATION })
        assertTrue(verdict.reasons.any { it.code == SignalCode.HIGH_RISK_TLD })
    }

    @Test
    fun `legitimate paypal dot com not flagged`() = runTest {
        val messageText = "Your PayPal receipt: https://www.paypal.com/activity"

        val links = extractLinksUseCase.execute(messageText)
        val profile = profileDomainUseCase.execute(links[0])

        // Should NOT detect brand impersonation (it's the official domain)
        assertNull("Official domain should not be flagged", profile.brandImpersonation)
        assertEquals(TldRiskLevel.LOW, profile.tldRiskLevel)
    }

    @Test
    fun `goog1e dot com detected as Google typosquatting`() = runTest {
        val messageText = "Google security alert: https://goog1e.com/verify"

        val links = extractLinksUseCase.execute(messageText)
        val profile = profileDomainUseCase.execute(links[0])
        val verdict = analyzeRiskUseCase.execute(
            messageId = "test-msg-3",
            sender = "+1234567890",
            messageBody = messageText,
            links = links,
            domainProfiles = listOf(profile)
        )

        assertNotNull(profile.brandImpersonation)
        assertEquals("Google", profile.brandImpersonation?.brandName)
        assertEquals(ImpersonationType.TYPOSQUATTING, profile.brandImpersonation?.type)
        assertTrue(verdict.level in listOf(VerdictLevel.AMBER, VerdictLevel.RED))
    }

    @Test
    fun `app1e dot com detected as Apple typosquatting`() = runTest {
        val messageText = "Your Apple ID: https://app1e.com/verify"

        val links = extractLinksUseCase.execute(messageText)
        val profile = profileDomainUseCase.execute(links[0])

        assertNotNull(profile.brandImpersonation)
        assertEquals("Apple", profile.brandImpersonation?.brandName)
        assertEquals(ImpersonationType.TYPOSQUATTING, profile.brandImpersonation?.type)
    }

    @Test
    fun `g00gle dot com detected as Google leet-speak impersonation`() = runTest {
        val messageText = "Google security alert: https://g00gle.com/verify"

        val links = extractLinksUseCase.execute(messageText)
        println("Extracted links: ${links.map { it.original }}")
        assertEquals(1, links.size)
        assertEquals("g00gle.com", links[0].host)

        val profile = profileDomainUseCase.execute(links[0])
        println("Brand impersonation: ${profile.brandImpersonation}")
        println("Brand impersonation type: ${profile.brandImpersonation?.type}")

        assertNotNull("g00gle should be detected as Google impersonation", profile.brandImpersonation)
        assertEquals("Google", profile.brandImpersonation?.brandName)
        // g00gle.com vs google.com has Levenshtein distance of 2 (two o->0 substitutions)
        // So it should be detected as TYPOSQUATTING
        assertEquals(ImpersonationType.TYPOSQUATTING, profile.brandImpersonation?.type)
    }

    @Test
    fun `amaz0n dot com detected as Amazon leet-speak impersonation`() = runTest {
        val messageText = "Amazon order: https://amaz0n.com/track"

        val links = extractLinksUseCase.execute(messageText)
        val profile = profileDomainUseCase.execute(links[0])

        assertNotNull(profile.brandImpersonation)
        assertEquals("Amazon", profile.brandImpersonation?.brandName)
        // amaz0n.com vs amazon.com has Levenshtein distance of 1 (one o->0 substitution)
        // So it should be detected as TYPOSQUATTING
        assertEquals(ImpersonationType.TYPOSQUATTING, profile.brandImpersonation?.type)
    }
}
