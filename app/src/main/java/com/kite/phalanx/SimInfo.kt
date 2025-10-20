package com.kite.phalanx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat

/**
 * Represents information about a SIM card slot
 */
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String?,
    val phoneNumber: String?,
    val color: Color
)

object SimHelper {
    /**
     * Get list of active SIM cards
     */
    fun getActiveSims(context: Context): List<SimInfo> {
        if (!hasPhoneStatePermission(context)) {
            return emptyList()
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        if (subscriptionManager == null) {
            return emptyList()
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList ?: emptyList()

                val sims = activeSubscriptions.mapIndexed { index, subscriptionInfo ->
                    val color = getSimColor(index, subscriptionInfo)

                    // Get phone number - use newer API on Android 13+, fallback to deprecated method for older versions
                    val phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            subscriptionManager.getPhoneNumber(subscriptionInfo.subscriptionId)
                                ?.takeIf { it.isNotBlank() }
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        subscriptionInfo.number?.takeIf { it.isNotBlank() }
                    }

                    val simInfo = SimInfo(
                        subscriptionId = subscriptionInfo.subscriptionId,
                        slotIndex = subscriptionInfo.simSlotIndex,
                        displayName = subscriptionInfo.displayName?.toString() ?: "SIM ${index + 1}",
                        carrierName = subscriptionInfo.carrierName?.toString(),
                        phoneNumber = phoneNumber,
                        color = color
                    )
                    simInfo
                }
                sims
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get the default SIM subscription ID for SMS
     */
    fun getDefaultSmsSubscriptionId(context: Context): Int {
        if (!hasPhoneStatePermission(context)) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SubscriptionManager.getDefaultSmsSubscriptionId()
            } else {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
        } catch (e: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    /**
     * Get SIM info for a specific subscription ID
     */
    fun getSimInfo(context: Context, subscriptionId: Int): SimInfo? {
        return getActiveSims(context).find { it.subscriptionId == subscriptionId }
    }

    /**
     * Check if device has multiple active SIMs
     */
    fun hasMultipleSims(context: Context): Boolean {
        return getActiveSims(context).size > 1
    }

    private fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get a color for the SIM based on its index and system color if available
     */
    private fun getSimColor(index: Int, subscriptionInfo: SubscriptionInfo): Color {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val iconTint = subscriptionInfo.iconTint
            if (iconTint != 0) {
                Color(iconTint)
            } else {
                getDefaultSimColor(index)
            }
        } else {
            getDefaultSimColor(index)
        }
    }

    /**
     * Get default fallback colors for SIM cards
     */
    private fun getDefaultSimColor(index: Int): Color {
        return when (index) {
            0 -> Color(0xFF2196F3) // Blue
            1 -> Color(0xFFFF9800) // Orange
            else -> Color(0xFF4CAF50) // Green
        }
    }
}
