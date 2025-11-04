package com.kite.phalanx.domain.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for StringUtils (Stage 1B).
 *
 * Tests Levenshtein distance calculation and typosquatting detection.
 */
class StringUtilsTest {

    @Test
    fun `levenshteinDistance - identical strings return 0`() {
        assertEquals(0, StringUtils.levenshteinDistance("paypal.com", "paypal.com"))
        assertEquals(0, StringUtils.levenshteinDistance("", ""))
        assertEquals(0, StringUtils.levenshteinDistance("test", "test"))
    }

    @Test
    fun `levenshteinDistance - empty string cases`() {
        assertEquals(5, StringUtils.levenshteinDistance("", "hello"))
        assertEquals(5, StringUtils.levenshteinDistance("hello", ""))
    }

    @Test
    fun `levenshteinDistance - single character difference`() {
        // paypal.com vs paypa1.com (l -> 1)
        assertEquals(1, StringUtils.levenshteinDistance("paypal.com", "paypa1.com"))

        // google.com vs goog1e.com (l -> 1)
        assertEquals(1, StringUtils.levenshteinDistance("google.com", "goog1e.com"))

        // amazon.com vs amaz0n.com (o -> 0)
        assertEquals(1, StringUtils.levenshteinDistance("amazon.com", "amaz0n.com"))
    }

    @Test
    fun `levenshteinDistance - two character difference`() {
        // paypal.com -> paypa1.co is 2 edits (l->1, delete m)
        assertEquals(2, StringUtils.levenshteinDistance("paypal.com", "paypa1.co"))
        // test -> best is 1 edit (t->b)
        assertEquals(1, StringUtils.levenshteinDistance("test", "best"))
        // test -> text is 1 edit (insert x)
        assertEquals(1, StringUtils.levenshteinDistance("test", "text"))
    }

    @Test
    fun `levenshteinDistance - three character difference`() {
        assertEquals(3, StringUtils.levenshteinDistance("kitten", "sitting"))
    }

    @Test
    fun `levenshteinDistance - completely different strings`() {
        assertTrue(StringUtils.levenshteinDistance("abc", "xyz") > 0)
        assertEquals(3, StringUtils.levenshteinDistance("abc", "xyz"))
    }

    @Test
    fun `levenshteinDistance - case sensitive`() {
        // Should be case sensitive by default
        assertTrue(StringUtils.levenshteinDistance("PayPal", "paypal") > 0)
    }

    @Test
    fun `isTyposquatting - detects single character typo`() {
        assertTrue(StringUtils.isTyposquatting("paypal.com", "paypa1.com"))
        assertTrue(StringUtils.isTyposquatting("google.com", "goog1e.com"))
        assertTrue(StringUtils.isTyposquatting("amazon.com", "amaz0n.com"))
    }

    @Test
    fun `isTyposquatting - detects two character typo`() {
        assertTrue(StringUtils.isTyposquatting("chase.com", "chass.com"))
        assertTrue(StringUtils.isTyposquatting("paypal.com", "paypa1.co"))
    }

    @Test
    fun `isTyposquatting - detects three character typo`() {
        assertTrue(StringUtils.isTyposquatting("bankofamerica.com", "bankofameric4.com"))
    }

    @Test
    fun `isTyposquatting - rejects identical domains`() {
        assertFalse(StringUtils.isTyposquatting("paypal.com", "paypal.com"))
        assertFalse(StringUtils.isTyposquatting("google.com", "google.com"))
    }

    @Test
    fun `isTyposquatting - rejects too many differences`() {
        // More than 3 character difference
        assertFalse(StringUtils.isTyposquatting("paypal.com", "aaaaaaaaaa.com"))
        assertFalse(StringUtils.isTyposquatting("example.com", "totally-different.com"))
    }

    @Test
    fun `isTyposquatting - rejects very different length strings`() {
        // Length difference too large (less than 50% similarity)
        assertFalse(StringUtils.isTyposquatting("paypalverification.com", "pay.com"))
        assertFalse(StringUtils.isTyposquatting("amazonsecurity.com", "am.com"))
    }

    @Test
    fun `isTyposquatting - is case insensitive`() {
        assertTrue(StringUtils.isTyposquatting("PayPal.com", "paypa1.com"))
        assertTrue(StringUtils.isTyposquatting("GOOGLE.COM", "goog1e.com"))
    }

    @Test
    fun `isTyposquatting - handles edge cases`() {
        assertFalse(StringUtils.isTyposquatting("", ""))
        assertFalse(StringUtils.isTyposquatting("a", ""))
        assertFalse(StringUtils.isTyposquatting("", "a"))
    }

    @Test
    fun `isTyposquatting - real world examples`() {
        // Real typosquatting attacks
        assertTrue(StringUtils.isTyposquatting("netflix.com", "netfl1x.com"))
        assertTrue(StringUtils.isTyposquatting("microsoft.com", "micr0soft.com"))
        assertTrue(StringUtils.isTyposquatting("apple.com", "app1e.com"))
        assertTrue(StringUtils.isTyposquatting("facebook.com", "faceb00k.com"))

        // Not typosquatting (legitimate variations)
        assertFalse(StringUtils.isTyposquatting("amazon.com", "amazon-aws.com"))
        assertFalse(StringUtils.isTyposquatting("google.com", "google-analytics.com"))
    }
}
