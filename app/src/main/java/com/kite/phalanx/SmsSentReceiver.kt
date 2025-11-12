package com.kite.phalanx

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import timber.log.Timber

/**
 * Receiver for SMS sent and delivery status updates
 */
class SmsSentReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SMS_SENT = "com.kite.phalanx.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.kite.phalanx.SMS_DELIVERED"
        const val EXTRA_MESSAGE_URI = "message_uri"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("onReceive called - action: ${intent?.action}, resultCode: $resultCode")

        if (context == null || intent == null) {
            Timber.e("Context or intent is null")
            return
        }

        val messageUriString = intent.getStringExtra(EXTRA_MESSAGE_URI)
        if (messageUriString == null) {
            Timber.e("Message URI not found in intent")
            return
        }

        val messageUri = Uri.parse(messageUriString)
        val messageId = messageUri.lastPathSegment?.toLongOrNull()
        if (messageId == null) {
            Timber.e("Invalid message URI: $messageUriString")
            return
        }

        when (intent.action) {
            ACTION_SMS_SENT -> handleSmsSent(context, messageUri, resultCode)
            ACTION_SMS_DELIVERED -> handleSmsDelivered(context, messageUri, resultCode)
            else -> Timber.w("Unexpected action: ${intent.action}")
        }
    }

    private fun handleSmsSent(context: Context, messageUri: Uri, resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Timber.d("SMS sent successfully, URI: $messageUri")
                // Update message type to SENT and status to PENDING (awaiting delivery report)
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        put(Telephony.Sms.STATUS, 32) // STATUS_PENDING - sent but not yet delivered
                    }
                    val updated = context.contentResolver.update(messageUri, values, null, null)
                    Timber.d("Updated message to SENT status (rows: $updated)")
                } catch (e: Exception) {
                    Timber.e(e, "Error updating sent message")
                }
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Timber.e("SMS send failed: Generic failure, URI: $messageUri")
                updateMessageType(context, messageUri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                Toast.makeText(context, "Failed to send SMS", Toast.LENGTH_SHORT).show()
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Timber.e("SMS send failed: No service, URI: $messageUri")
                updateMessageType(context, messageUri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                Toast.makeText(context, "Failed to send SMS: No service", Toast.LENGTH_SHORT).show()
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Timber.e("SMS send failed: Null PDU, URI: $messageUri")
                updateMessageType(context, messageUri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                Toast.makeText(context, "Failed to send SMS: Invalid message", Toast.LENGTH_SHORT).show()
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Timber.e("SMS send failed: Radio off, URI: $messageUri")
                updateMessageType(context, messageUri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                Toast.makeText(context, "Failed to send SMS: Radio off", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Timber.e("SMS send failed: Unknown error code $resultCode, URI: $messageUri")
                updateMessageType(context, messageUri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                Toast.makeText(context, "Failed to send SMS", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSmsDelivered(context: Context, messageUri: Uri, resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Timber.d("SMS delivered successfully, URI: $messageUri")
                // Update delivery status to delivered (STATUS_COMPLETE = 0)
                updateDeliveryStatus(context, messageUri, 0)
            }
            Activity.RESULT_CANCELED -> {
                Timber.e("SMS delivery failed, URI: $messageUri")
                // Update delivery status to failed (STATUS_FAILED = 64)
                updateDeliveryStatus(context, messageUri, 64)
            }
            else -> {
                Timber.w("SMS delivery status unknown: $resultCode, URI: $messageUri")
            }
        }
    }

    private fun updateMessageType(context: Context, messageUri: Uri, messageType: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE, messageType)
            }
            val updated = context.contentResolver.update(messageUri, values, null, null)
            Timber.d("Updated message type to $messageType (rows: $updated)")
        } catch (e: Exception) {
            Timber.e(e, "Error updating message type")
        }
    }

    private fun updateDeliveryStatus(context: Context, messageUri: Uri, status: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.STATUS, status)
            }
            val updated = context.contentResolver.update(messageUri, values, null, null)
            Timber.d("Updated delivery status to $status (rows: $updated)")
        } catch (e: Exception) {
            Timber.e(e, "Error updating delivery status")
        }
    }
}
