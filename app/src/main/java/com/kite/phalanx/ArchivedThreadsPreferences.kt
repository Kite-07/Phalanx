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
private val Context.archivedThreadsDataStore: DataStore<Preferences> by preferencesDataStore(name = "archived_threads")

/**
 * Manages archived threads preferences
 * Archived threads are hidden from the main conversation list but not deleted
 */
object ArchivedThreadsPreferences {

    private val ARCHIVED_THREADS_KEY = stringSetPreferencesKey("archived_threads")

    /**
     * Archive a thread
     * @param context The application context
     * @param address The phone number/address of the conversation
     */
    suspend fun archiveThread(context: Context, address: String) {
        context.archivedThreadsDataStore.edit { preferences ->
            val currentArchived = preferences[ARCHIVED_THREADS_KEY] ?: emptySet()
            preferences[ARCHIVED_THREADS_KEY] = currentArchived + address
        }
    }

    /**
     * Unarchive a thread (restore to inbox)
     * @param context The application context
     * @param address The phone number/address of the conversation
     */
    suspend fun unarchiveThread(context: Context, address: String) {
        context.archivedThreadsDataStore.edit { preferences ->
            val currentArchived = preferences[ARCHIVED_THREADS_KEY] ?: emptySet()
            preferences[ARCHIVED_THREADS_KEY] = currentArchived - address
        }
    }

    /**
     * Check if a thread is archived
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return true if archived, false otherwise
     */
    suspend fun isThreadArchived(context: Context, address: String): Boolean {
        val prefs = context.archivedThreadsDataStore.data.first()
        val archivedThreads = prefs[ARCHIVED_THREADS_KEY] ?: emptySet()
        return address in archivedThreads
    }

    /**
     * Get a Flow that emits whether a thread is archived
     * @param context The application context
     * @param address The phone number/address of the conversation
     * @return Flow<Boolean> that emits true if archived, false otherwise
     */
    fun isThreadArchivedFlow(context: Context, address: String): Flow<Boolean> {
        return context.archivedThreadsDataStore.data.map { preferences ->
            val archivedThreads = preferences[ARCHIVED_THREADS_KEY] ?: emptySet()
            address in archivedThreads
        }
    }

    /**
     * Get all archived thread addresses
     * @param context The application context
     * @return Flow<Set<String>> of archived thread addresses
     */
    fun getArchivedThreadsFlow(context: Context): Flow<Set<String>> {
        return context.archivedThreadsDataStore.data.map { preferences ->
            preferences[ARCHIVED_THREADS_KEY] ?: emptySet()
        }
    }

    /**
     * Get all archived thread addresses (blocking)
     * @param context The application context
     * @return Set<String> of archived thread addresses
     */
    suspend fun getArchivedThreads(context: Context): Set<String> {
        val prefs = context.archivedThreadsDataStore.data.first()
        return prefs[ARCHIVED_THREADS_KEY] ?: emptySet()
    }

    /**
     * Clear all archived threads
     * @param context The application context
     */
    suspend fun clearAllArchived(context: Context) {
        context.archivedThreadsDataStore.edit { preferences ->
            preferences.remove(ARCHIVED_THREADS_KEY)
        }
    }
}
