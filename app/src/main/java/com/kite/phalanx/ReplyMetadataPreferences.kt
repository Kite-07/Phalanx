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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Reply metadata for a sent message
 */
@Serializable
data class ReplyMetadata(
    val replyToMessageId: Long,          // Timestamp of the message being replied to
    val replyToSnippet: String,          // Snippet of the original message
    val replyToSender: String,           // Sender of the original message (contact name or number)
    val replyToIsFromUser: Boolean       // Whether the replied-to message was sent by user
)

/**
 * Manages reply metadata for sent messages using DataStore.
 *
 * When a user replies to a message, we store metadata about which message
 * was replied to. This allows us to show reply references in the conversation.
 */
object ReplyMetadataPreferences {
    private val Context.replyMetadataDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "reply_metadata"
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Save reply metadata for a sent message
     *
     * @param context Context
     * @param sentMessageTimestamp Timestamp of the sent message (used as key)
     * @param metadata Reply metadata to save
     */
    suspend fun saveReplyMetadata(
        context: Context,
        sentMessageTimestamp: Long,
        metadata: ReplyMetadata
    ) {
        val key = stringPreferencesKey("reply_$sentMessageTimestamp")
        val jsonString = json.encodeToString(metadata)

        context.replyMetadataDataStore.edit { preferences ->
            preferences[key] = jsonString
        }
    }

    /**
     * Get reply metadata for a sent message
     *
     * @param context Context
     * @param sentMessageTimestamp Timestamp of the sent message
     * @return Reply metadata if exists, null otherwise
     */
    suspend fun getReplyMetadata(
        context: Context,
        sentMessageTimestamp: Long
    ): ReplyMetadata? {
        val key = stringPreferencesKey("reply_$sentMessageTimestamp")

        return try {
            val preferences = context.replyMetadataDataStore.data.first()
            val jsonString = preferences[key] ?: return null
            json.decodeFromString<ReplyMetadata>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get reply metadata flow for a sent message (reactive)
     *
     * @param context Context
     * @param sentMessageTimestamp Timestamp of the sent message
     * @return Flow of reply metadata (null if doesn't exist)
     */
    fun getReplyMetadataFlow(
        context: Context,
        sentMessageTimestamp: Long
    ): Flow<ReplyMetadata?> {
        val key = stringPreferencesKey("reply_$sentMessageTimestamp")

        return context.replyMetadataDataStore.data.map { preferences ->
            try {
                val jsonString = preferences[key] ?: return@map null
                json.decodeFromString<ReplyMetadata>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Delete reply metadata for a message
     *
     * @param context Context
     * @param sentMessageTimestamp Timestamp of the sent message
     */
    suspend fun deleteReplyMetadata(
        context: Context,
        sentMessageTimestamp: Long
    ) {
        val key = stringPreferencesKey("reply_$sentMessageTimestamp")

        context.replyMetadataDataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    /**
     * Clean up old reply metadata (older than 30 days)
     * Should be called periodically to prevent storage bloat
     *
     * @param context Context
     */
    suspend fun cleanupOldMetadata(context: Context) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)

        context.replyMetadataDataStore.edit { preferences ->
            val keysToRemove = preferences.asMap().keys.filter { key ->
                val timestamp = key.name.removePrefix("reply_").toLongOrNull() ?: return@filter false
                timestamp < thirtyDaysAgo
            }

            keysToRemove.forEach { key ->
                preferences.remove(key)
            }
        }
    }
}
