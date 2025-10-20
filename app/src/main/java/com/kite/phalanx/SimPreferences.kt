package com.kite.phalanx

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore for SIM preferences
private val Context.simPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "sim_preferences")

object SimPreferences {
    private val DEFAULT_SIM_KEY = intPreferencesKey("default_sim_subscription_id")

    // Default colors for user's message bubbles per SIM
    private val DEFAULT_BUBBLE_COLOR_SIM1 = Color(0xFF2196F3) // Blue
    private val DEFAULT_BUBBLE_COLOR_SIM2 = Color(0xFFFF9800) // Orange

    /**
     * Get the user-configured default SIM subscription ID
     * Returns INVALID_SUBSCRIPTION_ID if not set
     */
    fun getDefaultSimFlow(context: Context): Flow<Int> {
        return context.simPreferencesDataStore.data.map { preferences ->
            preferences[DEFAULT_SIM_KEY] ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    /**
     * Get the user-configured default SIM subscription ID synchronously
     */
    suspend fun getDefaultSim(context: Context): Int {
        return getDefaultSimFlow(context).first()
    }

    /**
     * Set the default SIM subscription ID
     */
    suspend fun setDefaultSim(context: Context, subscriptionId: Int) {
        context.simPreferencesDataStore.edit { preferences ->
            preferences[DEFAULT_SIM_KEY] = subscriptionId
        }
    }

    /**
     * Get conversation-specific SIM preference
     * Returns INVALID_SUBSCRIPTION_ID if not set
     */
    fun getConversationSimFlow(context: Context, address: String): Flow<Int> {
        val key = intPreferencesKey("conversation_sim_$address")
        return context.simPreferencesDataStore.data.map { preferences ->
            preferences[key] ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    /**
     * Get conversation-specific SIM preference synchronously
     */
    suspend fun getConversationSim(context: Context, address: String): Int {
        return getConversationSimFlow(context, address).first()
    }

    /**
     * Set conversation-specific SIM preference
     */
    suspend fun setConversationSim(context: Context, address: String, subscriptionId: Int) {
        val key = intPreferencesKey("conversation_sim_$address")
        context.simPreferencesDataStore.edit { preferences ->
            preferences[key] = subscriptionId
        }
    }

    /**
     * Get the SIM to use for a conversation
     * Priority: conversation-specific > default SIM > system default
     */
    suspend fun getSimForConversation(context: Context, address: String): Int {
        val conversationSim = getConversationSim(context, address)
        if (conversationSim != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return conversationSim
        }

        val defaultSim = getDefaultSim(context)
        if (defaultSim != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return defaultSim
        }

        return SimHelper.getDefaultSmsSubscriptionId(context)
    }

    /**
     * Get the bubble color for a specific SIM subscription ID
     */
    suspend fun getBubbleColorForSim(context: Context, subscriptionId: Int): Color {
        val key = intPreferencesKey("bubble_color_sim_$subscriptionId")
        val colorInt = context.simPreferencesDataStore.data.map { preferences ->
            preferences[key]
        }.first()

        return if (colorInt != null) {
            Color(colorInt)
        } else {
            // Return default color based on SIM index
            val activeSims = SimHelper.getActiveSims(context)
            val simIndex = activeSims.indexOfFirst { it.subscriptionId == subscriptionId }
            when (simIndex) {
                0 -> DEFAULT_BUBBLE_COLOR_SIM1
                1 -> DEFAULT_BUBBLE_COLOR_SIM2
                else -> Color(0xFF2196F3) // Default blue
            }
        }
    }

    /**
     * Set the bubble color for a specific SIM subscription ID
     */
    suspend fun setBubbleColorForSim(context: Context, subscriptionId: Int, color: Color) {
        val key = intPreferencesKey("bubble_color_sim_$subscriptionId")
        context.simPreferencesDataStore.edit { preferences ->
            preferences[key] = color.toArgb()
        }
    }

    /**
     * Get bubble color Flow for a specific SIM
     */
    fun getBubbleColorForSimFlow(context: Context, subscriptionId: Int): Flow<Color> {
        val key = intPreferencesKey("bubble_color_sim_$subscriptionId")
        return context.simPreferencesDataStore.data.map { preferences ->
            val colorInt = preferences[key]
            if (colorInt != null) {
                Color(colorInt)
            } else {
                // Return default color based on SIM index
                val activeSims = SimHelper.getActiveSims(context)
                val simIndex = activeSims.indexOfFirst { it.subscriptionId == subscriptionId }
                when (simIndex) {
                    0 -> DEFAULT_BUBBLE_COLOR_SIM1
                    1 -> DEFAULT_BUBBLE_COLOR_SIM2
                    else -> Color(0xFF2196F3)
                }
            }
        }
    }
}
