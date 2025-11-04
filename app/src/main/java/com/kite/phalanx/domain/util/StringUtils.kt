package com.kite.phalanx.domain.util

import kotlin.math.min

/**
 * String utilities for security analysis.
 *
 * Stage 1B Enhancement: Typosquatting detection using Levenshtein distance.
 */
object StringUtils {

    /**
     * Calculate Levenshtein distance between two strings.
     *
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string into another.
     *
     * Used to detect typosquatting: paypal.com vs paypa1.com (distance = 1)
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Levenshtein distance (0 means identical, higher means more different)
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        // Early exit for identical strings
        if (s1 == s2) return 0

        // Early exit if one string is empty
        if (len1 == 0) return len2
        if (len2 == 0) return len1

        // Create distance matrix
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        // Initialize first row and column
        for (i in 0..len1) {
            dp[i][0] = i
        }
        for (j in 0..len2) {
            dp[0][j] = j
        }

        // Calculate distances
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1

                dp[i][j] = min(
                    min(
                        dp[i - 1][j] + 1,      // deletion
                        dp[i][j - 1] + 1       // insertion
                    ),
                    dp[i - 1][j - 1] + cost    // substitution
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Check if two domains are typosquatting variants.
     *
     * Returns true if:
     * - Distance is between 1-3 (close enough to be typosquatting)
     * - Strings are similar enough in length (within 50%)
     *
     * @param domain1 First domain
     * @param domain2 Second domain
     * @return true if likely typosquatting, false otherwise
     */
    fun isTyposquatting(domain1: String, domain2: String): Boolean {
        val distance = levenshteinDistance(domain1.lowercase(), domain2.lowercase())

        // Must be 1-3 character difference
        if (distance !in 1..3) return false

        // Check length similarity (prevent matching very different length strings)
        val maxLen = maxOf(domain1.length, domain2.length)
        val minLen = minOf(domain1.length, domain2.length)

        // Require at least 50% length similarity
        return minLen.toDouble() / maxLen >= 0.5
    }
}
