package com.kite.phalanx.domain.usecase

import android.content.Context
import com.kite.phalanx.TrustedDomainsPreferences
import com.kite.phalanx.data.source.local.entity.RuleAction
import com.kite.phalanx.data.source.local.entity.RuleType
import com.kite.phalanx.domain.repository.AllowBlockListRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Use case for migrating legacy TrustedDomainsPreferences to AllowBlockListRepository.
 *
 * Phase 3 - Safety Rails: Migration Utility
 *
 * This should be called once during app upgrade to migrate existing trusted domains
 * from the old DataStore-based system to the new Room-based allow/block list.
 *
 * Migration process:
 * 1. Read all trusted domains from TrustedDomainsPreferences
 * 2. Create ALLOW rules in AllowBlockListRepository for each domain
 * 3. Set high priority (90) to match previous whitelist behavior
 * 4. Mark migration as complete in preferences
 */
class MigrateTrustedDomainsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val allowBlockListRepository: AllowBlockListRepository
) {

    companion object {
        // Priority for migrated trusted domains (high, but not maximum)
        private const val MIGRATED_DOMAIN_PRIORITY = 90

        // Preference key to track if migration is complete
        private const val MIGRATION_COMPLETE_KEY = "trusted_domains_migration_complete"
    }

    /**
     * Execute the migration if it hasn't been done yet.
     *
     * @return Number of domains migrated, or null if migration already completed
     */
    suspend fun execute(): Int? {
        // Check if migration already completed
        if (isMigrationComplete()) {
            return null
        }

        // Get all trusted domains from old system
        val trustedDomains = TrustedDomainsPreferences.getTrustedDomains(context)

        // Migrate each domain to new allow/block list
        trustedDomains.forEach { domain ->
            allowBlockListRepository.addRule(
                type = RuleType.DOMAIN,
                value = domain,
                action = RuleAction.ALLOW,
                priority = MIGRATED_DOMAIN_PRIORITY,
                notes = "Migrated from legacy trusted domains"
            )
        }

        // Mark migration as complete
        markMigrationComplete()

        return trustedDomains.size
    }

    /**
     * Check if migration has already been completed.
     */
    private suspend fun isMigrationComplete(): Boolean {
        val prefs = context.getSharedPreferences("phalanx_migration", Context.MODE_PRIVATE)
        return prefs.getBoolean(MIGRATION_COMPLETE_KEY, false)
    }

    /**
     * Mark migration as complete to prevent running again.
     */
    private fun markMigrationComplete() {
        val prefs = context.getSharedPreferences("phalanx_migration", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
    }

    /**
     * Force re-run of migration (for testing/debugging only).
     */
    fun resetMigration() {
        val prefs = context.getSharedPreferences("phalanx_migration", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(MIGRATION_COMPLETE_KEY, false).apply()
    }
}
