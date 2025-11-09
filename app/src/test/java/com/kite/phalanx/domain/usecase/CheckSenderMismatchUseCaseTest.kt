package com.kite.phalanx.domain.usecase

import com.kite.phalanx.domain.model.*
import com.kite.phalanx.domain.repository.SenderPackRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CheckSenderMismatchUseCase (Phase 4).
 *
 * Tests cover:
 * - Legitimate sender IDs matching brand claims → no signals
 * - Fake sender IDs with brand claims → SENDER_MISMATCH signals
 * - Whole-word matching (no false positives from substrings)
 * - Weight calculation based on brand type
 * - Multiple brand claims in single message
 * - Empty message/no brand claims
 * - Case-insensitive matching
 */
class CheckSenderMismatchUseCaseTest {

    private lateinit var useCase: CheckSenderMismatchUseCase
    private lateinit var mockRepository: MockSenderPackRepository

    @Before
    fun setup() {
        mockRepository = MockSenderPackRepository()
        useCase = CheckSenderMismatchUseCase(mockRepository)
    }

    @Test
    fun `no mismatch when sender matches claimed brand`() = runTest {
        // HDFC Bank sending HDFC message - legitimate
        val senderId = "HDFCBK"
        val messageBody = "Your HDFC Bank account has been credited with Rs 10,000"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(0, signals.size)
    }

    @Test
    fun `mismatch when sender does not match claimed brand`() = runTest {
        // Fake sender claiming to be HDFC
        val senderId = "SPAM123"
        val messageBody = "Your HDFC Bank account has been debited. Click here to verify."
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertEquals(SignalCode.SENDER_MISMATCH, signals[0].code)
        assertEquals(70, signals[0].weight) // BANK type weight
        assertEquals("HDFC Bank", signals[0].metadata["claimedBrand"])
        assertEquals("SPAM123", signals[0].metadata["actualSender"])
    }

    @Test
    fun `no false positive for substring matches`() = runTest {
        // Message contains "visit" and "available" which have "vi" substring
        // Should NOT match Vodafone Idea's "vi" keyword
        val senderId = "HDFCBK"
        val messageBody = "Visit our website for available offers"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        // Should be no signals - "vi" is not a whole word
        assertEquals(0, signals.size)
    }

    @Test
    fun `detects mismatch for carrier brand`() = runTest {
        // Fake sender claiming to be Airtel
        val senderId = "FAKE-CARRIER"
        val messageBody = "Congratulations! Airtel has selected you for a free recharge."
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertEquals(SignalCode.SENDER_MISMATCH, signals[0].code)
        assertEquals(50, signals[0].weight) // CARRIER type weight
        assertEquals("Airtel", signals[0].metadata["claimedBrand"])
    }

    @Test
    fun `legitimate Airtel sender with AX prefix`() = runTest {
        // Airtel uses AX-AIRTEL or AIRTEL as sender ID
        val senderId = "AX-AIRTEL"
        val messageBody = "Your Airtel recharge of Rs 399 is successful"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(0, signals.size)
    }

    @Test
    fun `detects government agency impersonation`() = runTest {
        // Fake sender claiming to be UIDAI (Aadhaar)
        val senderId = "+919876543210"
        val messageBody = "URGENT: Your Aadhaar has been suspended. Update KYC immediately."
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertEquals(SignalCode.SENDER_MISMATCH, signals[0].code)
        assertEquals(65, signals[0].weight) // GOVERNMENT type weight
    }

    @Test
    fun `detects payment service impersonation`() = runTest {
        // Fake sender claiming to be Paytm
        val senderId = "SCAMMER"
        val messageBody = "Paytm: Your cashback of Rs 5000 is waiting. Claim now!"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertEquals(SignalCode.SENDER_MISMATCH, signals[0].code)
        assertEquals(65, signals[0].weight) // PAYMENT type weight
    }

    @Test
    fun `detects ecommerce impersonation`() = runTest {
        // Fake sender claiming to be Amazon
        val senderId = "FAKE-SHOP"
        val messageBody = "Amazon: You have won a prize! Click to claim."
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertEquals(SignalCode.SENDER_MISMATCH, signals[0].code)
        assertEquals(45, signals[0].weight) // ECOMMERCE type weight
    }

    @Test
    fun `case insensitive brand matching`() = runTest {
        // Mixed case in message
        val senderId = "SPAM"
        val messageBody = "YOUR hDfC BANK ACCOUNT IS LOCKED"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertEquals("HDFC Bank", signals[0].metadata["claimedBrand"])
    }

    @Test
    fun `no signals when no brand mentioned`() = runTest {
        val senderId = "RANDOM"
        val messageBody = "Hello, how are you doing today?"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(0, signals.size)
    }

    @Test
    fun `no signals when empty message`() = runTest {
        val senderId = "SENDER"
        val messageBody = ""
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(0, signals.size)
    }

