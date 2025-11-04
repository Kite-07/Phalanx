package com.kite.phalanx.domain.util

import com.kite.phalanx.domain.model.TldRiskLevel
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TldRiskScorer (Stage 1B).
 *
 * Tests TLD risk level evaluation.
 */
class TldRiskScorerTest {

    private lateinit var tldRiskScorer: TldRiskScorer

    @Before
    fun setUp() {
        tldRiskScorer = TldRiskScorer()
    }

    @Test
    fun `evaluateTldRisk - CRITICAL level for free TLDs`() {
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("scam.tk"))
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("phishing.ml"))
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("fake.ga"))
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("fraud.cf"))
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("spam.gq"))
    }

    @Test
    fun `evaluateTldRisk - HIGH level for cheap scam TLDs`() {
        assertEquals(TldRiskLevel.HIGH, tldRiskScorer.evaluateTldRisk("scam.xyz"))
        assertEquals(TldRiskLevel.HIGH, tldRiskScorer.evaluateTldRisk("phishing.top"))
        assertEquals(TldRiskLevel.HIGH, tldRiskScorer.evaluateTldRisk("fake.club"))
        assertEquals(TldRiskLevel.HIGH, tldRiskScorer.evaluateTldRisk("fraud.online"))
        assertEquals(TldRiskLevel.HIGH, tldRiskScorer.evaluateTldRisk("spam.site"))
    }

    @Test
    fun `evaluateTldRisk - MEDIUM level for sometimes suspicious TLDs`() {
        assertEquals(TldRiskLevel.MEDIUM, tldRiskScorer.evaluateTldRisk("example.info"))
        assertEquals(TldRiskLevel.MEDIUM, tldRiskScorer.evaluateTldRisk("example.biz"))
    }

    @Test
    fun `evaluateTldRisk - LOW level for trusted TLDs`() {
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("google.com"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.org"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.net"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("whitehouse.gov"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("mit.edu"))
    }

    @Test
    fun `evaluateTldRisk - LOW level for country code TLDs`() {
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.us"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.uk"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.ca"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.au"))
    }

    @Test
    fun `evaluateTldRisk - LOW level for new trusted gTLDs`() {
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.app"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.dev"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.io"))
    }

    @Test
    fun `evaluateTldRisk - defaults to LOW for unknown TLDs`() {
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.unknown"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("example.xyz123"))
    }

    @Test
    fun `evaluateTldRisk - is case insensitive`() {
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("scam.TK"))
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("SCAM.tk"))
        assertEquals(TldRiskLevel.HIGH, tldRiskScorer.evaluateTldRisk("scam.XYZ"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("google.COM"))
    }

    @Test
    fun `evaluateTldRisk - handles subdomains correctly`() {
        // Should extract .tk from www.scam.tk
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("www.scam.tk"))
        assertEquals(TldRiskLevel.CRITICAL, tldRiskScorer.evaluateTldRisk("secure.phishing.ml"))
        assertEquals(TldRiskLevel.LOW, tldRiskScorer.evaluateTldRisk("www.google.com"))
    }

    @Test
    fun `isHighRiskTld - returns true for CRITICAL level`() {
        assertTrue(tldRiskScorer.isHighRiskTld("scam.tk"))
        assertTrue(tldRiskScorer.isHighRiskTld("phishing.ml"))
        assertTrue(tldRiskScorer.isHighRiskTld("fake.ga"))
    }

    @Test
    fun `isHighRiskTld - returns true for HIGH level`() {
        assertTrue(tldRiskScorer.isHighRiskTld("scam.xyz"))
        assertTrue(tldRiskScorer.isHighRiskTld("phishing.top"))
        assertTrue(tldRiskScorer.isHighRiskTld("fake.club"))
    }

    @Test
    fun `isHighRiskTld - returns false for MEDIUM level`() {
        assertFalse(tldRiskScorer.isHighRiskTld("example.info"))
        assertFalse(tldRiskScorer.isHighRiskTld("example.biz"))
    }

    @Test
    fun `isHighRiskTld - returns false for LOW level`() {
        assertFalse(tldRiskScorer.isHighRiskTld("google.com"))
        assertFalse(tldRiskScorer.isHighRiskTld("example.org"))
        assertFalse(tldRiskScorer.isHighRiskTld("whitehouse.gov"))
    }

    @Test
    fun `TldRiskLevel - has correct weights`() {
        assertEquals(30, TldRiskLevel.CRITICAL.weight)
        assertEquals(20, TldRiskLevel.HIGH.weight)
        assertEquals(10, TldRiskLevel.MEDIUM.weight)
        assertEquals(0, TldRiskLevel.LOW.weight)
    }

    @Test
    fun `real world phishing domains - high risk`() {
        // Real-world phishing examples
        assertTrue(tldRiskScorer.isHighRiskTld("paypal-verify.tk"))
        assertTrue(tldRiskScorer.isHighRiskTld("amazon-security.ml"))
        assertTrue(tldRiskScorer.isHighRiskTld("chase-account.xyz"))
        assertTrue(tldRiskScorer.isHighRiskTld("apple-id.top"))
    }

    @Test
    fun `legitimate domains - low risk`() {
        // Legitimate domains should be low risk
        assertFalse(tldRiskScorer.isHighRiskTld("paypal.com"))
        assertFalse(tldRiskScorer.isHighRiskTld("amazon.com"))
        assertFalse(tldRiskScorer.isHighRiskTld("chase.com"))
        assertFalse(tldRiskScorer.isHighRiskTld("apple.com"))
    }
}
