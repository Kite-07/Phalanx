package com.kite.phalanx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Broadcast receiver that listens for incoming SMS messages.
 * When a new SMS is received, it triggers a notification.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // When the app is the default SMS app, it receives SMS_DELIVER instead of SMS_RECEIVED
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        // Process each SMS message
        for (smsMessage in messages) {
            val sender = smsMessage.displayOriginatingAddress ?: continue
            val messageBody = smsMessage.messageBody ?: ""
            val timestamp = smsMessage.timestampMillis

            // Write the message to the system SMS database
            // This is required for default SMS apps - the system no longer writes messages automatically
            SmsOperations.writeIncomingSms(
                context = context,
                sender = sender,
                messageBody = messageBody,
                timestamp = timestamp
            )

            // Show notification for new message
            NotificationHelper.showMessageNotification(
                context = context,
                sender = sender,
                message = messageBody,
                timestamp = timestamp
            )
        }
    }
}
