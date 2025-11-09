package com.kite.phalanx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for notification actions (Mark as Read, Reply, Block, Delete)
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val sender = intent.getStringExtra(NotificationHelper.EXTRA_SENDER) ?: return

        when (intent.action) {
            NotificationHelper.ACTION_MARK_AS_READ -> {
                // Mark conversation as read
                SmsOperations.markAsRead(context, sender)
                // Cancel the notification
                NotificationHelper.cancelNotification(context, sender)
            }

            NotificationHelper.ACTION_REPLY -> {
                // Get reply text from RemoteInput
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

                if (!replyText.isNullOrBlank()) {
                    // Send SMS
                    SmsHelper.sendSms(context, sender, replyText)
                    // Cancel the notification
                    NotificationHelper.cancelNotification(context, sender)
                }
            }

            NotificationHelper.ACTION_BLOCK_SENDER -> {
                // Block the sender (add to blocked numbers)
                val messageTimestamp = intent.getLongExtra(NotificationHelper.EXTRA_MESSAGE_TIMESTAMP, 0L)
                SmsOperations.blockNumber(context, sender)
                // Cancel all notifications from this sender
                NotificationHelper.cancelNotification(context, sender)
                // Also cancel the security threat notification
                if (messageTimestamp != 0L) {
                    val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
                    notificationManager.cancel((sender + messageTimestamp).hashCode())
                }
            }

            NotificationHelper.ACTION_DELETE_MESSAGE -> {
                // Delete the specific message (suspend function - use coroutine)
                val messageTimestamp = intent.getLongExtra(NotificationHelper.EXTRA_MESSAGE_TIMESTAMP, 0L)
                if (messageTimestamp != 0L) {
                    // Use goAsync() to extend receiver lifetime for async operation
                    val pendingResult = goAsync()

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Call suspend deleteMessage without trash vault (hard delete from notification)
                            SmsOperations.deleteMessage(context, sender, messageTimestamp, moveToTrashUseCase = null)
                            // Cancel the security threat notification
                            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
                            notificationManager.cancel((sender + messageTimestamp).hashCode())
                        } finally {
                            // Finish the async operation
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
