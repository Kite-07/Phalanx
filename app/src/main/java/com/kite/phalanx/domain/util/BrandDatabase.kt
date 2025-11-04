package com.kite.phalanx.domain.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database of top brands and their official domains for brand impersonation detection.
 *
 * Stage 1B Enhancement: Brand Impersonation Detection
 * Detects typosquatting and wrong TLD attacks.
 */
@Singleton
class BrandDatabase @Inject constructor() {

    /**
     * Brand profile with official domains and keywords.
     *
     * @property name Display name of the brand
     * @property officialDomains Set of verified official domains
     * @property keywords Set of keywords to detect in suspicious domains
     */
    data class BrandProfile(
        val name: String,
        val officialDomains: Set<String>,
        val keywords: Set<String>
    )

    /**
     * Top 100 brands database for phishing detection.
     * Focus on brands commonly targeted in SMS phishing attacks.
     */
    private val brands = listOf(
        // Financial Services (most targeted)
        BrandProfile("PayPal", setOf("paypal.com"), setOf("paypal", "paypai", "paypa1", "payp4l", "p4ypal")),
        BrandProfile("Chase Bank", setOf("chase.com"), setOf("chase", "chas3")),
        BrandProfile("Bank of America", setOf("bankofamerica.com", "bofa.com"), setOf("bankofamerica", "bofa", "bankofamer1ca")),
        BrandProfile("Wells Fargo", setOf("wellsfargo.com"), setOf("wellsfargo", "wells", "we11s")),
        BrandProfile("Capital One", setOf("capitalone.com"), setOf("capitalone", "capital1", "capita1one")),
        BrandProfile("Citibank", setOf("citi.com", "citibank.com"), setOf("citi", "citibank")),
        BrandProfile("American Express", setOf("americanexpress.com", "amex.com"), setOf("amex", "americanexpress")),
        BrandProfile("Discover", setOf("discover.com"), setOf("discover")),
        BrandProfile("HSBC", setOf("hsbc.com"), setOf("hsbc")),
        BrandProfile("Barclays", setOf("barclays.com", "barclays.co.uk"), setOf("barclays")),
        BrandProfile("US Bank", setOf("usbank.com"), setOf("usbank")),
        BrandProfile("TD Bank", setOf("tdbank.com"), setOf("tdbank")),
        BrandProfile("PNC Bank", setOf("pnc.com"), setOf("pnc")),
        BrandProfile("Navy Federal", setOf("navyfederal.org"), setOf("navyfederal")),
        BrandProfile("USAA", setOf("usaa.com"), setOf("usaa")),

        // E-commerce & Retail
        BrandProfile("Amazon", setOf("amazon.com", "amazon.co.uk", "amazon.de", "amazon.in", "amazon.ca", "amazon.fr"), setOf("amazon", "amzn", "amaz0n", "amaz00n", "am4zon")),
        BrandProfile("eBay", setOf("ebay.com", "ebay.co.uk"), setOf("ebay", "3bay")),
        BrandProfile("Walmart", setOf("walmart.com"), setOf("walmart")),
        BrandProfile("Target", setOf("target.com"), setOf("target")),
        BrandProfile("Best Buy", setOf("bestbuy.com"), setOf("bestbuy")),
        BrandProfile("Etsy", setOf("etsy.com"), setOf("etsy")),
        BrandProfile("Alibaba", setOf("alibaba.com"), setOf("alibaba")),
        BrandProfile("AliExpress", setOf("aliexpress.com"), setOf("aliexpress")),

        // Technology Companies
        BrandProfile("Apple", setOf("apple.com", "icloud.com"), setOf("apple", "icloud", "app1e", "appl3", "app13")),
        BrandProfile("Microsoft", setOf("microsoft.com", "outlook.com", "live.com", "hotmail.com"), setOf("microsoft", "outlook", "hotmail", "micr0soft")),
        BrandProfile("Google", setOf("google.com", "gmail.com", "youtube.com"), setOf("google", "gmail", "goog1e", "g00gle", "g0ogle", "go0gle")),
        BrandProfile("Facebook", setOf("facebook.com", "fb.com", "meta.com"), setOf("facebook", "fb", "faceb00k", "facebo0k")),
        BrandProfile("Instagram", setOf("instagram.com"), setOf("instagram", "insta")),
        BrandProfile("WhatsApp", setOf("whatsapp.com"), setOf("whatsapp")),
        BrandProfile("LinkedIn", setOf("linkedin.com"), setOf("linkedin")),
        BrandProfile("Twitter", setOf("twitter.com", "x.com"), setOf("twitter")),
        BrandProfile("Netflix", setOf("netflix.com"), setOf("netflix")),
        BrandProfile("Spotify", setOf("spotify.com"), setOf("spotify")),
        BrandProfile("Adobe", setOf("adobe.com"), setOf("adobe")),
        BrandProfile("Dropbox", setOf("dropbox.com"), setOf("dropbox")),

        // Crypto & Finance Tech
        BrandProfile("Coinbase", setOf("coinbase.com"), setOf("coinbase")),
        BrandProfile("Binance", setOf("binance.com"), setOf("binance")),
        BrandProfile("Kraken", setOf("kraken.com"), setOf("kraken")),
        BrandProfile("Venmo", setOf("venmo.com"), setOf("venmo")),
        BrandProfile("Cash App", setOf("cash.app"), setOf("cashapp")),
        BrandProfile("Zelle", setOf("zellepay.com"), setOf("zelle")),

        // Shipping & Logistics
        BrandProfile("FedEx", setOf("fedex.com"), setOf("fedex")),
        BrandProfile("UPS", setOf("ups.com"), setOf("ups")),
        BrandProfile("USPS", setOf("usps.com"), setOf("usps")),
        BrandProfile("DHL", setOf("dhl.com"), setOf("dhl")),

        // Government & Services
        BrandProfile("IRS", setOf("irs.gov"), setOf("irs")),
        BrandProfile("Social Security", setOf("ssa.gov"), setOf("socialsecurity", "ssa")),
        BrandProfile("USPS Official", setOf("usps.gov"), setOf("usps")),

        // Telecom
        BrandProfile("AT&T", setOf("att.com"), setOf("att")),
        BrandProfile("Verizon", setOf("verizon.com"), setOf("verizon")),
        BrandProfile("T-Mobile", setOf("t-mobile.com"), setOf("tmobile")),
        BrandProfile("Sprint", setOf("sprint.com"), setOf("sprint")),

        // Insurance
        BrandProfile("Geico", setOf("geico.com"), setOf("geico")),
        BrandProfile("State Farm", setOf("statefarm.com"), setOf("statefarm")),
        BrandProfile("Progressive", setOf("progressive.com"), setOf("progressive")),

        // Healthcare
        BrandProfile("CVS", setOf("cvs.com"), setOf("cvs")),
        BrandProfile("Walgreens", setOf("walgreens.com"), setOf("walgreens")),

        // Travel
        BrandProfile("Booking.com", setOf("booking.com"), setOf("booking")),
        BrandProfile("Expedia", setOf("expedia.com"), setOf("expedia")),
        BrandProfile("Airbnb", setOf("airbnb.com"), setOf("airbnb")),

        // Other Major Brands
        BrandProfile("Costco", setOf("costco.com"), setOf("costco")),
        BrandProfile("Home Depot", setOf("homedepot.com"), setOf("homedepot")),
        BrandProfile("Lowe's", setOf("lowes.com"), setOf("lowes")),
        BrandProfile("Macy's", setOf("macys.com"), setOf("macys"))
    )

    /**
     * Get brand profile if domain matches a known brand keyword.
     *
     * @param domain The domain to check (e.g., "paypal-verify.tk")
     * @return BrandProfile if brand keyword detected, null otherwise
     */
    fun findBrandByDomain(domain: String): BrandProfile? {
        val domainLower = domain.lowercase()

        return brands.find { brand ->
            // Check if domain contains any brand keyword
            brand.keywords.any { keyword ->
                domainLower.contains(keyword)
            }
        }
    }

    /**
     * Check if domain is an official domain for the given brand.
     *
     * @param domain The domain to check
     * @param brand The brand profile
     * @return true if domain is official, false otherwise
     */
    fun isOfficialDomain(domain: String, brand: BrandProfile): Boolean {
        val domainLower = domain.lowercase()
        return brand.officialDomains.any { official ->
            domainLower == official || domainLower.endsWith(".$official")
        }
    }

    /**
     * Get all brand profiles for iteration.
     */
    fun getAllBrands(): List<BrandProfile> = brands
}
