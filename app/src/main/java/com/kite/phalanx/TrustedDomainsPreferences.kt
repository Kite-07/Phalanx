package com.kite.phalanx

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property for DataStore
private val Context.trustedDomainsDataStore: DataStore<Preferences> by preferencesDataStore(name = "trusted_domains")

/**
 * Manages trusted domains for security analysis bypass.
 *
 * When a domain is trusted, messages containing links from that domain
 * will not trigger security warnings.
 */
object TrustedDomainsPreferences {

    private val TRUSTED_DOMAINS_KEY = stringSetPreferencesKey("trusted_domains_set")

    /**
     * Add a domain to the trusted list
     * @param context The application context
     * @param domain The domain to trust (e.g., "example.com")
     */
    suspend fun trustDomain(context: Context, domain: String) {
        if (domain.isBlank()) return

        context.trustedDomainsDataStore.edit { preferences ->
            val currentDomains = preferences[TRUSTED_DOMAINS_KEY] ?: emptySet()
            preferences[TRUSTED_DOMAINS_KEY] = currentDomains + domain.lowercase()
        }
    }

    /**
     * Remove a domain from the trusted list
     * @param context The application context
     * @param domain The domain to remove
     */
    suspend fun untrustDomain(context: Context, domain: String) {
        context.trustedDomainsDataStore.edit { preferences ->
            val currentDomains = preferences[TRUSTED_DOMAINS_KEY] ?: emptySet()
            preferences[TRUSTED_DOMAINS_KEY] = currentDomains - domain.lowercase()
        }
    }

    /**
     * Check if a domain is trusted
     * @param context The application context
     * @param domain The domain to check
     * @return true if trusted, false otherwise
     */
    suspend fun isDomainTrusted(context: Context, domain: String): Boolean {
        val prefs = context.trustedDomainsDataStore.data.first()
        val trustedDomains = prefs[TRUSTED_DOMAINS_KEY] ?: emptySet()
        return trustedDomains.contains(domain.lowercase())
    }

    /**
     * Get all trusted domains
     * @param context The application context
     * @return Set of all trusted domains
     */
    suspend fun getTrustedDomains(context: Context): Set<String> {
        val prefs = context.trustedDomainsDataStore.data.first()
        return prefs[TRUSTED_DOMAINS_KEY] ?: emptySet()
    }

    /**
     * Get a Flow of all trusted domains
     * @param context The application context
     * @return Flow<Set<String>> that emits the set of trusted domains
     */
    fun getTrustedDomainsFlow(context: Context): Flow<Set<String>> {
        return context.trustedDomainsDataStore.data.map { preferences ->
            preferences[TRUSTED_DOMAINS_KEY] ?: emptySet()
        }
    }

    /**
     * Clear all trusted domains
     * @param context The application context
     */
    suspend fun clearAllTrustedDomains(context: Context) {
        context.trustedDomainsDataStore.edit { preferences ->
            preferences.remove(TRUSTED_DOMAINS_KEY)
        }
    }
}
