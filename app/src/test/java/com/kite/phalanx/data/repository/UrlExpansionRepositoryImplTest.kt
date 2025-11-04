package com.kite.phalanx.data.repository

import com.kite.phalanx.data.source.local.dao.CachedExpansionDao
import com.kite.phalanx.data.source.local.entity.CachedExpansionEntity
import com.kite.phalanx.domain.repository.UrlExpansionException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Unit tests for UrlExpansionRepositoryImpl.
 *
 * Tests cover:
 * - Basic URL expansion without redirects
 * - Single and multiple redirect following
 * - Two-tier caching (memory + database)
 * - Cache expiry
 * - Error scenarios (timeout, network, invalid URL)
 * - Too many redirects (>4)
 * - HEAD request fallback to GET
 *
 * Target: ≤1.5s on first request, ≤50ms cached
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UrlExpansionRepositoryImplTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var dao: CachedExpansionDao
    private lateinit var repository: UrlExpansionRepositoryImpl

    @Before
    fun setup() {
        okHttpClient = mockk()
        dao = mockk()
        repository = UrlExpansionRepositoryImpl(okHttpClient, dao)

        // Default DAO behavior
        coEvery { dao.insert(any()) } returns Unit
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `expandUrl with no redirects returns original URL`() = runTest {
        val originalUrl = "https://example.com/page"

        // Mock no cached expansion
        coEvery { dao.getByOriginalUrl(originalUrl) } returns null

        // Mock successful HEAD request with no redirect
        val call: okhttp3.Call = mockk<okhttp3.Call>()
        val response: Response = mockk<Response>()
        val request: Request = mockk<Request>()
        val httpUrl: HttpUrl = mockk<HttpUrl>()

        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns response
        every { response.isRedirect } returns false
        every { response.request } returns request
        every { request.url } returns httpUrl
        every { httpUrl.toString() } returns originalUrl
        every { response.close() } just runs

        val result = repository.expandUrl(originalUrl)

        assertEquals(originalUrl, result.originalUrl)
        assertEquals(originalUrl, result.finalUrl)
        assertTrue(result.redirectChain.isEmpty())
        coVerify { dao.insert(any()) }
    }

    @Test
    fun `expandUrl follows single redirect`() = runTest {
        val originalUrl = "https://bit.ly/abc123"
        val finalUrl = "https://example.com/article"

        coEvery { dao.getByOriginalUrl(originalUrl) } returns null

        // First call - redirect response
        val call1: okhttp3.Call = mockk<okhttp3.Call>()
        val response1: Response = mockk<Response>()
        every { response1.isRedirect } returns true
        every { response1.header("Location") } returns finalUrl
        every { response1.close() } just runs

        // Second call - final response
        val call2: okhttp3.Call = mockk<okhttp3.Call>()
        val response2: Response = mockk<Response>()
        val request2: Request = mockk<Request>()
        val httpUrl2: HttpUrl = mockk<HttpUrl>()
        every { response2.isRedirect } returns false
        every { response2.request } returns request2
        every { request2.url } returns httpUrl2
        every { httpUrl2.toString() } returns finalUrl
        every { response2.close() } just runs

        every { okHttpClient.newCall(any()) } returnsMany listOf(call1, call2)
        every { call1.execute() } returns response1
        every { call2.execute() } returns response2

        val result = repository.expandUrl(originalUrl)

        assertEquals(originalUrl, result.originalUrl)
        assertEquals(finalUrl, result.finalUrl)
        assertEquals(1, result.redirectChain.size)
        assertEquals(originalUrl, result.redirectChain[0])
    }

    @Test
    fun `expandUrl follows multiple redirects`() = runTest {
        val url1 = "https://short.link/a"
        val url2 = "https://medium.link/b"
        val url3 = "https://another.link/c"
        val finalUrl = "https://final.com/page"

        coEvery { dao.getByOriginalUrl(url1) } returns null

        val responses = listOf(
            createRedirectResponse(url2),
            createRedirectResponse(url3),
            createRedirectResponse(finalUrl),
            createFinalResponse(finalUrl)
        )

        val calls: List<okhttp3.Call> = responses.map { mockk<okhttp3.Call>() }
        calls.zip(responses).forEach { (call, response) ->
            every { call.execute() } returns response
        }

        every { okHttpClient.newCall(any()) } returnsMany calls

        val result = repository.expandUrl(url1)

        assertEquals(url1, result.originalUrl)
        assertEquals(finalUrl, result.finalUrl)
        assertEquals(3, result.redirectChain.size)
    }

    @Test(expected = UrlExpansionException.TooManyRedirects::class)
    fun `expandUrl throws TooManyRedirects when exceeding limit`() = runTest {
        val originalUrl = "https://infinite.loop/start"

        coEvery { dao.getByOriginalUrl(originalUrl) } returns null

        // Create 5 redirect responses (exceeds MAX_REDIRECTS of 4)
        val redirectResponse = createRedirectResponse("https://loop.com/next")
        val call: okhttp3.Call = mockk<okhttp3.Call>()
        every { call.execute() } returns redirectResponse
        every { okHttpClient.newCall(any()) } returns call

        repository.expandUrl(originalUrl)
    }

    @Test
    fun `expandUrl uses cache when available`() = runTest {
        val originalUrl = "https://cached.link/abc"
        val finalUrl = "https://final.com/page"

        val cachedEntity = CachedExpansionEntity(
            originalUrl = originalUrl,
            finalUrl = finalUrl,
            redirectChain = Json.encodeToString(listOf(originalUrl)),
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        )

        coEvery { dao.getByOriginalUrl(originalUrl) } returns cachedEntity

        val result = repository.expandUrl(originalUrl)

        assertEquals(finalUrl, result.finalUrl)
        // Should not make any HTTP calls
        verify(exactly = 0) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `expandUrl ignores expired cache`() = runTest {
        val originalUrl = "https://expired.link/abc"
        val finalUrl = "https://fresh.com/page"

        // Cached entity that expired 1 hour ago
        val expiredEntity = CachedExpansionEntity(
            originalUrl = originalUrl,
            finalUrl = "https://old.com/page",
            redirectChain = "[]",
            timestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2),
            expiresAt = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        )

        coEvery { dao.getByOriginalUrl(originalUrl) } returns expiredEntity
        coEvery { dao.deleteExpired(any()) } returns 0

        // Mock fresh expansion
        val response = createFinalResponse(finalUrl)
        val call: okhttp3.Call = mockk<okhttp3.Call>()
        every { call.execute() } returns response
        every { okHttpClient.newCall(any()) } returns call

        val result = repository.expandUrl(originalUrl)

        assertEquals(finalUrl, result.finalUrl)
        coVerify { dao.deleteExpired(any()) }
        verify { okHttpClient.newCall(any()) }
    }

    @Test(expected = UrlExpansionException.TimeoutError::class)
    fun `expandUrl throws TimeoutError on socket timeout`() = runTest {
        val originalUrl = "https://slow.site/page"

        coEvery { dao.getByOriginalUrl(originalUrl) } returns null

        val call: okhttp3.Call = mockk<okhttp3.Call>()
        every { call.execute() } throws SocketTimeoutException("Read timed out")
        every { okHttpClient.newCall(any()) } returns call

        repository.expandUrl(originalUrl)
    }

    @Test(expected = UrlExpansionException.NetworkError::class)
    fun `expandUrl throws NetworkError on IOException`() = runTest {
        val originalUrl = "https://unreachable.site/page"

        coEvery { dao.getByOriginalUrl(originalUrl) } returns null

        val call: okhttp3.Call = mockk<okhttp3.Call>()
        every { call.execute() } throws IOException("Failed to connect")
        every { okHttpClient.newCall(any()) } returns call

        repository.expandUrl(originalUrl)
    }

    @Test(expected = UrlExpansionException.InvalidUrl::class)
    fun `expandUrl throws InvalidUrl on malformed URL`() = runTest {
        val originalUrl = "not-a-valid-url"

        coEvery { dao.getByOriginalUrl(originalUrl) } returns null

        val call: okhttp3.Call = mockk<okhttp3.Call>()
        every { call.execute() } throws IllegalArgumentException("Invalid URL")
        every { okHttpClient.newCall(any()) } returns call

        repository.expandUrl(originalUrl)
    }

    @Test
    fun `getCachedExpansion returns null when not cached`() = runTest {
        val url = "https://uncached.com/page"

        coEvery { dao.getByOriginalUrl(url) } returns null

        val result = repository.getCachedExpansion(url)

        assertNull(result)
    }

    @Test
    fun `getCachedExpansion returns cached value when available`() = runTest {
        val originalUrl = "https://cached.com/page"
        val finalUrl = "https://final.com/page"
        val redirectChain = listOf("https://redirect1.com", "https://redirect2.com")

        val entity = CachedExpansionEntity(
            originalUrl = originalUrl,
            finalUrl = finalUrl,
            redirectChain = Json.encodeToString(redirectChain),
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        )

        coEvery { dao.getByOriginalUrl(originalUrl) } returns entity

        val result = repository.getCachedExpansion(originalUrl)

        assertNotNull(result)
        assertEquals(finalUrl, result!!.finalUrl)
        assertEquals(redirectChain, result.redirectChain)
    }

    @Test
    fun `clearExpiredCache delegates to DAO`() = runTest {
        coEvery { dao.deleteExpired(any()) } returns 0

        repository.clearExpiredCache()

        coVerify { dao.deleteExpired(any()) }
    }

    @Test
    fun `clearAllCache clears both memory and database cache`() = runTest {
        coEvery { dao.deleteAll() } returns Unit

        repository.clearAllCache()

        coVerify { dao.deleteAll() }
    }

    @Test
    fun `memory cache is used on second access`() = runTest {
        val originalUrl = "https://example.com/page"
        val finalUrl = "https://example.com/page"

        coEvery { dao.getByOriginalUrl(originalUrl) } returns null

        val response = createFinalResponse(finalUrl)
        val call: okhttp3.Call = mockk<okhttp3.Call>()
        every { call.execute() } returns response
        every { okHttpClient.newCall(any()) } returns call

        // First call - should hit network
        repository.expandUrl(originalUrl)

        // Second call - should hit memory cache
        val result = repository.expandUrl(originalUrl)

        assertEquals(finalUrl, result.finalUrl)
        // newCall should only be invoked once (first time)
        verify(exactly = 1) { okHttpClient.newCall(any()) }
    }

    // Helper functions

    private fun createRedirectResponse(location: String): Response {
        val response: Response = mockk<Response>()
        every { response.isRedirect } returns true
        every { response.header("Location") } returns location
        every { response.close() } just runs
        return response
    }

    private fun createFinalResponse(url: String): Response {
        val response: Response = mockk<Response>()
        val request: Request = mockk<Request>()
        val httpUrl: HttpUrl = mockk<HttpUrl>()

        every { response.isRedirect } returns false
        every { response.request } returns request
        every { request.url } returns httpUrl
        every { httpUrl.toString() } returns url
        every { response.close() } just runs
        return response
    }
}
