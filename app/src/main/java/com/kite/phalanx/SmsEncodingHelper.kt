package com.kite.phalanx

/**
 * Helper for SMS encoding detection and segment calculation
 *
 * SMS can use two encodings:
 * - GSM-7: 160 chars/segment (single) or 153 chars/segment (multi-part)
 * - UCS-2 (Unicode): 70 chars/segment (single) or 67 chars/segment (multi-part)
 */
object SmsEncodingHelper {

    /**
     * GSM 7-bit default alphabet
     * Source: GSM 03.38 standard
     */
    private val GSM_7BIT_CHARS = setOf(
        '@', '£', '$', '¥', 'è', 'é', 'ù', 'ì', 'ò', 'Ç', '\n', 'Ø', 'ø', '\r', 'Å', 'å',
        'Δ', '_', 'Φ', 'Γ', 'Λ', 'Ω', 'Π', 'Ψ', 'Σ', 'Θ', 'Ξ', ' ', 'Æ', 'æ', 'ß', 'É',
        '!', '"', '#', '¤', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?',
        '¡', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
        'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'Ä', 'Ö', 'Ñ', 'Ü', '§',
        '¿', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
        'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'ä', 'ö', 'ñ', 'ü', 'à'
    )

    /**
     * GSM 7-bit extended characters (count as 2 characters)
     * These require an escape sequence
     */
    private val GSM_7BIT_EXTENDED = setOf(
        '|', '^', '€', '{', '}', '[', ']', '~', '\\'
    )

    /**
     * Encoding type for SMS
     */
    enum class SmsEncoding {
        GSM_7BIT,
        UCS2
    }

    /**
     * SMS segment information
     */
    data class SmsSegmentInfo(
        val encoding: SmsEncoding,
        val charCount: Int,
        val encodedLength: Int, // Length accounting for extended chars
        val segmentCount: Int,
        val charsPerSegment: Int,
        val charsRemaining: Int,
        val isApproachingLimit: Boolean // true when within 10 chars of segment limit
    )

    /**
     * Analyze text and return segment information
     */
    fun analyzeText(text: String): SmsSegmentInfo {
        val encoding = detectEncoding(text)
        val encodedLength = calculateEncodedLength(text, encoding)

        val (singleLimit, multiLimit) = when (encoding) {
            SmsEncoding.GSM_7BIT -> Pair(160, 153)
            SmsEncoding.UCS2 -> Pair(70, 67)
        }

        val segmentCount = when {
            encodedLength == 0 -> 1
            encodedLength <= singleLimit -> 1
            else -> (encodedLength + multiLimit - 1) / multiLimit
        }

        val charsPerSegment = if (segmentCount == 1) singleLimit else multiLimit
        val charsRemaining = (charsPerSegment * segmentCount) - encodedLength
        val isApproachingLimit = charsRemaining <= 10

        return SmsSegmentInfo(
            encoding = encoding,
            charCount = text.length,
            encodedLength = encodedLength,
            segmentCount = segmentCount,
            charsPerSegment = charsPerSegment,
            charsRemaining = charsRemaining,
            isApproachingLimit = isApproachingLimit
        )
    }

    /**
     * Detect which encoding the text requires
     */
    private fun detectEncoding(text: String): SmsEncoding {
        for (char in text) {
            if (char !in GSM_7BIT_CHARS && char !in GSM_7BIT_EXTENDED) {
                return SmsEncoding.UCS2
            }
        }
        return SmsEncoding.GSM_7BIT
    }

    /**
     * Calculate encoded length (GSM-7 extended chars count as 2)
     */
    private fun calculateEncodedLength(text: String, encoding: SmsEncoding): Int {
        if (encoding == SmsEncoding.UCS2) {
            return text.length
        }

        var length = 0
        for (char in text) {
            length += if (char in GSM_7BIT_EXTENDED) 2 else 1
        }
        return length
    }

    /**
     * Format segment info for display
     * Examples:
     * - "145" (single segment, plenty of space)
     * - "155 (1/1)" (single segment, close to limit)
     * - "150 (1/2)" (multi-segment)
     */
    fun formatSegmentDisplay(info: SmsSegmentInfo): String {
        return when {
            info.segmentCount == 1 && !info.isApproachingLimit -> {
                // Single segment with plenty of space - just show char count
                "${info.encodedLength}"
            }
            info.segmentCount == 1 && info.isApproachingLimit -> {
                // Single segment but approaching limit - show count and segment
                "${info.encodedLength} (1/1)"
            }
            else -> {
                // Multi-segment - always show segments
                "${info.encodedLength} (${getCurrentSegment(info)}/${info.segmentCount})"
            }
        }
    }

    /**
     * Get the current segment number (1-based)
     */
    private fun getCurrentSegment(info: SmsSegmentInfo): Int {
        if (info.encodedLength == 0) return 1
        val segment = (info.encodedLength + info.charsPerSegment - 1) / info.charsPerSegment
        return segment.coerceAtLeast(1)
    }

    /**
     * Get encoding name for display
     */
    fun getEncodingName(encoding: SmsEncoding): String {
        return when (encoding) {
            SmsEncoding.GSM_7BIT -> "GSM-7"
            SmsEncoding.UCS2 -> "Unicode"
        }
    }
}
