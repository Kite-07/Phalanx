package com.kite.phalanx

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

/**
 * Helper class for managing notifications for incoming SMS messages.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "sms_messages"
    private const val CHANNEL_NAME = "SMS Messages"
    private const val NOTIFICATION_ID_BASE = 1000
    private const val GROUP_KEY = "com.kite.phalanx.SMS_GROUP"
    private const val SUMMARY_ID = 0

    const val ACTION_MARK_AS_READ = "com.kite.phalanx.MARK_AS_READ"
    const val ACTION_REPLY = "com.kite.phalanx.REPLY"
    const val EXTRA_SENDER = "sender"
    const val KEY_TEXT_REPLY = "key_text_reply"

    // Track active notifications by sender
    private val activeNotifications = mutableSetOf<String>()

    /**
     * Creates the notification channel for SMS messages (required for Android O+)
     */
    suspend fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Get user preference for bypassing DND
            val bypassDnd = AppPreferences.getBypassDnd(context)

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming SMS messages"
                enableVibration(true)
                enableLights(true)

                // Only set bypass DND if user has enabled it and granted permission
                if (bypassDnd) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        setBypassDnd(true)
                    }
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a notification for a new SMS message
     */
    suspend fun showMessageNotification(
        context: Context,
        sender: String,
        message: String,
        timestamp: Long
    ) {
        // Check if conversation is muted
        if (ConversationMutePreferences.isConversationMuted(context, sender)) {
            return // Don't show notification if conversation is muted
        }

        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // Create intent to open detail view when notification is tapped
        val intent = Intent(context, SmsDetailActivity::class.java).apply {
            putExtra("sender", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create Mark as Read action
        val markAsReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_AS_READ
            putExtra(EXTRA_SENDER, sender)
        }
        val markAsReadPendingIntent = PendingIntent.getBroadcast(
            context,
            sender.hashCode() + 1,
            markAsReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create Reply action with RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_SENDER, sender)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            sender.hashCode() + 2,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()

        // Load contact photo
        val largeIcon = loadContactPhoto(context, sender)

        // Add to active notifications
        activeNotifications.add(sender)

        // Build individual notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(largeIcon)
            .setContentTitle(sender)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(timestamp)
            .setShowWhen(true)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Mark as read",
                markAsReadPendingIntent
            )

        // Only add reply action if there's a single active notification
        // or all active notifications are from the same sender
        if (activeNotifications.size == 1 || activeNotifications.all { it == sender }) {
            notificationBuilder.addAction(replyAction)
        }

        // Add to group if multiple notifications exist
        if (activeNotifications.size > 1) {
            notificationBuilder.setGroup(GROUP_KEY)
        }

        val notification = notificationBuilder.build()

        // Show notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(sender.hashCode(), notification)

        // Show summary notification if there are multiple notifications
        if (activeNotifications.size > 1) {
            showSummaryNotification(context)
        }
    }

    /**
     * Shows a summary notification for grouped messages
     */
    private fun showSummaryNotification(context: Context) {
        val summaryText = if (activeNotifications.size == 1) {
            "1 new message"
        } else {
            "${activeNotifications.size} new messages"
        }

        // Create intent to open main activity
        val intent = Intent(context, SmsListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Phalanx")
            .setContentText(summaryText)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(SUMMARY_ID, summaryNotification)
    }

    /**
     * Loads contact photo for notification
     */
    private fun loadContactPhoto(context: Context, phoneNumber: String): Bitmap? {
        // Check if we have contact permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val photoUriString = it.getString(0)
                    if (photoUriString != null) {
                        val photoUri = Uri.parse(photoUriString)
                        context.contentResolver.openInputStream(photoUri)?.use { stream ->
                            return BitmapFactory.decodeStream(stream)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Cancels all notifications
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()
        activeNotifications.clear()
    }

    /**
     * Cancels notification for a specific sender
     */
    fun cancelNotification(context: Context, sender: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(sender.hashCode())
        activeNotifications.remove(sender)

        // Update or remove summary notification
        if (activeNotifications.isEmpty()) {
            notificationManager.cancel(SUMMARY_ID)
        } else if (activeNotifications.size > 1) {
            showSummaryNotification(context)
        } else {
            // Only one notification left, remove grouping
            notificationManager.cancel(SUMMARY_ID)
        }
    }
}
