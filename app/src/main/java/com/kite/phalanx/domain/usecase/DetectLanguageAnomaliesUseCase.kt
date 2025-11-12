package com.kite.phalanx.domain.usecase

import com.kite.phalanx.domain.model.Signal
import com.kite.phalanx.domain.model.SignalCode
import javax.inject.Inject

/**
 * Use case for detecting language anomalies in message text.
 *
 * Per PRD Phase 6: Lightweight language cues as security signals.
 * These are "minor bumps" with low weights to avoid false positives.
 *
 * Detection Rules:
 * 1. ZERO_WIDTH_CHARS: Hidden zero-width characters (U+200B, U+200C, U+200D, U+FEFF)
 * 2. WEIRD_CAPS: Alternating capitalization patterns (e.g., "hElLo WoRlD")
 * 3. DOUBLED_SPACES: Excessive consecutive spaces (3+ spaces in a row)
 * 4. EXCESSIVE_UNICODE: High ratio of non-ASCII characters or excessive emojis
 *
 * Weights (low to avoid false positives):
 * - ZERO_WIDTH_CHARS: 10 (deliberate obfuscation)
 * - WEIRD_CAPS: 5 (minor indicator)
 * - DOUBLED_SPACES: 3 (minor formatting issue)
 * - EXCESSIVE_UNICODE: 8 (potential obfuscation or spam)
 */
class DetectLanguageAnomaliesUseCase @Inject constructor() {

    companion object {
        // Zero-width characters used for text obfuscation
        private val ZERO_WIDTH_CHARS = setOf(
            '\u200B', // Zero-width space
            '\u200C', // Zero-width non-joiner
            '\u200D', // Zero-width joiner
            '\uFEFF', // Zero-width no-break space (BOM)
            '\u2060', // Word joiner
            '\u180E'  // Mongolian vowel separator
        )

        // Thresholds for detection
        private const val MIN_ALTERNATING_CAPS_RATIO = 0.4 // 40% alternating pattern
        private const val MIN_CAPS_FOR_DETECTION = 3 // At least 3 alternating caps
        private const val MAX_CONSECUTIVE_SPACES = 2 // Trigger if more than 2 spaces
        private const val EXCESSIVE_UNICODE_RATIO = 0.5 // 50% non-ASCII characters
        private const val MIN_LENGTH_FOR_UNICODE_CHECK = 20 // Only check longer messages
        private const val EXCESSIVE_EMOJI_COUNT = 10 // More than 10 emojis is excessive
    }

    /**
     * Detect language anomalies in message text.
     *
     * @param messageBody The full message text to analyze
     * @return List of detected language anomaly signals
     */
    fun execute(messageBody: String): List<Signal> {
        val signals = mutableListOf<Signal>()

        // Check for zero-width characters
        if (hasZeroWidthChars(messageBody)) {
            signals.add(
                Signal(
                    code = SignalCode.ZERO_WIDTH_CHARS,
                    weight = 10,
                    metadata = mapOf(
                        "count" to countZeroWidthChars(messageBody).toString()
                    )
                )
            )
        }

        // Check for weird capitalization patterns
        if (hasWeirdCaps(messageBody)) {
            signals.add(
                Signal(
                    code = SignalCode.WEIRD_CAPS,
                    weight = 5,
                    metadata = mapOf(
                        "sample" to getWeirdCapsExample(messageBody)
                    )
                )
            )
        }

        // Check for doubled/excessive spaces
        if (hasDoubledSpaces(messageBody)) {
            signals.add(
                Signal(
                    code = SignalCode.DOUBLED_SPACES,
                    weight = 3,
                    metadata = mapOf(
                        "maxConsecutive" to getMaxConsecutiveSpaces(messageBody).toString()
                    )
                )
            )
        }

        // Check for excessive unicode/emoji
        if (hasExcessiveUnicode(messageBody)) {
            signals.add(
                Signal(
                    code = SignalCode.EXCESSIVE_UNICODE,
                    weight = 8,
                    metadata = mapOf(
                        "unicodeRatio" to String.format("%.2f", getUnicodeRatio(messageBody)),
                        "emojiCount" to countEmojis(messageBody).toString()
                    )
                )
            )
        }

        return signals
    }

