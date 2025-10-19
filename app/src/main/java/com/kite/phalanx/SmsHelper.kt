package com.kite.phalanx

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
        // Check for SEND_SMS permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)

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

            context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT), Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED), Context.RECEIVER_NOT_EXPORTED)

            // Split message if it's too long (standard SMS is 160 characters)
            val parts = smsManager.divideMessage(message)

            if (parts.size == 1) {
                // Send single SMS
                smsManager.sendTextMessage(
                    recipient,
                    null,
                    message,
                    sentIntent,
                    deliveredIntent
                )
            } else {
                // Send multipart SMS
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }
                smsManager.sendMultipartTextMessage(
                    recipient,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents
                )
            }

            Log.d("SmsHelper", "SMS sending initiated to $recipient")
        } catch (e: Exception) {
            Log.e("SmsHelper", "Failed to send SMS", e)
            Toast.makeText(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
