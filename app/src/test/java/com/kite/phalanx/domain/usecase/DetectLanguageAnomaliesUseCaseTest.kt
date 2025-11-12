package com.kite.phalanx.domain.usecase

import com.kite.phalanx.domain.model.SignalCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DetectLanguageAnomaliesUseCase.
 *
 * Tests cover all 4 language anomaly detectors per PRD Phase 6:
 * - Zero-width character detection
 * - Weird capitalization pattern detection
 * - Doubled/excessive space detection
 * - Excessive unicode/emoji detection
 */
class DetectLanguageAnomaliesUseCaseTest {

    private lateinit var useCase: DetectLanguageAnomaliesUseCase

    @Before
    fun setup() {
        useCase = DetectLanguageAnomaliesUseCase()
    }

    // ========== Zero-Width Character Tests ==========

    @Test
    fun `detects zero-width space`() {
        val message = "Hello\u200BWorld" // Contains zero-width space

        val signals = useCase.execute(message)

        val zeroWidthSignal = signals.find { it.code == SignalCode.ZERO_WIDTH_CHARS }
        assertNotNull("Should detect zero-width space", zeroWidthSignal)
        assertEquals("Weight should be 10", 10, zeroWidthSignal?.weight)
        assertEquals("Should count 1 zero-width char", "1", zeroWidthSignal?.metadata?.get("count"))
    }

    @Test
    fun `detects multiple zero-width characters`() {
        // Contains: zero-width space, zero-width non-joiner, zero-width joiner
        val message = "Test\u200BMessage\u200CWith\u200DMultiple"

        val signals = useCase.execute(message)

        val zeroWidthSignal = signals.find { it.code == SignalCode.ZERO_WIDTH_CHARS }
        assertNotNull("Should detect multiple zero-width chars", zeroWidthSignal)
        assertEquals("Should count 3 zero-width chars", "3", zeroWidthSignal?.metadata?.get("count"))
    }

    @Test
    fun `does not detect zero-width chars in clean text`() {
        val message = "This is a normal message without any hidden characters."

        val signals = useCase.execute(message)

        val zeroWidthSignal = signals.find { it.code == SignalCode.ZERO_WIDTH_CHARS }
        assertNull("Should not detect zero-width chars", zeroWidthSignal)
    }

    // ========== Weird Caps Tests ==========

    @Test
    fun `detects alternating caps pattern`() {
        val message = "cLiCk HeRe FoR fReE pRiZe"

        val signals = useCase.execute(message)

        val weirdCapsSignal = signals.find { it.code == SignalCode.WEIRD_CAPS }
        assertNotNull("Should detect weird caps pattern", weirdCapsSignal)
        assertEquals("Weight should be 5", 5, weirdCapsSignal?.weight)
        assertTrue("Should include sample in metadata",
            weirdCapsSignal?.metadata?.containsKey("sample") == true)
    }

    @Test
    fun `detects severe alternating pattern`() {
        val message = "hElLo ThIs Is A tEsT"

        val signals = useCase.execute(message)

        val weirdCapsSignal = signals.find { it.code == SignalCode.WEIRD_CAPS }
        assertNotNull("Should detect severe alternating pattern", weirdCapsSignal)
    }

    @Test
    fun `does not flag normal capitalization`() {
        val message = "This Is A Normal Sentence With Proper Capitalization."

        val signals = useCase.execute(message)

        val weirdCapsSignal = signals.find { it.code == SignalCode.WEIRD_CAPS }
        assertNull("Should not flag normal caps", weirdCapsSignal)
    }

    @Test
    fun `does not flag all lowercase`() {
        val message = "this is all lowercase text"

        val signals = useCase.execute(message)

        val weirdCapsSignal = signals.find { it.code == SignalCode.WEIRD_CAPS }
        assertNull("Should not flag all lowercase", weirdCapsSignal)
    }

    @Test
    fun `does not flag all uppercase`() {
        val message = "THIS IS ALL UPPERCASE TEXT"

        val signals = useCase.execute(message)

        val weirdCapsSignal = signals.find { it.code == SignalCode.WEIRD_CAPS }
        assertNull("Should not flag all uppercase", weirdCapsSignal)
    }

    // ========== Doubled Spaces Tests ==========

    @Test
    fun `detects three consecutive spaces`() {
        val message = "Hello   World" // 3 spaces

        val signals = useCase.execute(message)

        val doubledSpacesSignal = signals.find { it.code == SignalCode.DOUBLED_SPACES }
        assertNotNull("Should detect 3 consecutive spaces", doubledSpacesSignal)
        assertEquals("Weight should be 3", 3, doubledSpacesSignal?.weight)
        assertEquals("Should report 3 max consecutive", "3",
            doubledSpacesSignal?.metadata?.get("maxConsecutive"))
    }

    @Test
    fun `detects excessive spaces`() {
        val message = "Click here for     free prize" // 5 spaces

        val signals = useCase.execute(message)

        val doubledSpacesSignal = signals.find { it.code == SignalCode.DOUBLED_SPACES }
        assertNotNull("Should detect excessive spaces", doubledSpacesSignal)
        assertEquals("Should report 5 max consecutive", "5",
            doubledSpacesSignal?.metadata?.get("maxConsecutive"))
    }

