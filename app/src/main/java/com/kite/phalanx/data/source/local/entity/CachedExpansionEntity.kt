package com.kite.phalanx.data.source.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching URL expansions.
 * Stores the final destination of shortened/redirecting URLs.
 */
@Entity(
    tableName = "cached_expansions",
    indices = [Index(value = ["finalUrl"])]
)
data class CachedExpansionEntity(
    @PrimaryKey
    val originalUrl: String,
    val finalUrl: String,
    val redirectChain: String, // JSON-encoded list of URLs
    val timestamp: Long,
    val expiresAt: Long // Timestamp when this cache entry expires
)
