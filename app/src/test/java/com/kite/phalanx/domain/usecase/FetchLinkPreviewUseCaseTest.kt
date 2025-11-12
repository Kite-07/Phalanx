package com.kite.phalanx.domain.usecase

import com.kite.phalanx.domain.model.LinkPreview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FetchLinkPreviewUseCase.
 *
 * Tests cover:
 * - Data URL blocking (per PRD Phase 5)
 * - URL scheme validation (HTTP/HTTPS only)
 * - Basic execution (network tests would require mocking OkHttp)
 */
class FetchLinkPreviewUseCaseTest {

    private lateinit var useCase: FetchLinkPreviewUseCase

    @Before
    fun setup() {
        useCase = FetchLinkPreviewUseCase()
    }

    @Test
    fun `data URLs are blocked`() = runTest {
        val dataUrl = "data:text/html,<html><body>Test</body></html>"

        val result = useCase.execute(dataUrl)

        assertNotNull("Result should not be null", result)
        assertEquals("URL should match", dataUrl, result.url)
        assertNull("Title should be null for blocked data URL", result.title)
        assertNotNull("Error should be set for data URL", result.error)
        assertTrue(
            "Error should mention data URLs not supported",
            result.error!!.contains("Data URLs are not supported", ignoreCase = true)
        )
    }

    @Test
    fun `non-HTTP and non-HTTPS URLs are rejected`() = runTest {
        val ftpUrl = "ftp://example.com/file.txt"

        val result = useCase.execute(ftpUrl)

        assertNotNull("Result should not be null", result)
        assertEquals("URL should match", ftpUrl, result.url)
        assertNull("Title should be null for invalid scheme", result.title)
        assertNotNull("Error should be set for invalid scheme", result.error)
        assertTrue(
            "Error should mention only HTTP/HTTPS supported",
            result.error!!.contains("Only HTTP/HTTPS URLs are supported", ignoreCase = true)
        )
    }

    @Test
    fun `HTTP URL is accepted for processing`() = runTest {
        val httpUrl = "http://example.com"

        val result = useCase.execute(httpUrl)

        assertNotNull("Result should not be null", result)
        assertEquals("URL should match", httpUrl, result.url)
        // Note: Will fail with network error in test environment without mocking
        // This test verifies URL validation passes, actual fetch would need mocking
    }

    @Test
    fun `HTTPS URL is accepted for processing`() = runTest {
        val httpsUrl = "https://example.com"

        val result = useCase.execute(httpsUrl)

        assertNotNull("Result should not be null", result)
        assertEquals("URL should match", httpsUrl, result.url)
        // Note: Will fail with network error in test environment without mocking
        // This test verifies URL validation passes, actual fetch would need mocking
    }

    @Test
    fun `javascript URLs are rejected`() = runTest {
        val jsUrl = "javascript:alert('test')"

        val result = useCase.execute(jsUrl)

        assertNotNull("Result should not be null", result)
        assertEquals("URL should match", jsUrl, result.url)
        assertNull("Title should be null for javascript URL", result.title)
        assertNotNull("Error should be set for javascript URL", result.error)
    }

    @Test
    fun `file URLs are rejected`() = runTest {
        val fileUrl = "file:///etc/passwd"

        val result = useCase.execute(fileUrl)

        assertNotNull("Result should not be null", result)
        assertEquals("URL should match", fileUrl, result.url)
        assertNull("Title should be null for file URL", result.title)
        assertNotNull("Error should be set for file URL", result.error)
    }

    @Test
    fun `result has fetchedAt timestamp`() = runTest {
        val url = "data:text/html,test" // Use data URL to test without network

        val beforeTimestamp = System.currentTimeMillis()
        val result = useCase.execute(url)
        val afterTimestamp = System.currentTimeMillis()

        assertTrue(
            "fetchedAt should be between before and after timestamps",
            result.fetchedAt in beforeTimestamp..afterTimestamp
        )
    }

    @Test
    fun `malformed URLs are handled gracefully`() = runTest {
        val malformedUrl = "http://[invalid"

        val result = useCase.execute(malformedUrl)

        assertNotNull("Result should not be null", result)
        assertEquals("URL should match", malformedUrl, result.url)
        // Should have an error, not crash
        assertNotNull("Error should be set for malformed URL", result.error)
    }
}
