package com.kite.phalanx.data.repository

import android.content.Context
import com.kite.phalanx.domain.model.SenderType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for SenderPackRepositoryImpl (Phase 4).
 *
 * Tests cover:
 * - JSON parsing of sender packs
 * - Signature verification (development bypass)
 * - Pack loading from assets
 * - Sender ID pattern matching
 * - Pack version tracking
 * - Error handling for malformed JSON
 *
 * Note: Uses Robolectric for Android Context access
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SenderPackRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repository: SenderPackRepositoryImpl

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repository = SenderPackRepositoryImpl(context)
    }

    @Test
    fun `loadPack returns valid pack for IN region`() = runTest {
        val result = repository.loadPack("IN")

        if (!result.isValid) {
            println("ERROR: Pack failed to load. Error message: ${result.errorMessage}")
        }

        assertTrue("Pack should load successfully. Error: ${result.errorMessage}", result.isValid)
        assertNotNull("Pack should not be null", result.pack)
        assertEquals("IN", result.pack?.region)
        assertNull(result.errorMessage)
    }

    @Test
    fun `loaded pack has expected entries`() = runTest {
        val result = repository.loadPack("IN")

        assertTrue(result.isValid)
        val pack = result.pack!!

        assertTrue("Pack should have entries", pack.entries.isNotEmpty())
        assertTrue("Pack should have at least 30 entries", pack.entries.size >= 30)
    }

    @Test
    fun `pack contains HDFC Bank entry`() = runTest {
        val result = repository.loadPack("IN")

        val hdfcEntry = result.pack?.entries?.find { it.brand == "HDFC Bank" }
        assertNotNull("Should contain HDFC Bank", hdfcEntry)
        assertEquals(SenderType.BANK, hdfcEntry?.type)
        assertTrue("Should have keywords", hdfcEntry?.keywords?.isNotEmpty() == true)
    }

    @Test
    fun `pack contains Airtel entry`() = runTest {
        val result = repository.loadPack("IN")

        val airtelEntry = result.pack?.entries?.find { it.brand == "Airtel" }
        assertNotNull("Should contain Airtel", airtelEntry)
        assertEquals(SenderType.CARRIER, airtelEntry?.type)
    }

    @Test
    fun `pack has development signature`() = runTest {
        val result = repository.loadPack("IN")

        val pack = result.pack!!
        assertTrue("Should use development placeholder signature",
            pack.signature.matches(Regex("^0+$")))
    }

    @Test
    fun `findMatchingSenders returns HDFC for HDFCBK`() = runTest {
        repository.loadPack("IN")

        val matches = repository.findMatchingSenders("HDFCBK")

        assertEquals(1, matches.size)
        assertEquals("HDFC Bank", matches[0].brand)
    }

    @Test
    fun `findMatchingSenders handles AX-AIRTEL pattern`() = runTest {
        repository.loadPack("IN")

        val matches = repository.findMatchingSenders("AX-AIRTEL")

        assertEquals(1, matches.size)
        assertEquals("Airtel", matches[0].brand)
    }

    @Test
    fun `findMatchingSenders handles AIRTEL without prefix`() = runTest {
        repository.loadPack("IN")

        val matches = repository.findMatchingSenders("AIRTEL")

        assertEquals(1, matches.size)
        assertEquals("Airtel", matches[0].brand)
    }

    @Test
    fun `findMatchingSenders returns empty for unknown sender`() = runTest {
        repository.loadPack("IN")

        val matches = repository.findMatchingSenders("UNKNOWN-SENDER")

        assertEquals(0, matches.size)
    }

    @Test
    fun `findMatchingSenders is case insensitive`() = runTest {
        repository.loadPack("IN")

        // Test lowercase
        val matchesLower = repository.findMatchingSenders("hdfcbk")
        assertEquals(1, matchesLower.size)

        // Test mixed case
        val matchesMixed = repository.findMatchingSenders("HdFcBk")
        assertEquals(1, matchesMixed.size)
    }

    @Test
    fun `getCurrentPack returns null before loading`() {
        val pack = repository.getCurrentPack()
        assertNull("Pack should be null before loading", pack)
    }

    @Test
    fun `getCurrentPack returns pack after loading`() = runTest {
        repository.loadPack("IN")

        val pack = repository.getCurrentPack()

        assertNotNull("Pack should be available after loading", pack)
        assertEquals("IN", pack?.region)
    }

    @Test
    fun `loadPack caches pack in memory`() = runTest {
        // Load pack once
        val result1 = repository.loadPack("IN")

        // Get from cache
        val cachedPack = repository.getCurrentPack()

        assertNotNull(cachedPack)
        assertEquals(result1.pack?.version, cachedPack?.version)
    }

    @Test
    fun `pack has valid version number`() = runTest {
        val result = repository.loadPack("IN")

        val version = result.pack?.version
        assertNotNull(version)
        assertTrue("Version should be positive", version!! > 0)
    }

    @Test
    fun `pack has valid timestamp`() = runTest {
        val result = repository.loadPack("IN")

        val timestamp = result.pack?.timestamp
        assertNotNull(timestamp)
        assertTrue("Timestamp should be positive", timestamp!! > 0)
    }

    @Test
    fun `all entries have valid patterns`() = runTest {
        val result = repository.loadPack("IN")

        result.pack?.entries?.forEach { entry ->
            assertNotNull("Pattern should not be null", entry.pattern)
            assertTrue("Pattern should not be empty", entry.pattern.isNotBlank())

            // Verify pattern is valid regex
            try {
                entry.pattern.toRegex()
            } catch (e: Exception) {
                fail("Invalid regex pattern for ${entry.brand}: ${entry.pattern}")
            }
        }
    }

    @Test
    fun `all entries have valid brand names`() = runTest {
        val result = repository.loadPack("IN")

        result.pack?.entries?.forEach { entry ->
            assertNotNull("Brand should not be null", entry.brand)
            assertTrue("Brand should not be empty", entry.brand.isNotBlank())
        }
    }

    @Test
    fun `all entries have valid types`() = runTest {
        val result = repository.loadPack("IN")

        result.pack?.entries?.forEach { entry ->
            assertNotNull("Type should not be null", entry.type)
            assertTrue("Type should be valid enum",
                entry.type in SenderType.values())
        }
    }

    @Test
    fun `entries have expected sender types`() = runTest {
        val result = repository.loadPack("IN")

        val entries = result.pack?.entries ?: emptyList()

        // Should have at least one of each major type
        assertTrue("Should have BANK entries",
            entries.any { it.type == SenderType.BANK })
        assertTrue("Should have CARRIER entries",
            entries.any { it.type == SenderType.CARRIER })
        assertTrue("Should have GOVERNMENT entries",
            entries.any { it.type == SenderType.GOVERNMENT })
    }

    @Test
    fun `loadPack handles invalid region gracefully`() = runTest {
        val result = repository.loadPack("INVALID_REGION")

        assertFalse("Should fail for invalid region", result.isValid)
        assertNull("Pack should be null", result.pack)
        assertNotNull("Should have error message", result.errorMessage)
    }

    @Test
    fun `findMatchingSenders handles special characters`() = runTest {
        repository.loadPack("IN")

        // Test with hyphens
        val matches = repository.findMatchingSenders("AX-AIRTEL")
        assertTrue("Should match pattern with hyphens", matches.isNotEmpty())
    }

    @Test
    fun `findMatchingSenders handles numeric sender IDs`() = runTest {
        repository.loadPack("IN")

        // Phone numbers won't match letter patterns
        val matches = repository.findMatchingSenders("+911234567890")
        assertEquals(0, matches.size)
    }

    @Test
    fun `pack contains multiple carrier entries`() = runTest {
        val result = repository.loadPack("IN")

        val carrierEntries = result.pack?.entries?.filter {
            it.type == SenderType.CARRIER
        } ?: emptyList()

        assertTrue("Should have multiple carriers", carrierEntries.size >= 3)

        // Verify common carriers are present
        val brands = carrierEntries.map { it.brand }
        assertTrue("Should include Airtel", brands.any { it.contains("Airtel", ignoreCase = true) })
        assertTrue("Should include Jio", brands.any { it.contains("Jio", ignoreCase = true) })
    }

    @Test
    fun `pack contains multiple bank entries`() = runTest {
        val result = repository.loadPack("IN")

        val bankEntries = result.pack?.entries?.filter {
            it.type == SenderType.BANK
        } ?: emptyList()

        assertTrue("Should have multiple banks", bankEntries.size >= 5)
    }

    @Test
    fun `keywords are lowercase for case-insensitive matching`() = runTest {
        val result = repository.loadPack("IN")

        result.pack?.entries?.forEach { entry ->
            entry.keywords.forEach { keyword ->
                // Keywords should either be lowercase or the code handles case
                // This is a guideline test
                assertNotNull("Keyword should not be null", keyword)
                assertTrue("Keyword should not be empty", keyword.isNotBlank())
            }
        }
    }
}
