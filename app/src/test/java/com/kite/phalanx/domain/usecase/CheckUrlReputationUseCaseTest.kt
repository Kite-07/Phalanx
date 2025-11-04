package com.kite.phalanx.domain.usecase

import com.kite.phalanx.data.repository.SafeBrowsingRepository
import com.kite.phalanx.data.repository.URLhausRepository
import com.kite.phalanx.domain.model.ReputationResult
import com.kite.phalanx.domain.model.ThreatType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for CheckUrlReputationUseCase.
 * Tests parallel execution, result aggregation, and convenience methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CheckUrlReputationUseCaseTest {

    private lateinit var safeBrowsingRepository: SafeBrowsingRepository
    private lateinit var urlhausRepository: URLhausRepository
    private lateinit var useCase: CheckUrlReputationUseCase

    @Before
    fun setup() {
        safeBrowsingRepository = mockk()
        urlhausRepository = mockk()
        useCase = CheckUrlReputationUseCase(safeBrowsingRepository, urlhausRepository)
    }

    @Test
    fun `execute returns results from all services`() = runTest {
        // Given
        val testUrl = "https://example.com"
        val safeBrowsingResult = ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "Google Safe Browsing",
            timestamp = 100L
        )
        val urlhausResult = ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "URLhaus",
            timestamp = 100L
        )

        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns safeBrowsingResult
        coEvery { urlhausRepository.checkUrl(testUrl) } returns urlhausResult

        // When
        val results = useCase.execute(testUrl)

        // Then
        assertEquals(2, results.size)
        assertTrue(results.contains(safeBrowsingResult))
        assertTrue(results.contains(urlhausResult))
        coVerify { safeBrowsingRepository.checkUrl(testUrl) }
        coVerify { urlhausRepository.checkUrl(testUrl) }
    }

    @Test
    fun `execute calls all services in parallel`() = runTest {
        // Given
        val testUrl = "https://malicious.com"
        coEvery { safeBrowsingRepository.checkUrl(testUrl) } coAnswers {
            kotlinx.coroutines.delay(100)
            ReputationResult(true, ThreatType.MALWARE, "Google Safe Browsing")
        }
        coEvery { urlhausRepository.checkUrl(testUrl) } coAnswers {
            kotlinx.coroutines.delay(100)
            ReputationResult(false, null, "URLhaus")
        }

        // When
        val startTime = System.currentTimeMillis()
        val results = useCase.execute(testUrl)
        val duration = System.currentTimeMillis() - startTime

        // Then
        // Should complete in ~100ms if parallel, ~200ms if sequential
        assertTrue("Parallel execution should be faster", duration < 150)
        assertEquals(2, results.size)
    }

    @Test
    fun `execute detects malicious URL from Safe Browsing`() = runTest {
        // Given
        val testUrl = "https://malware-site.com"
        val maliciousResult = ReputationResult(
            isMalicious = true,
            threatType = ThreatType.MALWARE,
            source = "Google Safe Browsing",
            metadata = mapOf("platform" to "WINDOWS")
        )
        val cleanResult = ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "URLhaus"
        )

        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns maliciousResult
        coEvery { urlhausRepository.checkUrl(testUrl) } returns cleanResult

        // When
        val results = useCase.execute(testUrl)

        // Then
        assertEquals(2, results.size)
        assertEquals(1, results.count { it.isMalicious })
        val malicious = results.first { it.isMalicious }
        assertEquals(ThreatType.MALWARE, malicious.threatType)
        assertEquals("Google Safe Browsing", malicious.source)
    }

    @Test
    fun `execute detects malicious URL from URLhaus`() = runTest {
        // Given
        val testUrl = "https://phishing-site.com"
        val cleanResult = ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "Google Safe Browsing"
        )
        val maliciousResult = ReputationResult(
            isMalicious = true,
            threatType = ThreatType.SOCIAL_ENGINEERING,
            source = "URLhaus"
        )

        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns cleanResult
        coEvery { urlhausRepository.checkUrl(testUrl) } returns maliciousResult

        // When
        val results = useCase.execute(testUrl)

        // Then
        assertEquals(2, results.size)
        assertEquals(1, results.count { it.isMalicious })
        val malicious = results.first { it.isMalicious }
        assertEquals(ThreatType.SOCIAL_ENGINEERING, malicious.threatType)
    }

    @Test
    fun `execute detects malicious URL from multiple services`() = runTest {
        // Given
        val testUrl = "https://very-malicious.com"
        val safeBrowsingResult = ReputationResult(
            isMalicious = true,
            threatType = ThreatType.MALWARE,
            source = "Google Safe Browsing"
        )
        val urlhausResult = ReputationResult(
            isMalicious = true,
            threatType = ThreatType.SOCIAL_ENGINEERING,
            source = "URLhaus"
        )

        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns safeBrowsingResult
        coEvery { urlhausRepository.checkUrl(testUrl) } returns urlhausResult

        // When
        val results = useCase.execute(testUrl)

        // Then
        assertEquals(2, results.size)
        assertEquals(2, results.count { it.isMalicious })
        assertTrue(results.all { it.isMalicious })
    }

    @Test
    fun `isMalicious returns true when any service flags URL`() = runTest {
        // Given
        val testUrl = "https://suspicious.com"
        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = true,
            threatType = ThreatType.SOCIAL_ENGINEERING,
            source = "Google Safe Browsing"
        )
        coEvery { urlhausRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "URLhaus"
        )

        // When
        val isMalicious = useCase.isMalicious(testUrl)

        // Then
        assertTrue(isMalicious)
    }

    @Test
    fun `isMalicious returns false when all services say clean`() = runTest {
        // Given
        val testUrl = "https://legitimate.com"
        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "Google Safe Browsing"
        )
        coEvery { urlhausRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "URLhaus"
        )

        // When
        val isMalicious = useCase.isMalicious(testUrl)

        // Then
        assertFalse(isMalicious)
    }

    @Test
    fun `getMostSevereResult returns first malicious result`() = runTest {
        // Given
        val testUrl = "https://bad-site.com"
        val maliciousResult = ReputationResult(
            isMalicious = true,
            threatType = ThreatType.MALWARE,
            source = "Google Safe Browsing"
        )
        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns maliciousResult
        coEvery { urlhausRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "URLhaus"
        )

        // When
        val result = useCase.getMostSevereResult(testUrl)

        // Then
        assertNotNull(result)
        assertEquals(ThreatType.MALWARE, result?.threatType)
        assertEquals("Google Safe Browsing", result?.source)
    }

    @Test
    fun `getMostSevereResult returns null when all clean`() = runTest {
        // Given
        val testUrl = "https://safe-site.com"
        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "Google Safe Browsing"
        )
        coEvery { urlhausRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "URLhaus"
        )

        // When
        val result = useCase.getMostSevereResult(testUrl)

        // Then
        assertNull(result)
    }

    @Test
    fun `execute handles all ThreatType variants`() = runTest {
        // Test each threat type
        val threatTypes = listOf(
            ThreatType.MALWARE,
            ThreatType.SOCIAL_ENGINEERING,
            ThreatType.UNWANTED_SOFTWARE,
            ThreatType.POTENTIALLY_HARMFUL,
            ThreatType.UNKNOWN
        )

        threatTypes.forEach { threatType ->
            val testUrl = "https://test-$threatType.com"
            coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns ReputationResult(
                isMalicious = true,
                threatType = threatType,
                source = "Google Safe Browsing"
            )
            coEvery { urlhausRepository.checkUrl(testUrl) } returns ReputationResult(
                isMalicious = false,
                threatType = null,
                source = "URLhaus"
            )

            val results = useCase.execute(testUrl)

            val malicious = results.first { it.isMalicious }
            assertEquals("Failed for threat type $threatType", threatType, malicious.threatType)
        }
    }

    @Test
    fun `execute preserves metadata from services`() = runTest {
        // Given
        val testUrl = "https://tracked.com"
        val metadata = mapOf(
            "platform" to "ANDROID",
            "threat_entry" to "URL",
            "detail" to "Phishing attempt detected"
        )
        coEvery { safeBrowsingRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = true,
            threatType = ThreatType.SOCIAL_ENGINEERING,
            source = "Google Safe Browsing",
            metadata = metadata
        )
        coEvery { urlhausRepository.checkUrl(testUrl) } returns ReputationResult(
            isMalicious = false,
            threatType = null,
            source = "URLhaus"
        )

        // When
        val results = useCase.execute(testUrl)

        // Then
        val result = results.first { it.isMalicious }
        assertEquals(metadata, result.metadata)
        assertEquals("ANDROID", result.metadata["platform"])
        assertEquals("Phishing attempt detected", result.metadata["detail"])
    }
}
