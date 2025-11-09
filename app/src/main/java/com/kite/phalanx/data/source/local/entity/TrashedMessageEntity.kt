package com.kite.phalanx.data.source.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Room entity for storing trashed messages with 30-day retention.
 *
 * Phase 3 - Safety Rails: Trash Vault
 * - Soft-delete: Messages moved here instead of permanent deletion
 * - 30-day retention: Auto-purged after expiresAt timestamp
 * - Restore: Can return to original thread via SMS provider
 * - Thread grouping: Messages deleted together share a threadGroupId
 */
@Entity(
    tableName = "trashed_messages",
    indices = [
        Index(value = ["expiresAt"]),      // For efficient auto-purge queries
        Index(value = ["threadGroupId"])    // For efficient thread grouping queries
    ]
)
data class TrashedMessageEntity(
    @PrimaryKey
    val messageId: Long,              // Original SMS provider message ID
    val sender: String,               // Phone number or short code
    val body: String,                 // Message content
    val timestamp: Long,              // Original message timestamp (millis)
    val trashedAt: Long,              // When moved to trash (millis)
    val expiresAt: Long,              // trashedAt + 30 days (millis)
    val originalThreadId: Long = 0,   // Original conversation thread ID
    val isMms: Boolean = false,       // True if MMS, false if SMS
    val subscriptionId: Int = -1,     // SIM subscription ID (-1 = default)
    val threadGroupId: String? = null // Shared ID for messages deleted together (null = single message)
)
