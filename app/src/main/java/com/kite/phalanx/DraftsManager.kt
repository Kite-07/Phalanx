package com.kite.phalanx

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Manager for handling SMS drafts per conversation thread
 */
private val Context.draftsDataStore: DataStore<Preferences> by preferencesDataStore(name = "sms_drafts")

object DraftsManager {
    /**
     * Saves a draft message for a specific sender
     */
    suspend fun saveDraft(context: Context, sender: String, draftText: String) {
        try {
            val key = stringPreferencesKey(normalizeSender(sender))
            context.draftsDataStore.edit { preferences ->
                if (draftText.isBlank()) {
                    // Remove draft if text is blank
                    preferences.remove(key)
                } else {
                    preferences[key] = draftText
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DraftsManager", "Error saving draft for $sender", e)
            throw e
        }
    }

    /**
     * Retrieves the draft message for a specific sender
     */
    fun getDraft(context: Context, sender: String): Flow<String> {
        return try {
            val key = stringPreferencesKey(normalizeSender(sender))
            context.draftsDataStore.data
                .map { preferences -> preferences[key] ?: "" }
                .catch { e ->
                    android.util.Log.e("DraftsManager", "Error getting draft flow for $sender", e)
                    emit("")
                }
        } catch (e: Exception) {
            android.util.Log.e("DraftsManager", "Error creating draft flow for $sender", e)
            flowOf("")
        }
    }

    /**
     * Deletes the draft for a specific sender
     */
    suspend fun deleteDraft(context: Context, sender: String) {
        try {
            val key = stringPreferencesKey(normalizeSender(sender))
            context.draftsDataStore.edit { preferences ->
                preferences.remove(key)
            }
        } catch (e: Exception) {
            android.util.Log.e("DraftsManager", "Error deleting draft for $sender", e)
            throw e
        }
    }

    /**
     * Gets draft for a specific sender synchronously (for use in UI list)
     */
    suspend fun getDraftSync(context: Context, sender: String): String? {
        return try {
            val key = stringPreferencesKey(normalizeSender(sender))
            context.draftsDataStore.data
                .map { preferences -> preferences[key] }
                .first()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            android.util.Log.e("DraftsManager", "Error getting draft for $sender", e)
            null
        }
    }

    /**
     * Normalizes sender phone number to use as key
     * Removes all non-digit characters except + sign
     */
    private fun normalizeSender(sender: String): String {
        return "draft_${sender.replace(Regex("[^0-9+]"), "")}"
    }
}
