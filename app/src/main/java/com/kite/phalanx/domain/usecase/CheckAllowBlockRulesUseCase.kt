package com.kite.phalanx.domain.usecase

import com.kite.phalanx.data.source.local.entity.RuleAction
import com.kite.phalanx.domain.repository.AllowBlockListRepository
import javax.inject.Inject

/**
 * Use case for checking allow/block list rules against a message.
 *
 * Phase 3 - Safety Rails: Allow/Block Lists
 * Integrates with risk analysis pipeline to override verdicts.
 *
 * Precedence:
 * - ALLOW rules force GREEN verdict (unless critical RED signal)
 * - BLOCK rules elevate to AMBER/RED
 * - Higher priority rules checked first
 */
class CheckAllowBlockRulesUseCase @Inject constructor(
    private val allowBlockListRepository: AllowBlockListRepository
) {

    /**
     * Check all rules (domain, sender, pattern) for a message.
     *
     * @param domain Registered domain from link analysis (e.g., "example.com")
     * @param sender Sender phone number or short code
     * @param messageBody Full message text
     * @return RuleAction.ALLOW if allowed, RuleAction.BLOCK if blocked, null if no match
     */
    suspend fun execute(
        domain: String?,
        sender: String,
        messageBody: String
    ): RuleAction? {
        return allowBlockListRepository.checkAllRules(domain, sender, messageBody)
    }

    /**
     * Check if a domain is explicitly allowed (whitelisted).
     */
    suspend fun isDomainAllowed(domain: String): Boolean {
        return allowBlockListRepository.checkDomainRule(domain) == RuleAction.ALLOW
    }

    /**
     * Check if a sender is explicitly blocked.
     */
    suspend fun isSenderBlocked(sender: String): Boolean {
        return allowBlockListRepository.checkSenderRule(sender) == RuleAction.BLOCK
    }
}
