package com.kite.phalanx.domain.repository

import com.kite.phalanx.data.source.local.entity.AllowBlockRuleEntity
import com.kite.phalanx.data.source.local.entity.RuleAction
import com.kite.phalanx.data.source.local.entity.RuleType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for allow/block list rules.
 *
 * Phase 3 - Safety Rails: Allow/Block Lists
 * Provides domain/sender/pattern matching with precedence rules.
 */
interface AllowBlockListRepository {

    /**
     * Add a new rule to the list.
     *
     * @param type Rule type (DOMAIN, SENDER, PATTERN)
     * @param value Rule value (e.g., "example.com", "+1234567890")
     * @param action Rule action (ALLOW, BLOCK)
     * @param priority Higher = checked first (default: 0)
     * @param notes Optional user notes
     * @return Rule ID
     */
    suspend fun addRule(
        type: RuleType,
        value: String,
        action: RuleAction,
        priority: Int = 0,
        notes: String = ""
    ): Long

    /**
     * Update an existing rule.
     */
    suspend fun updateRule(rule: AllowBlockRuleEntity)

    /**
     * Get all rules as a Flow for reactive UI.
     */
    fun getAllRules(): Flow<List<AllowBlockRuleEntity>>

    /**
     * Get all rules (one-time query).
     */
    suspend fun getAllRulesList(): List<AllowBlockRuleEntity>

    /**
     * Check if a domain is allowed (whitelisted).
     *
     * @return RuleAction.ALLOW if allowed, RuleAction.BLOCK if blocked, null if no rule
     */
    suspend fun checkDomainRule(domain: String): RuleAction?

    /**
     * Check if a sender is blocked.
     *
     * @return RuleAction.ALLOW if allowed, RuleAction.BLOCK if blocked, null if no rule
     */
    suspend fun checkSenderRule(sender: String): RuleAction?

    /**
     * Check all rules for a message (domain, sender, patterns).
     * Returns the highest-priority matching action, or null if no match.
     *
     * Precedence: ALLOW > BLOCK (unless critical RED signal overrides)
     */
    suspend fun checkAllRules(
        domain: String?,
        sender: String,
        messageBody: String
    ): RuleAction?

    /**
     * Get all allowed (whitelisted) domains.
     */
    suspend fun getAllowedDomains(): List<String>

    /**
     * Get all blocked senders.
     */
    suspend fun getBlockedSenders(): List<String>

    /**
     * Delete a rule by ID.
     */
    suspend fun deleteRule(ruleId: Long)

    /**
     * Delete a rule by type and value.
     */
    suspend fun deleteRuleByValue(type: RuleType, value: String)

    /**
     * Check if a specific rule exists.
     */
    suspend fun ruleExists(type: RuleType, value: String): Boolean

    /**
     * Get count of rules.
     */
    suspend fun getCount(): Int
}
