package com.kite.phalanx.data.repository

import com.kite.phalanx.data.source.local.dao.AllowBlockRuleDao
import com.kite.phalanx.data.source.local.entity.AllowBlockRuleEntity
import com.kite.phalanx.data.source.local.entity.RuleAction
import com.kite.phalanx.data.source.local.entity.RuleType
import com.kite.phalanx.domain.repository.AllowBlockListRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implementation of allow/block list repository.
 *
 * Phase 3 - Safety Rails: Allow/Block Lists
 * Handles domain/sender/pattern matching with precedence rules.
 *
 * Precedence logic:
 * 1. Higher priority rules checked first
 * 2. ALLOW overrides BLOCK (unless critical RED signal)
 * 3. First match wins within same priority
 */
class AllowBlockListRepositoryImpl @Inject constructor(
    private val allowBlockRuleDao: AllowBlockRuleDao
) : AllowBlockListRepository {

    override suspend fun addRule(
        type: RuleType,
        value: String,
        action: RuleAction,
        priority: Int,
        notes: String
    ): Long {
        val rule = AllowBlockRuleEntity(
            type = type.name,
            value = value,
            action = action.name,
            priority = priority,
            createdAt = System.currentTimeMillis(),
            notes = notes
        )
        return allowBlockRuleDao.insert(rule)
    }

    override suspend fun updateRule(rule: AllowBlockRuleEntity) {
        allowBlockRuleDao.update(rule)
    }

    override fun getAllRules(): Flow<List<AllowBlockRuleEntity>> {
        return allowBlockRuleDao.getAllRules()
    }

    override suspend fun getAllRulesList(): List<AllowBlockRuleEntity> {
        return allowBlockRuleDao.getAllRulesList()
    }

    override suspend fun checkDomainRule(domain: String): RuleAction? {
        val domainRules = allowBlockRuleDao.getRulesByType(RuleType.DOMAIN.name)

        // Check for exact match first
        for (rule in domainRules) {
            if (rule.value.equals(domain, ignoreCase = true)) {
                return RuleAction.valueOf(rule.action)
            }
        }

        // Check for subdomain matches (e.g., "example.com" matches "sub.example.com")
        for (rule in domainRules) {
            if (domain.endsWith(".${rule.value}", ignoreCase = true)) {
                return RuleAction.valueOf(rule.action)
            }
        }

        return null
    }

    override suspend fun checkSenderRule(sender: String): RuleAction? {
        val senderRules = allowBlockRuleDao.getRulesByType(RuleType.SENDER.name)

        for (rule in senderRules) {
            if (normalizePhoneNumber(rule.value) == normalizePhoneNumber(sender)) {
                return RuleAction.valueOf(rule.action)
            }
        }

        return null
    }

    override suspend fun checkAllRules(
        domain: String?,
        sender: String,
        messageBody: String
    ): RuleAction? {
        // Get all rules sorted by priority (highest first)
        val allRules = allowBlockRuleDao.getAllRulesList()

        // Track highest priority ALLOW and BLOCK matches
        var allowMatch: AllowBlockRuleEntity? = null
        var blockMatch: AllowBlockRuleEntity? = null

        for (rule in allRules) {
            val matches = when (RuleType.valueOf(rule.type)) {
                RuleType.DOMAIN -> {
                    domain != null && (
                        rule.value.equals(domain, ignoreCase = true) ||
                        domain.endsWith(".${rule.value}", ignoreCase = true)
                    )
                }
                RuleType.SENDER -> {
                    normalizePhoneNumber(rule.value) == normalizePhoneNumber(sender)
                }
                RuleType.PATTERN -> {
                    try {
                        messageBody.contains(Regex(rule.value, RegexOption.IGNORE_CASE))
                    } catch (e: Exception) {
                        false // Invalid regex
                    }
                }
            }

            if (matches) {
                val action = RuleAction.valueOf(rule.action)
                when (action) {
                    RuleAction.ALLOW -> {
                        if (allowMatch == null || rule.priority > allowMatch.priority) {
                            allowMatch = rule
                        }
                    }
                    RuleAction.BLOCK -> {
                        if (blockMatch == null || rule.priority > blockMatch.priority) {
                            blockMatch = rule
                        }
                    }
                }
            }
        }

        // Precedence: ALLOW > BLOCK
        return when {
            allowMatch != null -> RuleAction.ALLOW
            blockMatch != null -> RuleAction.BLOCK
            else -> null
        }
    }

    override suspend fun getAllowedDomains(): List<String> {
        return allowBlockRuleDao.getAllowedDomains().map { it.value }
    }

    override suspend fun getBlockedSenders(): List<String> {
        return allowBlockRuleDao.getBlockedSenders().map { it.value }
    }

    override suspend fun deleteRule(ruleId: Long) {
        allowBlockRuleDao.delete(ruleId)
    }

    override suspend fun deleteRuleByValue(type: RuleType, value: String) {
        allowBlockRuleDao.deleteByTypeAndValue(type.name, value)
    }

    override suspend fun ruleExists(type: RuleType, value: String): Boolean {
        return allowBlockRuleDao.findRule(type.name, value) != null
    }

    override suspend fun getCount(): Int {
        return allowBlockRuleDao.getCount()
    }

    /**
     * Normalize phone number for comparison (remove spaces, dashes, parentheses).
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }
}
