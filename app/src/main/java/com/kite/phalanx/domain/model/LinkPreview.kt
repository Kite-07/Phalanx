package com.kite.phalanx.domain.model

/**
 * Safe preview of a link fetched for user context.
 *
 * Per PRD Phase 5:
 * - HTTP GET with partial reading (first 20KB only)
 * - Parse HTML for title and favicon only (no JavaScript execution)
 * - Block data: URLs and malicious redirects
 * - Provides user-friendly context without security risks
 *
 * @property url The URL this preview is for
 * @property title Page title extracted from <title> tag (null if not found)
 * @property faviconData Favicon bytes (null if not found, max 10KB)
 * @property fetchedAt When the preview was fetched
 * @property error Error message if preview fetch failed (null if successful)
 */
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val faviconData: ByteArray? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
    val error: String? = null
) {
    // ByteArray doesn't implement equals/hashCode by default
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinkPreview

        if (url != other.url) return false
        if (title != other.title) return false
        if (faviconData != null) {
            if (other.faviconData == null) return false
            if (!faviconData.contentEquals(other.faviconData)) return false
        } else if (other.faviconData != null) return false
        if (fetchedAt != other.fetchedAt) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (faviconData?.contentHashCode() ?: 0)
        result = 31 * result + fetchedAt.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}
