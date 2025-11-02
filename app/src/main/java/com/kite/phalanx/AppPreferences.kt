package com.kite.phalanx

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore for app preferences
private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

object AppPreferences {
    private val DELIVERY_REPORTS_KEY = booleanPreferencesKey("delivery_reports_enabled")
    private val MMS_AUTO_DOWNLOAD_WIFI_KEY = booleanPreferencesKey("mms_auto_download_wifi")
    private val MMS_AUTO_DOWNLOAD_CELLULAR_KEY = booleanPreferencesKey("mms_auto_download_cellular")
    private val BYPASS_DND_KEY = booleanPreferencesKey("bypass_dnd")
    private val TEXT_SIZE_SCALE_KEY = floatPreferencesKey("text_size_scale")

    /**
     * Delivery Reports
     */
    fun getDeliveryReportsFlow(context: Context): Flow<Boolean> {
        return context.appPreferencesDataStore.data.map { preferences ->
            preferences[DELIVERY_REPORTS_KEY] ?: false // Default: disabled
        }
    }

    suspend fun getDeliveryReports(context: Context): Boolean {
        return getDeliveryReportsFlow(context).first()
    }

    suspend fun setDeliveryReports(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[DELIVERY_REPORTS_KEY] = enabled
        }
    }

    /**
     * MMS Auto-download on Wi-Fi
     */
    fun getMmsAutoDownloadWifiFlow(context: Context): Flow<Boolean> {
        return context.appPreferencesDataStore.data.map { preferences ->
            preferences[MMS_AUTO_DOWNLOAD_WIFI_KEY] ?: true // Default: enabled
        }
    }

    suspend fun getMmsAutoDownloadWifi(context: Context): Boolean {
        return getMmsAutoDownloadWifiFlow(context).first()
    }

    suspend fun setMmsAutoDownloadWifi(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[MMS_AUTO_DOWNLOAD_WIFI_KEY] = enabled
        }
    }

    /**
     * MMS Auto-download on Cellular
     */
    fun getMmsAutoDownloadCellularFlow(context: Context): Flow<Boolean> {
        return context.appPreferencesDataStore.data.map { preferences ->
            preferences[MMS_AUTO_DOWNLOAD_CELLULAR_KEY] ?: false // Default: disabled (to save data)
        }
    }

    suspend fun getMmsAutoDownloadCellular(context: Context): Boolean {
        return getMmsAutoDownloadCellularFlow(context).first()
    }

    suspend fun setMmsAutoDownloadCellular(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[MMS_AUTO_DOWNLOAD_CELLULAR_KEY] = enabled
        }
    }

    /**
     * Bypass Do Not Disturb mode for notifications
     */
    fun getBypassDndFlow(context: Context): Flow<Boolean> {
        return context.appPreferencesDataStore.data.map { preferences ->
            preferences[BYPASS_DND_KEY] ?: false // Default: disabled
        }
    }

    suspend fun getBypassDnd(context: Context): Boolean {
        return getBypassDndFlow(context).first()
    }

    suspend fun setBypassDnd(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[BYPASS_DND_KEY] = enabled
        }
    }

    /**
     * Text Size Scale
     * Range: 0.7 (Extra Small) to 1.6 (Extra Large), Default: 1.0 (Normal)
     */
    fun getTextSizeScaleFlow(context: Context): Flow<Float> {
        return context.appPreferencesDataStore.data.map { preferences ->
            preferences[TEXT_SIZE_SCALE_KEY] ?: 1.0f // Default: normal size
        }
    }

    suspend fun getTextSizeScale(context: Context): Float {
        return getTextSizeScaleFlow(context).first()
    }

    suspend fun setTextSizeScale(context: Context, scale: Float) {
        context.appPreferencesDataStore.edit { preferences ->
            // Clamp value between 0.7 and 1.6
            preferences[TEXT_SIZE_SCALE_KEY] = scale.coerceIn(0.7f, 1.6f)
        }
    }
}
