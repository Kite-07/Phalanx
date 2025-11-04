package com.kite.phalanx.data.source.local.dao

import androidx.room.Room
import com.kite.phalanx.data.source.local.AppDatabase
import org.robolectric.RuntimeEnvironment
import com.kite.phalanx.data.source.local.entity.VerdictEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for VerdictDao using Robolectric.
 * Tests database persistence, retrieval, and deletion operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VerdictDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var verdictDao: VerdictDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        // Use in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        verdictDao = database.verdictDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve verdict successfully`() = runTest {
        // Given
        val verdict = VerdictEntity(
            messageId = 123L,
            level = "RED",
            score = 75,
            reasons = """[{"code":"SAFEBROWSING_MALICIOUS","label":"Malware detected","details":"Flagged by Google"}]""",
            timestamp = 1000L
        )

        // When
        verdictDao.insert(verdict)
        val retrieved = verdictDao.getVerdictForMessage(123L)

        // Then
        assertNotNull(retrieved)
        assertEquals(123L, retrieved?.messageId)
        assertEquals("RED", retrieved?.level)
        assertEquals(75, retrieved?.score)
        assertTrue(retrieved?.reasons?.contains("SAFEBROWSING_MALICIOUS") == true)
        assertEquals(1000L, retrieved?.timestamp)
    }

    @Test
    fun `insert replaces existing verdict with same messageId`() = runTest {
        // Given - insert first verdict
        val verdict1 = VerdictEntity(
            messageId = 456L,
            level = "AMBER",
            score = 50,
            reasons = """[{"code":"SHORTENER_EXPANDED","label":"Short URL","details":"bit.ly"}]""",
            timestamp = 1000L
        )
        verdictDao.insert(verdict1)

        // When - insert second verdict with same messageId
        val verdict2 = VerdictEntity(
            messageId = 456L,
            level = "RED",
            score = 80,
            reasons = """[{"code":"SAFEBROWSING_MALICIOUS","label":"Malware","details":"Dangerous"}]""",
            timestamp = 2000L
        )
        verdictDao.insert(verdict2)

        // Then - should have replaced, not added
        val retrieved = verdictDao.getVerdictForMessage(456L)
        assertNotNull(retrieved)
        assertEquals("RED", retrieved?.level) // Updated value
        assertEquals(80, retrieved?.score) // Updated value
        assertEquals(2000L, retrieved?.timestamp) // Updated value
    }

    @Test
    fun `getVerdictForMessage returns null when not found`() = runTest {
        // When
        val retrieved = verdictDao.getVerdictForMessage(999L)

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `deleteVerdictForMessage removes specific verdict`() = runTest {
        // Given - insert two verdicts
        val verdict1 = VerdictEntity(111L, "RED", 75, "[]", 1000L)
        val verdict2 = VerdictEntity(222L, "GREEN", 0, "[]", 1000L)
        verdictDao.insert(verdict1)
        verdictDao.insert(verdict2)

        // When - delete only one
        verdictDao.deleteVerdictForMessage(111L)

        // Then
        assertNull(verdictDao.getVerdictForMessage(111L))
        assertNotNull(verdictDao.getVerdictForMessage(222L))
    }

    @Test
    fun `deleteAll removes all verdicts`() = runTest {
        // Given - insert multiple verdicts
        verdictDao.insert(VerdictEntity(1L, "RED", 75, "[]", 1000L))
        verdictDao.insert(VerdictEntity(2L, "AMBER", 50, "[]", 1000L))
        verdictDao.insert(VerdictEntity(3L, "GREEN", 0, "[]", 1000L))

        // When
        verdictDao.deleteAll()

        // Then
        assertNull(verdictDao.getVerdictForMessage(1L))
        assertNull(verdictDao.getVerdictForMessage(2L))
        assertNull(verdictDao.getVerdictForMessage(3L))
    }

    @Test
    fun `insert handles complex JSON reasons correctly`() = runTest {
        // Given - verdict with complex reasons JSON
        val complexReasons = """
            [
                {
                    "code": "SAFEBROWSING_MALICIOUS",
                    "label": "Malicious URL detected",
                    "details": "URL flagged by Google Safe Browsing with metadata: {\"platform\":\"ANDROID\"}"
                },
                {
                    "code": "URLHAUS_LISTED",
                    "label": "Known malware distribution",
                    "details": "Listed in URLhaus database since 2024-01-15"
                },
                {
                    "code": "HOMOGLYPH_SUSPECT",
                    "label": "Suspicious characters",
                    "details": "Domain contains: Ƥaypal.com (note special P)"
                }
            ]
        """.trimIndent()

        val verdict = VerdictEntity(
            messageId = 777L,
            level = "RED",
            score = 100,
            reasons = complexReasons,
            timestamp = 1000L
        )

        // When
        verdictDao.insert(verdict)
        val retrieved = verdictDao.getVerdictForMessage(777L)

        // Then
        assertNotNull(retrieved)
        assertTrue(retrieved?.reasons?.contains("SAFEBROWSING_MALICIOUS") == true)
        assertTrue(retrieved?.reasons?.contains("URLHAUS_LISTED") == true)
        assertTrue(retrieved?.reasons?.contains("HOMOGLYPH_SUSPECT") == true)
        assertTrue(retrieved?.reasons?.contains("Ƥaypal.com") == true)
    }

    @Test
    fun `insert handles all verdict levels correctly`() = runTest {
        // Given
        val greenVerdict = VerdictEntity(1L, "GREEN", 0, "[]", 1000L)
        val amberVerdict = VerdictEntity(2L, "AMBER", 50, "[]", 1000L)
        val redVerdict = VerdictEntity(3L, "RED", 100, "[]", 1000L)

        // When
        verdictDao.insert(greenVerdict)
        verdictDao.insert(amberVerdict)
        verdictDao.insert(redVerdict)

        // Then
        assertEquals("GREEN", verdictDao.getVerdictForMessage(1L)?.level)
        assertEquals("AMBER", verdictDao.getVerdictForMessage(2L)?.level)
        assertEquals("RED", verdictDao.getVerdictForMessage(3L)?.level)
    }

    @Test
    fun `insert handles empty reasons array`() = runTest {
        // Given
        val verdict = VerdictEntity(888L, "GREEN", 0, "[]", 1000L)

        // When
        verdictDao.insert(verdict)
        val retrieved = verdictDao.getVerdictForMessage(888L)

        // Then
        assertNotNull(retrieved)
        assertEquals("[]", retrieved?.reasons)
    }

    @Test
    fun `insert handles very long messageId`() = runTest {
        // Given - use actual SMS timestamp format (13 digits)
        val messageId = System.currentTimeMillis()
        val verdict = VerdictEntity(
            messageId = messageId,
            level = "AMBER",
            score = 60,
            reasons = "[]",
            timestamp = 1000L
        )

        // When
        verdictDao.insert(verdict)
        val retrieved = verdictDao.getVerdictForMessage(messageId)

        // Then
        assertNotNull(retrieved)
        assertEquals(messageId, retrieved?.messageId)
    }

    @Test
    fun `concurrent inserts with different messageIds succeed`() = runTest {
        // Given
        val verdict1 = VerdictEntity(100L, "RED", 75, "[]", 1000L)
        val verdict2 = VerdictEntity(200L, "AMBER", 50, "[]", 1000L)
        val verdict3 = VerdictEntity(300L, "GREEN", 0, "[]", 1000L)

        // When - insert concurrently (in rapid succession)
        verdictDao.insert(verdict1)
        verdictDao.insert(verdict2)
        verdictDao.insert(verdict3)

        // Then - all should be retrievable
        assertNotNull(verdictDao.getVerdictForMessage(100L))
        assertNotNull(verdictDao.getVerdictForMessage(200L))
        assertNotNull(verdictDao.getVerdictForMessage(300L))
    }

    @Test
    fun `insert preserves score values correctly`() = runTest {
        // Test various score values
        val scores = listOf(0, 25, 50, 75, 100)
        scores.forEachIndexed { index, score ->
            val verdict = VerdictEntity(
                messageId = index.toLong(),
                level = "AMBER",
                score = score,
                reasons = "[]",
                timestamp = 1000L
            )
            verdictDao.insert(verdict)

            val retrieved = verdictDao.getVerdictForMessage(index.toLong())
            assertEquals(score, retrieved?.score)
        }
    }
}
