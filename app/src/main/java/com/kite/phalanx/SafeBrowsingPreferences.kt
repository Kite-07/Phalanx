package com.kite.phalanx

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore for Safe Browsing API preferences
private val Context.safeBrowsingDataStore: DataStore<Preferences> by preferencesDataStore(name = "safe_browsing_preferences")

object SafeBrowsingPreferences {
    private val CUSTOM_API_KEY = stringPreferencesKey("custom_safe_browsing_api_key")

    // Fallback API key (same as hardcoded in SafeBrowsingRepository)
    const val DEFAULT_API_KEY = "AIzaSyB8n2tdXPaNWNwDae-w9DIBgLFfHWd44ms"

    /**
     * Get custom Safe Browsing API key if set, otherwise return default.
     */
    fun getApiKeyFlow(context: Context): Flow<String> {
        return context.safeBrowsingDataStore.data.map { preferences ->
            preferences[CUSTOM_API_KEY]?.takeIf { it.isNotBlank() } ?: DEFAULT_API_KEY
        }
    }

    suspend fun getApiKey(context: Context): String {
        return getApiKeyFlow(context).first()
    }

    /**
     * Set custom Safe Browsing API key.
     * Pass empty string to reset to default.
     */
    suspend fun setCustomApiKey(context: Context, apiKey: String) {
        context.safeBrowsingDataStore.edit { preferences ->
            if (apiKey.isBlank()) {
                preferences.remove(CUSTOM_API_KEY)
            } else {
                preferences[CUSTOM_API_KEY] = apiKey.trim()
            }
        }
    }

    /**
     * Check if a custom API key is currently set.
     */
    suspend fun hasCustomApiKey(context: Context): Boolean {
        return context.safeBrowsingDataStore.data.first()[CUSTOM_API_KEY]?.isNotBlank() == true
    }

    /**
     * Clear custom API key and revert to default.
     */
    suspend fun clearCustomApiKey(context: Context) {
        context.safeBrowsingDataStore.edit { preferences ->
            preferences.remove(CUSTOM_API_KEY)
        }
    }
}