    /**
     * Check if text contains zero-width characters.
     */
    private fun hasZeroWidthChars(text: String): Boolean {
        return text.any { it in ZERO_WIDTH_CHARS }
    }

    /**
     * Count zero-width characters in text.
     */
    private fun countZeroWidthChars(text: String): Int {
        return text.count { it in ZERO_WIDTH_CHARS }
    }

    /**
     * Check for weird capitalization patterns (aLtErNaTiNg caps).
     *
     * Algorithm:
     * 1. Find sequences of letters
     * 2. Check for alternating uppercase/lowercase patterns
     * 3. Trigger if pattern ratio exceeds threshold
     */
    private fun hasWeirdCaps(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.length < MIN_CAPS_FOR_DETECTION * 2) return false

        var alternatingCount = 0
        var previousWasUpper = letters.first().isUpperCase()

        for (i in 1 until letters.length) {
            val currentIsUpper = letters[i].isUpperCase()
            if (currentIsUpper != previousWasUpper) {
                alternatingCount++
            }
            previousWasUpper = currentIsUpper
        }

        val alternatingRatio = alternatingCount.toDouble() / (letters.length - 1)
        return alternatingRatio >= MIN_ALTERNATING_CAPS_RATIO &&
               alternatingCount >= MIN_CAPS_FOR_DETECTION
    }

    /**
     * Get an example of weird caps pattern for metadata.
     */
    private fun getWeirdCapsExample(text: String): String {
        val words = text.split(Regex("\\s+"))
        val weirdWord = words.find { word ->
            val letters = word.filter { it.isLetter() }
            if (letters.length < 3) return@find false

            var alternating = 0
            var prevUpper = letters.first().isUpperCase()
            for (i in 1 until letters.length) {
                val currUpper = letters[i].isUpperCase()
                if (currUpper != prevUpper) alternating++
                prevUpper = currUpper
            }
            alternating >= 2
        }
        return weirdWord?.take(20) ?: text.take(20)
    }

    /**
     * Check for excessive consecutive spaces.
     */
    private fun hasDoubledSpaces(text: String): Boolean {
        return getMaxConsecutiveSpaces(text) > MAX_CONSECUTIVE_SPACES
    }

    /**
     * Get maximum consecutive spaces in text.
     */
    private fun getMaxConsecutiveSpaces(text: String): Int {
        var maxConsecutive = 0
        var currentConsecutive = 0

        for (char in text) {
            if (char == ' ') {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else {
                currentConsecutive = 0
            }
        }

        return maxConsecutive
    }

    /**
     * Check for excessive Unicode characters or emojis.
     *
     * Criteria:
     * 1. High ratio (>50%) of non-ASCII characters in longer messages
     * 2. Excessive emoji count (>10 emojis)
     */
    private fun hasExcessiveUnicode(text: String): Boolean {
        // Check emoji count first
        val emojiCount = countEmojis(text)
        if (emojiCount > EXCESSIVE_EMOJI_COUNT) return true

        // Check unicode ratio for longer messages
        if (text.length < MIN_LENGTH_FOR_UNICODE_CHECK) return false

        val unicodeRatio = getUnicodeRatio(text)
        return unicodeRatio > EXCESSIVE_UNICODE_RATIO
    }

    /**
     * Get ratio of non-ASCII characters in text.
     */
    private fun getUnicodeRatio(text: String): Double {
        if (text.isEmpty()) return 0.0

        val nonAsciiCount = text.count { it.code > 127 }
        return nonAsciiCount.toDouble() / text.length
    }

    /**
     * Count emojis in text.
     * Simplified: counts characters in emoji ranges (U+1F300-U+1F9FF)
     */
    private fun countEmojis(text: String): Int {
        return text.count { char ->
            val code = char.code
            // Emoticons and Miscellaneous Symbols
            (code in 0x1F300..0x1F6FF) ||
            // Supplemental Symbols and Pictographs
            (code in 0x1F900..0x1F9FF) ||
            // Emoticons range
            (code in 0x1F600..0x1F64F) ||
            // Transport and Map Symbols
            (code in 0x1F680..0x1F6FF) ||
            // Miscellaneous Symbols
            (code in 0x2600..0x26FF) ||
            // Dingbats
            (code in 0x2700..0x27BF)
        }
    }
}
