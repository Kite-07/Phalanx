package com.kite.phalanx

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Helper class for sending SMS messages
 */
object SmsHelper {
    private const val TAG = "SmsHelper"
    private const val ACTION_SMS_SENT = "com.kite.phalanx.SMS_SENT"
    private const val ACTION_SMS_DELIVERED = "com.kite.phalanx.SMS_DELIVERED"

    /**
     * Sends an SMS message to the specified recipient
     * @param subscriptionId Optional SIM subscription ID. If -1, uses default SIM
     */
    fun sendSms(context: Context, recipient: String, message: String, subscriptionId: Int = -1) {
        // Check if we're the default SMS app
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        val isDefaultViaPackage = defaultSmsPackage == context.packageName

        // On Android 10+, also check via RoleManager
        val isDefaultViaRole = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val isDefaultSmsApp = isDefaultViaPackage || isDefaultViaRole

        // Check for SEND_SMS permission
        val hasSendPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSendPermission) {
            Toast.makeText(context, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Write to database FIRST if we're the default SMS app
            // This gives us a message URI to track status updates
            val messageUri = if (isDefaultSmsApp) {
                val uri = SmsOperations.writeSentSms(context, recipient, message, System.currentTimeMillis(), subscriptionId)
                Log.d(TAG, "Wrote message to database with URI: $uri")
                uri
            } else {
                Log.w(TAG, "Not default SMS app, message won't be tracked")
                null
            }

            // Get SmsManager - use subscription-specific if provided, otherwise default
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - use system service
                val manager = context.getSystemService(SmsManager::class.java)
                if (subscriptionId != -1) {
                    manager.createForSubscriptionId(subscriptionId)
                } else {
                    manager
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Android 5.1+ - use subscription ID if provided
                if (subscriptionId != -1) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            } else {
                // Android 5.0 and below - use deprecated getDefault()
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                Toast.makeText(context, "SMS service unavailable", Toast.LENGTH_SHORT).show()
                return
            }

            // Create pending intents for sent and delivered status
            // Include message URI so SmsSentReceiver can update the specific message
            // Use EXPLICIT intents to avoid implicit broadcast restrictions
            val sentIntent = if (messageUri != null) {
                val intent = Intent(ACTION_SMS_SENT).apply {
                    setClass(context, SmsSentReceiver::class.java)
                    putExtra(SmsSentReceiver.EXTRA_MESSAGE_URI, messageUri.toString())
                }
                Log.d(TAG, "Creating sentIntent for URI: $messageUri")
                PendingIntent.getBroadcast(
                    context,
                    messageUri.hashCode(),
                    intent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            val deliveredIntent = if (messageUri != null) {
                val intent = Intent(ACTION_SMS_DELIVERED).apply {
                    setClass(context, SmsSentReceiver::class.java)
                    putExtra(SmsSentReceiver.EXTRA_MESSAGE_URI, messageUri.toString())
                }
                PendingIntent.getBroadcast(
                    context,
                    messageUri.hashCode() + 1,
                    intent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            // Split message if it's too long (standard SMS is 160 characters)
            val parts = smsManager.divideMessage(message)

            if (parts.size == 1) {
                // Send single SMS
                try {
                    Log.d(TAG, "Sending SMS to $recipient with URI tracking: ${messageUri != null}")
                    smsManager.sendTextMessage(
                        recipient,
                        null,
                        message,
                        sentIntent,
                        deliveredIntent
                    )
                    Log.d(TAG, "SMS sent successfully")
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid phone number or message", e)
                    Toast.makeText(context, "Invalid phone number or message", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending SMS", e)
                    throw e
                }
            } else {
                // Send multipart SMS
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntent?.let { sentIntents.add(it) }
                    deliveredIntent?.let { deliveredIntents.add(it) }
                }
                try {
                    smsManager.sendMultipartTextMessage(
                        recipient,
                        null,
                        parts,
                        if (sentIntents.isNotEmpty()) sentIntents else null,
                        if (deliveredIntents.isNotEmpty()) deliveredIntents else null
                    )
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, "Invalid phone number or message", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    throw e
                }
            }

            Toast.makeText(context, "Sending message...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
