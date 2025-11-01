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
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

// Extension property for DataStore
private val Context.pinnedMessagesDataStore: DataStore<Preferences> by preferencesDataStore(name = "pinned_messages")

/**
 * Represents a pinned message in a conversation
 */
@Serializable
data class PinnedMessage(
    val sender: String,  // Phone number/address of the conversation
    val messageTimestamp: Long,  // Timestamp of the message (used as ID)
    val expiryTimestamp: Long?,  // When the pin expires (null = always pinned)
    val snippet: String,  // Cached preview of the message
    val hasAttachments: Boolean = false  // Whether the message has attachments
)

/**
 * Manages pinned messages within conversations
 * Pinned messages appear below the top bar and persist during scroll
 */
object PinnedMessagesPreferences {

    private val PINNED_MESSAGES_KEY = stringPreferencesKey("pinned_messages_json")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Pin duration options
     */
    enum class PinDuration(val label: String, val durationMillis: Long?) {
        ONE_DAY("1 day", 24 * 60 * 60 * 1000L),
        ONE_WEEK("1 week", 7 * 24 * 60 * 60 * 1000L),
        ALWAYS("Always", null)
    }

    /**
     * Pin a message in a conversation
     * @param context The application context
     * @param sender The phone number/address of the conversation
     * @param messageTimestamp Timestamp of the message to pin
     * @param snippet Preview text of the message
     * @param duration How long to keep the message pinned
     * @param hasAttachments Whether the message has attachments
     */
    suspend fun pinMessage(
        context: Context,
        sender: String,
        messageTimestamp: Long,
        snippet: String,
        duration: PinDuration,
        hasAttachments: Boolean = false
    ) {
        context.pinnedMessagesDataStore.edit { preferences ->
            val currentPinned = getPinnedMessagesFromJson(preferences[PINNED_MESSAGES_KEY])

            // Calculate expiry timestamp
            val expiryTimestamp = duration.durationMillis?.let {
                System.currentTimeMillis() + it
            }

            val newPinnedMessage = PinnedMessage(
                sender = sender,
                messageTimestamp = messageTimestamp,
                expiryTimestamp = expiryTimestamp,
                snippet = snippet,
                hasAttachments = hasAttachments
            )

            // Remove any existing pin for this message, then add the new one
            val filteredList = currentPinned.filter {
                !(it.sender == sender && it.messageTimestamp == messageTimestamp)
            }
            val newList = filteredList + newPinnedMessage

            preferences[PINNED_MESSAGES_KEY] = json.encodeToString(newList)
        }
    }

    /**
     * Unpin a message
     * @param context The application context
     * @param sender The phone number/address of the conversation
     * @param messageTimestamp Timestamp of the message to unpin
     */
    suspend fun unpinMessage(context: Context, sender: String, messageTimestamp: Long) {
        context.pinnedMessagesDataStore.edit { preferences ->
            val currentPinned = getPinnedMessagesFromJson(preferences[PINNED_MESSAGES_KEY])
            val newList = currentPinned.filter {
                !(it.sender == sender && it.messageTimestamp == messageTimestamp)
            }

            if (newList.isEmpty()) {
                preferences.remove(PINNED_MESSAGES_KEY)
            } else {
                preferences[PINNED_MESSAGES_KEY] = json.encodeToString(newList)
            }
        }
    }

    /**
     * Get all pinned messages for a specific conversation (excluding expired ones)
     * @param context The application context
     * @param sender The phone number/address of the conversation
     * @return Flow<List<PinnedMessage>> of pinned messages for this conversation
     */
    fun getPinnedMessagesForSender(context: Context, sender: String): Flow<List<PinnedMessage>> {
        return context.pinnedMessagesDataStore.data.map { preferences ->
            val allPinned = getPinnedMessagesFromJson(preferences[PINNED_MESSAGES_KEY])
            val now = System.currentTimeMillis()

            // Filter to this sender and remove expired pins
            allPinned.filter { pinnedMsg ->
                pinnedMsg.sender == sender &&
                (pinnedMsg.expiryTimestamp == null || pinnedMsg.expiryTimestamp > now)
            }
        }
    }

    /**
     * Get all pinned messages for a specific conversation (blocking, excluding expired)
     * @param context The application context
     * @param sender The phone number/address of the conversation
     * @return List<PinnedMessage> of pinned messages for this conversation
     */
    suspend fun getPinnedMessagesForSenderSync(context: Context, sender: String): List<PinnedMessage> {
        val prefs = context.pinnedMessagesDataStore.data.first()
        val allPinned = getPinnedMessagesFromJson(prefs[PINNED_MESSAGES_KEY])
        val now = System.currentTimeMillis()

        return allPinned.filter { pinnedMsg ->
            pinnedMsg.sender == sender &&
            (pinnedMsg.expiryTimestamp == null || pinnedMsg.expiryTimestamp > now)
        }
    }

    /**
     * Check if a specific message is pinned
     * @param context The application context
     * @param sender The phone number/address of the conversation
     * @param messageTimestamp Timestamp of the message
     * @return true if pinned and not expired, false otherwise
     */
    suspend fun isMessagePinned(context: Context, sender: String, messageTimestamp: Long): Boolean {
        val pinnedMessages = getPinnedMessagesForSenderSync(context, sender)
        return pinnedMessages.any { it.messageTimestamp == messageTimestamp }
    }

    /**
     * Clean up expired pins
     * @param context The application context
     */
    suspend fun cleanupExpiredPins(context: Context) {
        context.pinnedMessagesDataStore.edit { preferences ->
            val currentPinned = getPinnedMessagesFromJson(preferences[PINNED_MESSAGES_KEY])
            val now = System.currentTimeMillis()

            val activePinned = currentPinned.filter { pinnedMsg ->
                pinnedMsg.expiryTimestamp == null || pinnedMsg.expiryTimestamp > now
            }

            if (activePinned.isEmpty()) {
                preferences.remove(PINNED_MESSAGES_KEY)
            } else {
                preferences[PINNED_MESSAGES_KEY] = json.encodeToString(activePinned)
            }
        }
    }

    /**
     * Clear all pinned messages
     * @param context The application context
     */
    suspend fun clearAllPinned(context: Context) {
        context.pinnedMessagesDataStore.edit { preferences ->
            preferences.remove(PINNED_MESSAGES_KEY)
        }
    }

    /**
     * Helper function to parse the stored JSON into a list
     */
    private fun getPinnedMessagesFromJson(storedValue: String?): List<PinnedMessage> {
        if (storedValue.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<PinnedMessage>>(storedValue)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
