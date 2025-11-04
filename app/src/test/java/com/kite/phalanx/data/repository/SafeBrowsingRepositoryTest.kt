package com.kite.phalanx.data.repository

import android.content.Context
import android.content.Intent
import com.kite.phalanx.SafeBrowsingPreferences
import com.kite.phalanx.domain.model.ThreatType
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SafeBrowsingRepository.
 * Tests quota handling, caching, response parsing, and threat detection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SafeBrowsingRepositoryTest {

    private lateinit var context: Context
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var repository: SafeBrowsingRepository

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        okHttpClient = mockk()
        repository = SafeBrowsingRepository(context, okHttpClient)

        // Mock SafeBrowsingPreferences
        mockkObject(SafeBrowsingPreferences)
        coEvery { SafeBrowsingPreferences.getApiKey(any()) } returns "test-api-key"
        coEvery { SafeBrowsingPreferences.hasCustomApiKey(any()) } returns false
    }

    @Test
    fun `checkUrl returns clean result for safe URL`() = runTest {
        // Given
        val testUrl = "https://safe-site.com"
        val mockResponse = createMockResponse(
            code = 200,
            body = "{}" // Empty response = safe
        )
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious)
        assertNull(result.threatType)
        assertEquals("Google Safe Browsing", result.source)
    }

    @Test
    fun `checkUrl detects malware threat`() = runTest {
        // Given
        val testUrl = "https://malware-site.com"
        val responseBody = """
            {
                "matches": [{
                    "threatType": "MALWARE",
                    "platformType": "ANY_PLATFORM",
                    "threatEntryType": "URL"
                }]
            }
        """.trimIndent()
        val mockResponse = createMockResponse(code = 200, body = responseBody)
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertTrue(result.isMalicious)
        assertEquals(ThreatType.MALWARE, result.threatType)
        assertEquals("Google Safe Browsing", result.source)
        assertEquals("MALWARE", result.metadata["threatType"])
    }

    @Test
    fun `checkUrl detects social engineering threat`() = runTest {
        // Given
        val testUrl = "https://phishing-site.com"
        val responseBody = """
            {
                "matches": [{
                    "threatType": "SOCIAL_ENGINEERING",
                    "platformType": "ANDROID"
                }]
            }
        """.trimIndent()
        val mockResponse = createMockResponse(code = 200, body = responseBody)
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertTrue(result.isMalicious)
        assertEquals(ThreatType.SOCIAL_ENGINEERING, result.threatType)
        assertEquals("ANDROID", result.metadata["platformType"])
    }

    @Test
    fun `checkUrl detects unwanted software threat`() = runTest {
        // Given
        val testUrl = "https://unwanted-software.com"
        val responseBody = """
            {
                "matches": [{
                    "threatType": "UNWANTED_SOFTWARE",
                    "platformType": "WINDOWS"
                }]
            }
        """.trimIndent()
        val mockResponse = createMockResponse(code = 200, body = responseBody)
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertTrue(result.isMalicious)
        assertEquals(ThreatType.UNWANTED_SOFTWARE, result.threatType)
    }

    @Test
    fun `checkUrl detects potentially harmful application`() = runTest {
        // Given
        val testUrl = "https://harmful-app.com"
        val responseBody = """
            {
                "matches": [{
                    "threatType": "POTENTIALLY_HARMFUL_APPLICATION",
                    "platformType": "ANY_PLATFORM"
                }]
            }
        """.trimIndent()
        val mockResponse = createMockResponse(code = 200, body = responseBody)
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertTrue(result.isMalicious)
        assertEquals(ThreatType.POTENTIALLY_HARMFUL, result.threatType)
    }

    @Test
    fun `checkUrl maps unknown threat type correctly`() = runTest {
        // Given
        val testUrl = "https://unknown-threat.com"
        val responseBody = """
            {
                "matches": [{
                    "threatType": "SOME_NEW_THREAT_TYPE",
                    "platformType": "ANY_PLATFORM"
                }]
            }
        """.trimIndent()
        val mockResponse = createMockResponse(code = 200, body = responseBody)
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertTrue(result.isMalicious)
        assertEquals(ThreatType.UNKNOWN, result.threatType)
    }

    @Test
    fun `checkUrl detects quota exceeded with HTTP 429`() = runTest {
        // Given
        val testUrl = "https://test.com"
        val mockResponse = createMockResponse(
            code = 429,
            body = """{"error": "Resource exhausted"}"""
        )
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious) // Fail-safe: return clean
        assertEquals("HTTP 429", result.metadata["error"])
        assertEquals("true", result.metadata["quotaExceeded"])

        // Verify broadcast was sent
        verify { context.sendBroadcast(match { intent ->
            intent.action == SafeBrowsingRepository.ACTION_QUOTA_EXCEEDED
        }) }
    }

    @Test
    fun `checkUrl detects quota exceeded with HTTP 403 and quota message`() = runTest {
        // Given
        val testUrl = "https://test.com"
        val mockResponse = createMockResponse(
            code = 403,
            body = """{"error": {"message": "Quota exceeded for quota metric 'Queries' and limit 'Queries per day'"}}"""
        )
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious)
        assertEquals("HTTP 403", result.metadata["error"])
        assertEquals("true", result.metadata["quotaExceeded"])

        // Verify broadcast was sent
        verify { context.sendBroadcast(match { intent ->
            intent.action == SafeBrowsingRepository.ACTION_QUOTA_EXCEEDED
        }) }
    }

    @Test
    fun `checkUrl does not flag quota exceeded for non-quota 403 error`() = runTest {
        // Given
        val testUrl = "https://test.com"
        val mockResponse = createMockResponse(
            code = 403,
            body = """{"error": "Invalid API key"}"""
        )
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious)
        assertEquals("HTTP 403", result.metadata["error"])
        assertEquals("false", result.metadata["quotaExceeded"])

        // Verify broadcast was NOT sent (quota not exceeded, just auth error)
        verify(exactly = 0) { context.sendBroadcast(any()) }
    }

    @Test
    fun `checkUrl handles network errors gracefully`() = runTest {
        // Given
        val testUrl = "https://test.com"
        val mockCall: Call = mockk()
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws java.io.IOException("Network error")

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious) // Fail-safe
        assertEquals("Network error", result.metadata["error"])
        assertEquals("Google Safe Browsing", result.source)
    }

    @Test
    fun `checkUrl handles malformed JSON response gracefully`() = runTest {
        // Given
        val testUrl = "https://test.com"
        val mockResponse = createMockResponse(
            code = 200,
            body = "not valid json {{{{"
        )
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious) // Fail-safe on parse error
        assertTrue(result.metadata["error"]?.contains("Parse error") == true)
    }

    @Test
    fun `getServiceName returns correct name`() {
        assertEquals("Google Safe Browsing", repository.getServiceName())
    }

    @Test
    fun `checkUrl handles empty response body as safe`() = runTest {
        // Given
        val testUrl = "https://safe-site.com"
        val mockResponse = createMockResponse(code = 200, body = "")
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious)
        assertNull(result.threatType)
    }

    @Test
    fun `checkUrl handles response with empty matches array`() = runTest {
        // Given
        val testUrl = "https://safe-site.com"
        val mockResponse = createMockResponse(
            code = 200,
            body = """{"matches": []}"""
        )
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertFalse(result.isMalicious)
        assertNull(result.threatType)
    }

    @Test
    fun `checkUrl handles multiple matches and returns first`() = runTest {
        // Given
        val testUrl = "https://multiple-threats.com"
        val responseBody = """
            {
                "matches": [
                    {
                        "threatType": "MALWARE",
                        "platformType": "ANY_PLATFORM"
                    },
                    {
                        "threatType": "SOCIAL_ENGINEERING",
                        "platformType": "ANDROID"
                    }
                ]
            }
        """.trimIndent()
        val mockResponse = createMockResponse(code = 200, body = responseBody)
        setupMockHttpCall(mockResponse)

        // When
        val result = repository.checkUrl(testUrl)

        // Then
        assertTrue(result.isMalicious)
        // Should return first match
        assertEquals(ThreatType.MALWARE, result.threatType)
        assertEquals("MALWARE", result.metadata["threatType"])
    }

    @Test
    fun `broadcast failure is handled gracefully`() = runTest {
        // Given
        val testUrl = "https://test.com"
        val mockResponse = createMockResponse(code = 429, body = "{}")
        setupMockHttpCall(mockResponse)

        // Make broadcast throw exception
        every { context.sendBroadcast(any()) } throws SecurityException("Broadcast not allowed")

        // When/Then - should not crash
        val result = repository.checkUrl(testUrl)

        // Still returns result despite broadcast failure
        assertFalse(result.isMalicious)
        assertEquals("true", result.metadata["quotaExceeded"])
    }

    // Helper methods

    private fun createMockResponse(code: Int, body: String): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://test.com").build())
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("Test response")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun setupMockHttpCall(response: Response) {
        val mockCall: Call = mockk()
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response
    }
}
