package com.kite.phalanx

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property for DataStore
private val Context.conversationMuteDataStore: DataStore<Preferences> by preferencesDataStore(name = "conversation_mute")

/**
 * Manages muting preferences for conversations
 */
object ConversationMutePreferences {

    /**
     * Mute durations in milliseconds
     */
    object MuteDuration {
        const val ONE_HOUR = 60L * 60L * 1000L
        const val ONE_DAY = 24L * 60L * 60L * 1000L
        const val ONE_WEEK = 7L * 24L * 60L * 60L * 1000L
        const val ALWAYS = Long.MAX_VALUE
    }

    /**
     * Get the mute key for a specific conversation
     */
    private fun getMuteKey(address: String): Preferences.Key<Long> {
        return longPreferencesKey("mute_$address")
    }

    /**
     * Mute a conversation until the specified timestamp
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @param untilTimestamp The timestamp (in milliseconds) until which to mute, or Long.MAX_VALUE for always
     */
    suspend fun muteConversation(context: Context, address: String, untilTimestamp: Long) {
        context.conversationMuteDataStore.edit { preferences ->
            preferences[getMuteKey(address)] = untilTimestamp
        }
    }

    /**
     * Mute a conversation for a specific duration
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @param durationMillis The duration in milliseconds (use MuteDuration constants)
     */
    suspend fun muteConversationFor(context: Context, address: String, durationMillis: Long) {
        val untilTimestamp = if (durationMillis == MuteDuration.ALWAYS) {
            Long.MAX_VALUE
        } else {
            System.currentTimeMillis() + durationMillis
        }
        muteConversation(context, address, untilTimestamp)
    }

    /**
     * Unmute a conversation
     * @param context The application context
     * @param address The phone number/address of the conversation
     */
    suspend fun unmuteConversation(context: Context, address: String) {
        context.conversationMuteDataStore.edit { preferences ->
            preferences.remove(getMuteKey(address))
        }
    }

    /**
     * Check if a conversation is currently muted
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return true if muted, false otherwise
     */
    suspend fun isConversationMuted(context: Context, address: String): Boolean {
        val prefs = context.conversationMuteDataStore.data.first()
        val muteUntil = prefs[getMuteKey(address)] ?: 0L

        if (muteUntil > 0L) {
            // Check if mute is still active
            if (muteUntil == Long.MAX_VALUE || muteUntil > System.currentTimeMillis()) {
                return true
            } else {
                // Mute has expired, clean it up
                unmuteConversation(context, address)
            }
        }
        return false
    }

    /**
     * Get a Flow that emits whether a conversation is muted
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return Flow<Boolean> that emits true if muted, false otherwise
     */
    fun isConversationMutedFlow(context: Context, address: String): Flow<Boolean> {
        return context.conversationMuteDataStore.data.map { preferences ->
            val muteUntil = preferences[getMuteKey(address)] ?: 0L
            if (muteUntil > 0L) {
                // Check if mute is still active
                muteUntil == Long.MAX_VALUE || muteUntil > System.currentTimeMillis()
            } else {
                false
            }
        }
    }

    /**
     * Get the timestamp until which a conversation is muted
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return The timestamp in milliseconds, or 0 if not muted
     */
    fun getMuteUntilTimestamp(context: Context, address: String): Flow<Long> {
        return context.conversationMuteDataStore.data.map { preferences ->
            preferences[getMuteKey(address)] ?: 0L
        }
    }
}
