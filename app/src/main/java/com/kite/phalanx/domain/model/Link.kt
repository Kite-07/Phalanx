package com.kite.phalanx.domain.model

/**
 * Represents a link extracted from an SMS message.
 *
 * @property original The original URL as it appears in the message
 * @property normalized The normalized version of the URL
 * @property finalUrl The final URL after following redirects (null if not yet expanded)
 * @property host The hostname from the URL
 * @property registeredDomain The registered domain (using PSL)
 * @property scheme The URL scheme (http, https, etc.)
 * @property port The port number (if specified)
 * @property path The URL path
 * @property params The query parameters
 * @property hasUserInfo Whether the URL contains user:pass@ component
 */
data class Link(
    val original: String,
    val normalized: String,
    val finalUrl: String? = null,
    val host: String,
    val registeredDomain: String,
    val scheme: String,
    val port: Int? = null,
    val path: String = "",
    val params: Map<String, String> = emptyMap(),
    val hasUserInfo: Boolean = false
)
