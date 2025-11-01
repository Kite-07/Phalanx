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

// Extension property for DataStore
private val Context.pinnedThreadsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pinned_threads")

/**
 * Manages pinned threads preferences
 * Pinned threads appear at the top of the conversation list in the order they were pinned
 */
object PinnedThreadsPreferences {

    private val PINNED_THREADS_KEY = stringPreferencesKey("pinned_threads_list")
    private const val SEPARATOR = "|"

    /**
     * Pin a thread (adds to end of pinned list)
     * @param context The application context
     * @param address The phone number/address of the conversation
     */
    suspend fun pinThread(context: Context, address: String) {
        context.pinnedThreadsDataStore.edit { preferences ->
            val currentPinned = getPinnedListFromString(preferences[PINNED_THREADS_KEY])
            if (address !in currentPinned) {
                val newPinned = currentPinned + address
                preferences[PINNED_THREADS_KEY] = newPinned.joinToString(SEPARATOR)
            }
        }
    }

    /**
     * Unpin a thread
     * @param context The application context
     * @param address The phone number/address of the conversation
     */
    suspend fun unpinThread(context: Context, address: String) {
        context.pinnedThreadsDataStore.edit { preferences ->
            val currentPinned = getPinnedListFromString(preferences[PINNED_THREADS_KEY])
            val newPinned = currentPinned - address
            if (newPinned.isEmpty()) {
                preferences.remove(PINNED_THREADS_KEY)
            } else {
                preferences[PINNED_THREADS_KEY] = newPinned.joinToString(SEPARATOR)
            }
        }
    }

    /**
     * Toggle pin state of a thread
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return true if thread is now pinned, false if unpinned
     */
    suspend fun togglePinThread(context: Context, address: String): Boolean {
        val isPinned = isThreadPinned(context, address)
        if (isPinned) {
            unpinThread(context, address)
        } else {
            pinThread(context, address)
        }
        return !isPinned
    }

    /**
     * Check if a thread is pinned
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return true if pinned, false otherwise
     */
    suspend fun isThreadPinned(context: Context, address: String): Boolean {
        val prefs = context.pinnedThreadsDataStore.data.first()
        val pinnedThreads = getPinnedListFromString(prefs[PINNED_THREADS_KEY])
        return address in pinnedThreads
    }

    /**
     * Get a Flow that emits whether a thread is pinned
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return Flow<Boolean> that emits true if pinned, false otherwise
     */
    fun isThreadPinnedFlow(context: Context, address: String): Flow<Boolean> {
        return context.pinnedThreadsDataStore.data.map { preferences ->
            val pinnedThreads = getPinnedListFromString(preferences[PINNED_THREADS_KEY])
            address in pinnedThreads
        }
    }

    /**
     * Get all pinned thread addresses in order
     * @param context The application context
     * @return Flow<List<String>> of pinned thread addresses (ordered)
     */
    fun getPinnedThreadsFlow(context: Context): Flow<List<String>> {
        return context.pinnedThreadsDataStore.data.map { preferences ->
            getPinnedListFromString(preferences[PINNED_THREADS_KEY])
        }
    }

    /**
     * Get all pinned thread addresses in order (blocking)
     * @param context The application context
     * @return List<String> of pinned thread addresses (ordered)
     */
    suspend fun getPinnedThreads(context: Context): List<String> {
        val prefs = context.pinnedThreadsDataStore.data.first()
        return getPinnedListFromString(prefs[PINNED_THREADS_KEY])
    }

    /**
     * Clear all pinned threads
     * @param context The application context
     */
    suspend fun clearAllPinned(context: Context) {
        context.pinnedThreadsDataStore.edit { preferences ->
            preferences.remove(PINNED_THREADS_KEY)
        }
    }

    /**
     * Helper function to parse the stored string into a list
     */
    private fun getPinnedListFromString(storedValue: String?): List<String> {
        if (storedValue.isNullOrBlank()) return emptyList()
        return storedValue.split(SEPARATOR).filter { it.isNotBlank() }
    }
}
