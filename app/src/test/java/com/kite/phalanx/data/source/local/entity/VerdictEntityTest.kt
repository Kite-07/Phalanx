package com.kite.phalanx.data.source.local.entity

import com.kite.phalanx.domain.model.Reason
import com.kite.phalanx.domain.model.SignalCode
import com.kite.phalanx.domain.model.Verdict
import com.kite.phalanx.domain.model.VerdictLevel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for VerdictEntity converter functions.
 * Tests JSON serialization/deserialization of verdict data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VerdictEntityTest {

    @Test
    fun `toEntity converts Verdict to VerdictEntity correctly`() {
        // Given
        val messageId = 1234567890L
        val verdict = Verdict(
            messageId = messageId.toString(),
            level = VerdictLevel.RED,
            score = 75,
            reasons = listOf(
                Reason(
                    code = SignalCode.SAFE_BROWSING_HIT,
                    label = "Malicious URL detected",
                    details = "URL flagged by Google Safe Browsing"
                ),
                Reason(
                    code = SignalCode.URLHAUS_LISTED,
                    label = "URLhaus threat",
                    details = "Listed in URLhaus database"
                )
            ),
            timestamp = 9876543210L
        )

        // When
        val entity = verdict.toEntity(messageId)

        // Then
        assertEquals(messageId, entity.messageId)
        assertEquals("RED", entity.level)
        assertEquals(75, entity.score)
        assertTrue(entity.reasons.contains("SAFE_BROWSING_HIT"))
        assertTrue(entity.reasons.contains("Malicious URL detected"))
        assertTrue(entity.reasons.contains("Google Safe Browsing"))
        assertTrue(entity.timestamp > 0)
    }

    @Test
    fun `toDomainModel converts VerdictEntity to Verdict correctly`() {
        // Given
        val messageId = 1234567890L
        val entity = VerdictEntity(
            messageId = messageId,
            level = "AMBER",
            score = 50,
            reasons = """
                [
                    {
                        "code": "SHORTENER_EXPANDED",
                        "label": "Shortened URL",
                        "details": "bit.ly expanded to example.com"
                    },
                    {
                        "code": "BRAND_IMPERSONATION",
                        "label": "Brand impersonation",
                        "details": "Domain impersonates known brand"
                    }
                ]
            """.trimIndent(),
            timestamp = 9876543210L
        )

        // When
        val verdict = entity.toDomainModel()

        // Then
        assertEquals(messageId.toString(), verdict.messageId)
        assertEquals(VerdictLevel.AMBER, verdict.level)
        assertEquals(50, verdict.score)
        assertEquals(2, verdict.reasons.size)
        assertEquals(SignalCode.SHORTENER_EXPANDED, verdict.reasons[0].code)
        assertEquals("Shortened URL", verdict.reasons[0].label)
        assertEquals("bit.ly expanded to example.com", verdict.reasons[0].details)
        assertEquals(SignalCode.BRAND_IMPERSONATION, verdict.reasons[1].code)
        assertEquals(9876543210L, verdict.timestamp)
    }

    @Test
    fun `toDomainModel handles empty reasons list`() {
        // Given
        val entity = VerdictEntity(
            messageId = 123L,
            level = "GREEN",
            score = 0,
            reasons = "[]",
            timestamp = 100L
        )

        // When
        val verdict = entity.toDomainModel()

        // Then
        assertEquals(VerdictLevel.GREEN, verdict.level)
        assertEquals(0, verdict.score)
        assertTrue(verdict.reasons.isEmpty())
    }

    @Test
    fun `toDomainModel handles malformed JSON gracefully`() {
        // Given
        val entity = VerdictEntity(
            messageId = 123L,
            level = "GREEN",
            score = 0,
            reasons = "not valid json",
            timestamp = 100L
        )

        // When
        val verdict = entity.toDomainModel()

        // Then
        // Should not crash, should return empty reasons list
        assertEquals(VerdictLevel.GREEN, verdict.level)
        assertTrue(verdict.reasons.isEmpty())
    }

    @Test
    fun `roundtrip conversion preserves data`() {
        // Given
        val originalMessageId = 9999999999L
        val originalVerdict = Verdict(
            messageId = originalMessageId.toString(),
            level = VerdictLevel.RED,
            score = 100,
            reasons = listOf(
                Reason(
                    code = SignalCode.SAFE_BROWSING_HIT,
                    label = "Critical threat",
                    details = "Multiple security issues detected"
                )
            ),
            timestamp = 1234567890L
        )

        // When
        val entity = originalVerdict.toEntity(originalMessageId)
        val convertedVerdict = entity.toDomainModel()

        // Then
        assertEquals(originalVerdict.messageId, convertedVerdict.messageId)
        assertEquals(originalVerdict.level, convertedVerdict.level)
        assertEquals(originalVerdict.score, convertedVerdict.score)
        assertEquals(originalVerdict.reasons.size, convertedVerdict.reasons.size)
        assertEquals(originalVerdict.reasons[0].code, convertedVerdict.reasons[0].code)
        assertEquals(originalVerdict.reasons[0].label, convertedVerdict.reasons[0].label)
        assertEquals(originalVerdict.reasons[0].details, convertedVerdict.reasons[0].details)
        // Note: timestamp will be different because toEntity() sets current time
        assertTrue(entity.timestamp > 0)
    }

    @Test
    fun `toEntity handles multiple reasons with special characters`() {
        // Given
        val verdict = Verdict(
            messageId = "123",
            level = VerdictLevel.AMBER,
            score = 60,
            reasons = listOf(
                Reason(
                    code = SignalCode.HOMOGLYPH_SUSPECT,
                    label = "Suspicious characters: \"quotes\" and 'apostrophes'",
                    details = "Domain contains unicode: Ƥaypal.com"
                )
            ),
            timestamp = 100L
        )

        // When
        val entity = verdict.toEntity(123L)

        // Then
        assertTrue(entity.reasons.contains("quotes"))
        assertTrue(entity.reasons.contains("apostrophes"))
        assertTrue(entity.reasons.contains("Ƥaypal.com"))
    }

    @Test
    fun `toEntity converts all VerdictLevel types correctly`() {
        // Test GREEN
        val greenVerdict = Verdict("1", VerdictLevel.GREEN, 0, emptyList(), 100L)
        assertEquals("GREEN", greenVerdict.toEntity(1L).level)

        // Test AMBER
        val amberVerdict = Verdict("2", VerdictLevel.AMBER, 50, emptyList(), 100L)
        assertEquals("AMBER", amberVerdict.toEntity(2L).level)

        // Test RED
        val redVerdict = Verdict("3", VerdictLevel.RED, 100, emptyList(), 100L)
        assertEquals("RED", redVerdict.toEntity(3L).level)
    }

    @Test
    fun `toDomainModel converts all SignalCode types correctly`() {
        // Test a variety of signal codes
        val signalCodes = listOf(
            SignalCode.SHORTENER_EXPANDED,
            SignalCode.SAFE_BROWSING_HIT,
            SignalCode.URLHAUS_LISTED,
            SignalCode.HOMOGLYPH_SUSPECT,
            SignalCode.BRAND_IMPERSONATION,
            SignalCode.HIGH_RISK_TLD,
            SignalCode.USERINFO_IN_URL,
            SignalCode.RAW_IP_HOST,
            SignalCode.EXCESSIVE_REDIRECTS,
            SignalCode.NON_STANDARD_PORT,
            SignalCode.HTTP_SCHEME
        )

        signalCodes.forEach { code ->
            val entity = VerdictEntity(
                messageId = 1L,
                level = "AMBER",
                score = 50,
                reasons = """[{"code": "${code.name}", "label": "Test", "details": "Test details"}]""",
                timestamp = 100L
            )

            val verdict = entity.toDomainModel()

            assertEquals(code, verdict.reasons[0].code)
        }
    }
}
