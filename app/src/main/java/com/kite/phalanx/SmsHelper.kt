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
    private const val ACTION_SMS_SENT = "com.kite.phalanx.SMS_SENT"
    private const val ACTION_SMS_DELIVERED = "com.kite.phalanx.SMS_DELIVERED"

    /**
     * Sends an SMS message to the specified recipient
     */
    fun sendSms(context: Context, recipient: String, message: String) {
        Log.d("SmsHelper", "sendSms called - recipient: $recipient, message length: ${message.length}")

        // Check if we're the default SMS app
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        val isDefaultViaPackage = defaultSmsPackage == context.packageName

        // On Android 10+, also check via RoleManager
        val isDefaultViaRole = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false
            } catch (e: Exception) {
                Log.e("SmsHelper", "Error checking SMS role", e)
                false
            }
        } else {
            false
        }

        val isDefaultSmsApp = isDefaultViaPackage || isDefaultViaRole
        Log.d("SmsHelper", "Default SMS package: $defaultSmsPackage, This package: ${context.packageName}")
        Log.d("SmsHelper", "Is default via package: $isDefaultViaPackage, via role: $isDefaultViaRole, combined: $isDefaultSmsApp")

        // Check for SEND_SMS permission
        val hasSendPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        Log.d("SmsHelper", "Has SEND_SMS permission: $hasSendPermission")

        if (!hasSendPermission) {
            Toast.makeText(context, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            Log.e("SmsHelper", "SEND_SMS permission not granted")
            return
        }

        try {
            // Get SmsManager - use default instance for compatibility
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - use system service
                context.getSystemService(SmsManager::class.java)
            } else {
                // Android 11 and below - use deprecated getDefault()
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                Log.e("SmsHelper", "SmsManager is null")
                Toast.makeText(context, "SMS service unavailable", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d("SmsHelper", "SmsManager obtained successfully, preparing to send SMS")

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
                            Log.d("SmsHelper", "Message sent successfully")

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
                                Log.d("SmsHelper", "Writing sent SMS to database")
                                SmsOperations.writeSentSms(context, recipient, message, System.currentTimeMillis())
                            } else {
                                Log.d("SmsHelper", "Not default SMS app, skipping database write")
                            }
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            Toast.makeText(context, "Failed to send SMS", Toast.LENGTH_SHORT).show()
                            Log.e("SmsHelper", "Failed to send SMS: GENERIC_FAILURE")
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            Toast.makeText(context, "No service", Toast.LENGTH_SHORT).show()
                            Log.e("SmsHelper", "Failed to send SMS: NO_SERVICE")
                        }
                        SmsManager.RESULT_ERROR_NULL_PDU -> {
                            Toast.makeText(context, "Null PDU", Toast.LENGTH_SHORT).show()
                            Log.e("SmsHelper", "Failed to send SMS: NULL_PDU")
                        }
                        SmsManager.RESULT_ERROR_RADIO_OFF -> {
                            Toast.makeText(context, "Radio off", Toast.LENGTH_SHORT).show()
                            Log.e("SmsHelper", "Failed to send SMS: RADIO_OFF")
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
            Log.d("SmsHelper", "Message split into ${parts.size} parts")

            if (parts.size == 1) {
                // Send single SMS
                Log.d("SmsHelper", "Sending single SMS to $recipient")
                try {
                    smsManager.sendTextMessage(
                        recipient,
                        null,
                        message,
                        sentIntent,
                        deliveredIntent
                    )
                    Log.d("SmsHelper", "sendTextMessage called successfully")
                } catch (e: IllegalArgumentException) {
                    Log.e("SmsHelper", "IllegalArgumentException sending SMS", e)
                    Toast.makeText(context, "Invalid phone number or message", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    Log.e("SmsHelper", "Exception in sendTextMessage", e)
                    throw e
                }
            } else {
                // Send multipart SMS
                Log.d("SmsHelper", "Sending multipart SMS (${parts.size} parts) to $recipient")
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
                    Log.d("SmsHelper", "sendMultipartTextMessage called successfully")
                } catch (e: IllegalArgumentException) {
                    Log.e("SmsHelper", "IllegalArgumentException sending multipart SMS", e)
                    Toast.makeText(context, "Invalid phone number or message", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    Log.e("SmsHelper", "Exception in sendMultipartTextMessage", e)
                    throw e
                }
            }

            // Write to database immediately if we're the default SMS app
            // This ensures the message appears in the UI even if broadcast receiver doesn't fire
            if (isDefaultSmsApp) {
                Log.d("SmsHelper", "Writing sent SMS to database immediately")
                SmsOperations.writeSentSms(context, recipient, message, System.currentTimeMillis())
            }

            Log.d("SmsHelper", "SMS sending initiated to $recipient")
            Toast.makeText(context, "Sending message...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SmsHelper", "Failed to send SMS", e)
            Toast.makeText(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
