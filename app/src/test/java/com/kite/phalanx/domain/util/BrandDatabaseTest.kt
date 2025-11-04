package com.kite.phalanx.domain.util

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for BrandDatabase (Stage 1B).
 *
 * Tests brand detection and official domain validation.
 */
class BrandDatabaseTest {

    private lateinit var brandDatabase: BrandDatabase

    @Before
    fun setUp() {
        brandDatabase = BrandDatabase()
    }

    @Test
    fun `findBrandByDomain - detects PayPal in domain`() {
        val brand = brandDatabase.findBrandByDomain("paypal-verify.tk")
        assertNotNull(brand)
        assertEquals("PayPal", brand?.name)
    }

    @Test
    fun `findBrandByDomain - detects Amazon in domain`() {
        val brand = brandDatabase.findBrandByDomain("amazon-security.xyz")
        assertNotNull(brand)
        assertEquals("Amazon", brand?.name)
    }

    @Test
    fun `findBrandByDomain - detects Chase in domain`() {
        val brand = brandDatabase.findBrandByDomain("chase-verify.com")
        assertNotNull(brand)
        assertEquals("Chase Bank", brand?.name)
    }

    @Test
    fun `findBrandByDomain - returns null for unknown brand`() {
        val brand = brandDatabase.findBrandByDomain("example.com")
        assertNull(brand)
    }

    @Test
    fun `findBrandByDomain - is case insensitive`() {
        val brand1 = brandDatabase.findBrandByDomain("PAYPAL.com")
        val brand2 = brandDatabase.findBrandByDomain("paypal.com")
        val brand3 = brandDatabase.findBrandByDomain("PayPal.com")

        assertNotNull(brand1)
        assertNotNull(brand2)
        assertNotNull(brand3)
        assertEquals("PayPal", brand1?.name)
        assertEquals("PayPal", brand2?.name)
        assertEquals("PayPal", brand3?.name)
    }

    @Test
    fun `findBrandByDomain - detects multiple brands correctly`() {
        val paypal = brandDatabase.findBrandByDomain("paypal-security.tk")
        val google = brandDatabase.findBrandByDomain("google-verify.com")
        val apple = brandDatabase.findBrandByDomain("apple-account.xyz")

        assertEquals("PayPal", paypal?.name)
        assertEquals("Google", google?.name)
        assertEquals("Apple", apple?.name)
    }

    @Test
    fun `isOfficialDomain - recognizes PayPal official domain`() {
        val brand = brandDatabase.findBrandByDomain("paypal.com")!!
        assertTrue(brandDatabase.isOfficialDomain("paypal.com", brand))
    }

    @Test
    fun `isOfficialDomain - recognizes Amazon official domains`() {
        val brand = brandDatabase.findBrandByDomain("amazon.com")!!
        assertTrue(brandDatabase.isOfficialDomain("amazon.com", brand))
        assertTrue(brandDatabase.isOfficialDomain("amazon.co.uk", brand))
        assertTrue(brandDatabase.isOfficialDomain("amazon.in", brand))
    }

    @Test
    fun `isOfficialDomain - recognizes subdomains as official`() {
        val brand = brandDatabase.findBrandByDomain("paypal.com")!!
        assertTrue(brandDatabase.isOfficialDomain("www.paypal.com", brand))
        assertTrue(brandDatabase.isOfficialDomain("secure.paypal.com", brand))
    }

    @Test
    fun `isOfficialDomain - rejects wrong TLD`() {
        val brand = brandDatabase.findBrandByDomain("paypal-verify.tk")!!
        assertFalse(brandDatabase.isOfficialDomain("paypal.tk", brand))
        assertFalse(brandDatabase.isOfficialDomain("paypal.xyz", brand))
    }

    @Test
    fun `isOfficialDomain - rejects typosquatted domains`() {
        val brand = brandDatabase.findBrandByDomain("paypa1.com")!!
        assertFalse(brandDatabase.isOfficialDomain("paypa1.com", brand))
    }

    @Test
    fun `isOfficialDomain - is case insensitive`() {
        val brand = brandDatabase.findBrandByDomain("PayPal.com")!!
        assertTrue(brandDatabase.isOfficialDomain("PAYPAL.COM", brand))
        assertTrue(brandDatabase.isOfficialDomain("PayPal.com", brand))
        assertTrue(brandDatabase.isOfficialDomain("paypal.com", brand))
    }

    @Test
    fun `getAllBrands - returns non-empty list`() {
        val brands = brandDatabase.getAllBrands()
        assertTrue(brands.isNotEmpty())
        assertTrue(brands.size >= 50) // Should have at least 50 brands
    }

    @Test
    fun `getAllBrands - contains major financial brands`() {
        val brands = brandDatabase.getAllBrands()
        val brandNames = brands.map { it.name }

        assertTrue("PayPal" in brandNames)
        assertTrue("Chase Bank" in brandNames)
        assertTrue("Bank of America" in brandNames)
        assertTrue("Wells Fargo" in brandNames)
    }

    @Test
    fun `getAllBrands - contains major tech brands`() {
        val brands = brandDatabase.getAllBrands()
        val brandNames = brands.map { it.name }

        assertTrue("Apple" in brandNames)
        assertTrue("Google" in brandNames)
        assertTrue("Microsoft" in brandNames)
        assertTrue("Amazon" in brandNames)
    }

    @Test
    fun `brand profiles have valid data`() {
        val brands = brandDatabase.getAllBrands()

        brands.forEach { brand ->
            // Each brand should have a name
            assertFalse(brand.name.isBlank())

            // Each brand should have at least one official domain
            assertTrue(brand.officialDomains.isNotEmpty())

            // Each brand should have at least one keyword
            assertTrue(brand.keywords.isNotEmpty())
        }
    }

    @Test
    fun `keywords match brand name`() {
        val paypal = brandDatabase.findBrandByDomain("paypal.com")!!
        assertTrue(paypal.keywords.any { it.contains("paypal", ignoreCase = true) })

        val amazon = brandDatabase.findBrandByDomain("amazon.com")!!
        assertTrue(amazon.keywords.any { it.contains("amazon", ignoreCase = true) })
    }
}
