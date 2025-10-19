package com.kite.phalanx

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.widget.Toast

/**
 * Helper object for SMS operations like delete, mark read/unread
 */
object SmsOperations {

    /**
     * Writes an incoming SMS to the system database
     * This is required for default SMS apps to persist received messages
     */
    fun writeIncomingSms(
        context: Context,
        sender: String,
        messageBody: String,
        timestamp: Long
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, messageBody)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 0) // Mark as unread
                put(Telephony.Sms.SEEN, 0) // Mark as unseen
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }

            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            Log.d("SmsOperations", "Wrote incoming SMS to database: $uri")
            uri
        } catch (e: Exception) {
            Log.e("SmsOperations", "Failed to write incoming SMS", e)
            null
        }
    }

    /**
     * Deletes all messages from a specific sender/thread
     */
    fun deleteThread(context: Context, sender: String): Boolean {
        return try {
            val deleted = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(sender)
            )
            Toast.makeText(context, "Deleted $deleted messages", Toast.LENGTH_SHORT).show()
            deleted > 0
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Deletes a single message by timestamp and sender
     */
    fun deleteMessage(context: Context, sender: String, timestamp: Long): Boolean {
        return try {
            val deleted = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} = ?",
                arrayOf(sender, timestamp.toString())
            )
            if (deleted > 0) {
                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
            }
            deleted > 0
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Marks a message as read
     */
    fun markAsRead(context: Context, sender: String): Boolean {
        return try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            val updated = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(sender)
            )
            updated > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Marks a message as unread
     */
    fun markAsUnread(context: Context, sender: String): Boolean {
        return try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 0)
            }
            val updated = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(sender)
            )
            updated > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets unread message count for a sender
     */
    fun getUnreadCount(context: Context, sender: String): Int {
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(sender),
                null
            )?.use { cursor ->
                cursor.count
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