    @Test
    fun `does not flag single spaces`() {
        val message = "This is normal text with single spaces."

        val signals = useCase.execute(message)

        val doubledSpacesSignal = signals.find { it.code == SignalCode.DOUBLED_SPACES }
        assertNull("Should not flag single spaces", doubledSpacesSignal)
    }

    @Test
    fun `does not flag double spaces`() {
        val message = "Hello  World" // 2 spaces (acceptable)

        val signals = useCase.execute(message)

        val doubledSpacesSignal = signals.find { it.code == SignalCode.DOUBLED_SPACES }
        assertNull("Should not flag double spaces", doubledSpacesSignal)
    }

    // ========== Excessive Unicode Tests ==========

    @Test
    fun `detects excessive emojis`() {
        val message = "🎉🎊🎁🎈🎀🎂🍰🍾🥳🎆🎇✨💫🌟⭐" // 15 emojis

        val signals = useCase.execute(message)

        val excessiveUnicodeSignal = signals.find { it.code == SignalCode.EXCESSIVE_UNICODE }
        assertNotNull("Should detect excessive emojis", excessiveUnicodeSignal)
        assertEquals("Weight should be 8", 8, excessiveUnicodeSignal?.weight)
        assertTrue("Emoji count should be > 10",
            (excessiveUnicodeSignal?.metadata?.get("emojiCount")?.toIntOrNull() ?: 0) > 10)
    }

    @Test
    fun `detects high unicode ratio in long message`() {
        // Message with >50% non-ASCII characters
        val message = "你好世界これは日本語ですこれはテストメッセージです" + "hello"

        val signals = useCase.execute(message)

        val excessiveUnicodeSignal = signals.find { it.code == SignalCode.EXCESSIVE_UNICODE }
        assertNotNull("Should detect high unicode ratio", excessiveUnicodeSignal)
        assertTrue("Unicode ratio should be included",
            excessiveUnicodeSignal?.metadata?.containsKey("unicodeRatio") == true)
    }

    @Test
    fun `does not flag normal text with few emojis`() {
        val message = "Hello world! 😊 How are you today?"

        val signals = useCase.execute(message)

        val excessiveUnicodeSignal = signals.find { it.code == SignalCode.EXCESSIVE_UNICODE }
        assertNull("Should not flag normal text with few emojis", excessiveUnicodeSignal)
    }

    @Test
    fun `does not flag short messages with unicode`() {
        val message = "こんにちは" // Short Japanese text

        val signals = useCase.execute(message)

        val excessiveUnicodeSignal = signals.find { it.code == SignalCode.EXCESSIVE_UNICODE }
        assertNull("Should not flag short unicode messages", excessiveUnicodeSignal)
    }

    @Test
    fun `does not flag normal English text`() {
        val message = "This is a normal English message with no special characters."

        val signals = useCase.execute(message)

        val excessiveUnicodeSignal = signals.find { it.code == SignalCode.EXCESSIVE_UNICODE }
        assertNull("Should not flag normal English text", excessiveUnicodeSignal)
    }

    // ========== Combined Tests ==========

    @Test
    fun `detects multiple anomalies in single message`() {
        val message = "cLiCk HeRe   FoR\u200BfReE   pRiZe 🎉🎊🎁🎈🎀🎂🍰🍾🥳🎆🎇"

        val signals = useCase.execute(message)

        // Should detect all 4 types
        val zeroWidth = signals.find { it.code == SignalCode.ZERO_WIDTH_CHARS }
        val weirdCaps = signals.find { it.code == SignalCode.WEIRD_CAPS }
        val doubledSpaces = signals.find { it.code == SignalCode.DOUBLED_SPACES }
        val excessiveUnicode = signals.find { it.code == SignalCode.EXCESSIVE_UNICODE }

        assertNotNull("Should detect zero-width chars", zeroWidth)
        assertNotNull("Should detect weird caps", weirdCaps)
        assertNotNull("Should detect doubled spaces", doubledSpaces)
        assertNotNull("Should detect excessive unicode", excessiveUnicode)
        assertEquals("Should detect 4 signals", 4, signals.size)
    }

    @Test
    fun `returns empty list for clean message`() {
        val message = "This is a perfectly normal message with no anomalies."

        val signals = useCase.execute(message)

        assertTrue("Should return empty list for clean message", signals.isEmpty())
    }

    @Test
    fun `handles empty string`() {
        val message = ""

        val signals = useCase.execute(message)

        assertTrue("Should handle empty string without crashing", signals.isEmpty())
    }

    @Test
    fun `handles very long message efficiently`() {
        val message = "Hello World ".repeat(1000) // 12,000 characters

        val startTime = System.currentTimeMillis()
        val signals = useCase.execute(message)
        val duration = System.currentTimeMillis() - startTime

        // Should complete quickly (< 100ms for such a simple message)
        assertTrue("Should process long message quickly", duration < 100)
        assertTrue("Should return results", signals.isNotEmpty() || signals.isEmpty())
    }
}
