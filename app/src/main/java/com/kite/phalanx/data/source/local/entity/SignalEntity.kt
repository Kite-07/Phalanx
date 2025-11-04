package com.kite.phalanx.data.source.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing risk signals detected in messages.
 */
@Entity(
    tableName = "signals",
    indices = [Index(value = ["messageId"])]
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long,
    val linkId: String, // Hash or identifier of the link
    val code: String, // Signal code (e.g., "SHORTENER_EXPANDED")
    val weight: Int, // Weight of this signal
    val metadata: String = "", // JSON-encoded additional metadata
    val timestamp: Long
)
