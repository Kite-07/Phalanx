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
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Helper class for sending SMS messages
 */
object SmsHelper {
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
            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_SMS_SENT),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_SMS_DELIVERED),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            // Register receivers for sent and delivered status (one-time use)
            val sentReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (resultCode) {
                        android.app.Activity.RESULT_OK -> {
                            Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()

                            // If we're the default SMS app, write the sent message to the database
                            // Check both package and role (for Android 10+)
                            val shouldWriteToDb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                                    roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false
                                } catch (e: Exception) {
                                    false
                                }
                            } else {
                                android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
                            }

                            if (shouldWriteToDb) {
                                SmsOperations.writeSentSms(context, recipient, message, System.currentTimeMillis(), subscriptionId)
                            }
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            Toast.makeText(context, "Failed to send SMS", Toast.LENGTH_SHORT).show()
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            Toast.makeText(context, "No service", Toast.LENGTH_SHORT).show()
                        }
                        SmsManager.RESULT_ERROR_NULL_PDU -> {
                            Toast.makeText(context, "Null PDU", Toast.LENGTH_SHORT).show()
                        }
                        SmsManager.RESULT_ERROR_RADIO_OFF -> {
                            Toast.makeText(context, "Radio off", Toast.LENGTH_SHORT).show()
                        }
                    }
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: IllegalArgumentException) {
                        // Receiver already unregistered
                    }
                }
            }

            val deliveredReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (resultCode) {
                        android.app.Activity.RESULT_OK -> {
                            Toast.makeText(context, "Message delivered", Toast.LENGTH_SHORT).show()
                        }
                        android.app.Activity.RESULT_CANCELED -> {
                            Toast.makeText(context, "SMS not delivered", Toast.LENGTH_SHORT).show()
                        }
                    }
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: IllegalArgumentException) {
                        // Receiver already unregistered
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT), Context.RECEIVER_NOT_EXPORTED)
                context.registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT))
                context.registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED))
            }

            // Split message if it's too long (standard SMS is 160 characters)
            val parts = smsManager.divideMessage(message)

            if (parts.size == 1) {
                // Send single SMS
                try {
                    smsManager.sendTextMessage(
                        recipient,
                        null,
                        message,
                        sentIntent,
                        deliveredIntent
                    )
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, "Invalid phone number or message", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    throw e
                }
            } else {
                // Send multipart SMS
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }
                try {
                    smsManager.sendMultipartTextMessage(
                        recipient,
                        null,
                        parts,
                        sentIntents,
                        deliveredIntents
                    )
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, "Invalid phone number or message", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    throw e
                }
            }

            // Write to database immediately if we're the default SMS app
            // This ensures the message appears in the UI even if broadcast receiver doesn't fire
            if (isDefaultSmsApp) {
                SmsOperations.writeSentSms(context, recipient, message, System.currentTimeMillis(), subscriptionId)
            }

            Toast.makeText(context, "Sending message...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
