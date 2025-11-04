package com.kite.phalanx.data.util

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parser for the Public Suffix List (PSL).
 * Extracts registered domains from hostnames using Mozilla's PSL.
 *
 * The PSL is a list of rules about which domain name registrations are controlled by
 * registrars (e.g., .com, .co.uk, .pvt.k12.wy.us).
 */
class PublicSuffixListParser(context: Context) {

    private val rules = mutableSetOf<String>()
    private val exceptions = mutableSetOf<String>()
    private val wildcards = mutableSetOf<String>()

    init {
        loadPslFromAssets(context)
    }

    /**
     * Load and parse the PSL file from assets.
     */
    private fun loadPslFromAssets(context: Context) {
        context.assets.open("public_suffix_list.dat").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("//") }
                    .forEach { line ->
                        when {
                            line.startsWith("!") -> {
                                // Exception rule
                                exceptions.add(line.substring(1))
                            }
                            line.startsWith("*.") -> {
                                // Wildcard rule
                                wildcards.add(line.substring(2))
                            }
                            else -> {
                                // Normal rule
                                rules.add(line)
                            }
                        }
                    }
            }
        }
    }

    /**
     * Extract the registered domain from a hostname.
     *
     * Example:
     * - "www.example.com" -> "example.com"
     * - "blog.example.co.uk" -> "example.co.uk"
     * - "192.168.1.1" -> "192.168.1.1" (IP addresses return as-is)
     *
     * @param host The hostname to analyze
     * @return The registered domain, or the original host if no match found
     */
    fun getRegisteredDomain(host: String): String {
        if (host.isEmpty()) return host

        // Handle IP addresses - return as-is
        if (isIpAddress(host)) {
            return host
        }

        val parts = host.lowercase().split(".")
        if (parts.size <= 1) return host

        // Check for exception rules first (highest priority)
        for (i in parts.indices) {
            val candidate = parts.subList(i, parts.size).joinToString(".")
            if (exceptions.contains(candidate)) {
                // Exception rule: the domain is one level up from the exception
                return if (i > 0) {
                    parts.subList(i - 1, parts.size).joinToString(".")
                } else {
                    host
                }
            }
        }

        // Check for wildcard rules
        for (i in 1 until parts.size) {
            val candidate = parts.subList(i, parts.size).joinToString(".")
            if (wildcards.contains(candidate)) {
                // Wildcard rule: the domain is one level up from the match
                return if (i > 1) {
                    parts.subList(i - 2, parts.size).joinToString(".")
                } else {
                    host
                }
            }
        }

        // Check for exact rules
        for (i in parts.indices) {
            val candidate = parts.subList(i, parts.size).joinToString(".")
            if (rules.contains(candidate)) {
                // Exact rule: the domain is one level up from the match
                return if (i > 0) {
                    parts.subList(i - 1, parts.size).joinToString(".")
                } else {
                    host
                }
            }
        }

        // No match found - assume it's a standard TLD and return domain.tld
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            host
        }
    }

    /**
     * Check if a string is an IP address (IPv4 or IPv6).
     */
    private fun isIpAddress(host: String): Boolean {
        // Simple IPv4 check
        if (host.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""))) {
            return true
        }
        // Simple IPv6 check (contains colons)
        if (host.contains(':')) {
            return true
        }
        return false
    }
}