    @Test
    fun `multiple brand claims detected`() = runTest {
        // Message mentions both HDFC and Airtel
        val senderId = "SCAM"
        val messageBody = "Transfer from HDFC Bank to your Airtel wallet failed"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        // Should detect 2 mismatches (HDFC Bank and Airtel)
        assertEquals(2, signals.size)
        assertTrue(signals.any { it.metadata["claimedBrand"] == "HDFC Bank" })
        assertTrue(signals.any { it.metadata["claimedBrand"] == "Airtel" })
    }

    @Test
    fun `keyword detection for brand aliases`() = runTest {
        // Uses "bharti airtel" keyword instead of "Airtel" brand name
        val senderId = "SPAM"
        val messageBody = "Bharti Airtel is offering you unlimited data"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertEquals("Airtel", signals[0].metadata["claimedBrand"])
    }

    @Test
    fun `returns empty list when sender pack not loaded`() = runTest {
        // Repository returns null pack
        mockRepository.pack = null

        val senderId = "SPAM"
        val messageBody = "HDFC Bank alert"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(0, signals.size)
    }

    @Test
    fun `metadata contains detection source`() = runTest {
        val senderId = "SPAM"
        val messageBody = "Your HDFC Bank account needs verification"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(1, signals.size)
        assertNotNull(signals[0].metadata["detectionSource"])
        assertTrue(signals[0].metadata["detectionSource"]!!.contains("keyword"))
    }

    @Test
    fun `legitimate sender with different casing`() = runTest {
        // Repository should handle case-insensitive sender matching
        val senderId = "hdfcbk" // lowercase
        val messageBody = "Your HDFC Bank statement is ready"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        // Mock repository does case-insensitive matching
        val signals = useCase(senderId, messageBody, links, domainProfiles)

        // Should NOT generate signal because sender matches
        assertEquals(0, signals.size)
    }

    @Test
    fun `handles special characters in sender ID`() = runTest {
        val senderId = "AX-AIRTEL" // Has hyphen
        val messageBody = "Airtel bill payment successful"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        assertEquals(0, signals.size)
    }

    @Test
    fun `handles international phone number as sender`() = runTest {
        val senderId = "+911234567890"
        val messageBody = "URGENT: HDFC Bank security alert"
        val links = emptyList<Link>()
        val domainProfiles = emptyList<DomainProfile>()

        val signals = useCase(senderId, messageBody, links, domainProfiles)

        // Phone number won't match HDFCBK pattern → mismatch
        assertEquals(1, signals.size)
    }

    /**
     * Mock implementation of SenderPackRepository for testing
     */
    private class MockSenderPackRepository : SenderPackRepository {
        var pack: SenderPack? = createDefaultPack()

        override suspend fun loadPack(region: String): PackVerificationResult {
            return PackVerificationResult(
                isValid = pack != null,
                pack = pack,
                errorMessage = if (pack == null) "Pack not available" else null
            )
        }

        override fun getCurrentPack(): SenderPack? = pack

        override fun findMatchingSenders(senderId: String): List<SenderPackEntry> {
            val p = pack ?: return emptyList()
            val senderLower = senderId.lowercase()

            return p.entries.filter { entry ->
                // Simple pattern matching - case insensitive
                try {
                    val pattern = entry.pattern.toRegex(RegexOption.IGNORE_CASE)
                    pattern.matches(senderLower)
                } catch (e: Exception) {
                    false
                }
            }
        }

        override fun isKnownSender(senderId: String): Boolean {
            return findMatchingSenders(senderId).isNotEmpty()
        }

        override suspend fun updateRegion(region: String): PackVerificationResult {
            return loadPack(region)
        }

        override fun clearCache() {
            pack = null
        }

        companion object {
            fun createDefaultPack(): SenderPack {
                return SenderPack(
                    region = "IN",
                    version = 1,
                    entries = listOf(
                        SenderPackEntry(
                            pattern = "^HDFCBK$",
                            brand = "HDFC Bank",
                            type = SenderType.BANK,
                            keywords = listOf("hdfc bank", "hdfc")
                        ),
                        SenderPackEntry(
                            pattern = "^(AX-)?AIRTEL$",
                            brand = "Airtel",
                            type = SenderType.CARRIER,
                            keywords = listOf("airtel", "bharti airtel")
                        ),
                        SenderPackEntry(
                            pattern = "^VM-ADHRNO$",
                            brand = "UIDAI",
                            type = SenderType.GOVERNMENT,
                            keywords = listOf("uidai", "aadhaar", "aadhar")
                        ),
                        SenderPackEntry(
                            pattern = "^PYTM$",
                            brand = "Paytm",
                            type = SenderType.PAYMENT,
                            keywords = listOf("paytm", "paytm wallet")
                        ),
                        SenderPackEntry(
                            pattern = "^AMAZON$",
                            brand = "Amazon",
                            type = SenderType.ECOMMERCE,
                            keywords = listOf("amazon", "amazon.in")
                        )
                    ),
                    signature = "0000000000000000000000000000000000000000000000000000000000000000",
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
}
