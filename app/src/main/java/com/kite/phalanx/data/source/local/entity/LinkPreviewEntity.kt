package com.kite.phalanx.data.source.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching link previews.
 *
 * Per PRD Phase 5:
 * - Stores safe previews of URLs (title and favicon)
 * - Cached to avoid repeated network requests
 * - Expires after 7 days
 */
@Entity(
    tableName = "link_previews",
    indices = [Index(value = ["fetchedAt"])]
)
data class LinkPreviewEntity(
    @PrimaryKey
    val url: String,
    val title: String? = null,
    val faviconData: ByteArray? = null,
    val fetchedAt: Long,
    val expiresAt: Long,  // Cache expires after 7 days
    val error: String? = null
) {
    // ByteArray doesn't implement equals/hashCode by default
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinkPreviewEntity

        if (url != other.url) return false
        if (title != other.title) return false
        if (faviconData != null) {
            if (other.faviconData == null) return false
            if (!faviconData.contentEquals(other.faviconData)) return false
        } else if (other.faviconData != null) return false
        if (fetchedAt != other.fetchedAt) return false
        if (expiresAt != other.expiresAt) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (faviconData?.contentHashCode() ?: 0)
        result = 31 * result + fetchedAt.hashCode()
        result = 31 * result + expiresAt.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}
