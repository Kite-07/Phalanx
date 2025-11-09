package com.kite.phalanx.data.source.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Room entity for allow/block list rules.
 *
 * Phase 3 - Safety Rails: Allow/Block Lists
 * - Domain rules: "example.com" → ALLOW forces GREEN verdict
 * - Sender rules: "+1234567890" → BLOCK prevents messages
 * - Pattern rules: ".*urgent.*" → custom regex matching
 *
 * Precedence: ALLOW > BLOCK, but CRITICAL RED signals always trigger
 */
@Entity(
    tableName = "allow_block_rules",
    indices = [
        Index(value = ["type", "action"]), // For efficient rule lookup
        Index(value = ["priority"])         // For ordered precedence checks
    ]
)
data class AllowBlockRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,           // "DOMAIN", "SENDER", "PATTERN"
    val value: String,          // e.g., "example.com", "+1234567890", ".*urgent.*"
    val action: String,         // "ALLOW", "BLOCK"
    val priority: Int,          // Higher = checked first (0 = lowest)
    val createdAt: Long,        // Timestamp when rule was created
    val notes: String = ""      // Optional user notes/reason for rule
)

/**
 * Rule type enum.
 */
enum class RuleType {
    DOMAIN,     // Domain-based rule (e.g., "example.com")
    SENDER,     // Sender phone number rule (e.g., "+1234567890")
    PATTERN     // Regex pattern rule (e.g., ".*urgent.*")
}

/**
 * Rule action enum.
 */
enum class RuleAction {
    ALLOW,      // Force GREEN verdict (unless CRITICAL RED signal)
    BLOCK       // Elevate to AMBER/RED or reject message
}
