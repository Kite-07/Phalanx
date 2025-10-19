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
     * Writes a sent SMS to the system database
     * This is required for default SMS apps to persist sent messages
     */
    fun writeSentSms(
        context: Context,
        recipient: String,
        messageBody: String,
        timestamp: Long
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, messageBody)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 1) // Sent messages are read
                put(Telephony.Sms.SEEN, 1) // Sent messages are seen
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }

            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            Log.d("SmsOperations", "Wrote sent SMS to database: $uri")
            uri
        } catch (e: Exception) {
            Log.e("SmsOperations", "Failed to write sent SMS", e)
            null
        }
    }

    /**
     * Deletes all messages from a specific sender/thread
     */
    fun deleteThread(context: Context, sender: String): Boolean {
        return try {
            Log.d("SmsOperations", "Attempting to delete thread for: $sender")

            // Check if app is default SMS app using both methods
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            val currentPackage = context.packageName
            Log.d("SmsOperations", "Default SMS package: $defaultSmsPackage, Current package: $currentPackage")

            // On Android 10+, also check via RoleManager
            val isDefaultViaRole = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    val isDefault = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false
                    Log.d("SmsOperations", "RoleManager check - is default SMS: $isDefault")
                    isDefault
                } catch (e: Exception) {
                    Log.e("SmsOperations", "Error checking SMS role", e)
                    false
                }
            } else {
                false
            }

            val isDefaultSmsApp = (defaultSmsPackage == currentPackage) || isDefaultViaRole

            if (!isDefaultSmsApp) {
                Log.w("SmsOperations", "Not default SMS app. Default is: $defaultSmsPackage, Current: $currentPackage, Role: $isDefaultViaRole")
                Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app first", Toast.LENGTH_LONG).show()
                return false
            }

            Log.d("SmsOperations", "App is default SMS app, proceeding with delete")

            // Try deleting with exact address match
            var deleted = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(sender)
            )

            Log.d("SmsOperations", "Exact match deleted: $deleted messages")

            // If no messages deleted, try with LIKE to handle different number formats
            if (deleted == 0) {
                // Get all message IDs for this sender using a query
                val idsToDelete = mutableListOf<String>()
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
                    val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)

                    while (cursor.moveToNext()) {
                        val address = cursor.getString(addressIndex)
                        // Normalize both numbers for comparison (remove spaces, dashes, etc)
                        val normalizedAddress = address.replace(Regex("[^0-9+]"), "")
                        val normalizedSender = sender.replace(Regex("[^0-9+]"), "")

                        if (normalizedAddress.endsWith(normalizedSender) ||
                            normalizedSender.endsWith(normalizedAddress)) {
                            idsToDelete.add(cursor.getString(idIndex))
                        }
                    }
                }

                // Delete by IDs
                idsToDelete.forEach { id ->
                    context.contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${Telephony.Sms._ID} = ?",
                        arrayOf(id)
                    )
                }

                deleted = idsToDelete.size
                Log.d("SmsOperations", "Normalized match deleted: $deleted messages")
            }

            if (deleted > 0) {
                Toast.makeText(context, "Deleted $deleted messages", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No messages found to delete", Toast.LENGTH_SHORT).show()
            }

            deleted > 0
        } catch (e: SecurityException) {
            Log.e("SmsOperations", "Security exception deleting messages - app may not be default SMS app", e)
            Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app", Toast.LENGTH_LONG).show()
            false
        } catch (e: Exception) {
            Log.e("SmsOperations", "Failed to delete messages", e)
            Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Deletes a single message by timestamp and sender
     */
    fun deleteMessage(context: Context, sender: String, timestamp: Long): Boolean {
        return try {
            // Check if app is default SMS app using both methods
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            val currentPackage = context.packageName

            // On Android 10+, also check via RoleManager
            val isDefaultViaRole = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false
                } catch (e: Exception) {
                    Log.e("SmsOperations", "Error checking SMS role", e)
                    false
                }
            } else {
                false
            }

            val isDefaultSmsApp = (defaultSmsPackage == currentPackage) || isDefaultViaRole

            if (!isDefaultSmsApp) {
                Log.w("SmsOperations", "Cannot delete message - not default SMS app")
                Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app first", Toast.LENGTH_LONG).show()
                return false
            }

            val deleted = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} = ?",
                arrayOf(sender, timestamp.toString())
            )
            if (deleted > 0) {
                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
            }
            deleted > 0
        } catch (e: SecurityException) {
            Log.e("SmsOperations", "Security exception deleting message - app may not be default SMS app", e)
            Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app", Toast.LENGTH_LONG).show()
            false
        } catch (e: Exception) {
            Log.e("SmsOperations", "Failed to delete message", e)
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

    /**
     * Blocks a phone number by adding it to BlockedNumbers provider
     * Requires WRITE_BLOCKED_NUMBERS permission on Android N+
     */
    fun blockNumber(context: Context, phoneNumber: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val values = ContentValues().apply {
                    put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber)
                }
                val uri = context.contentResolver.insert(
                    android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    values
                )
                if (uri != null) {
                    Log.d("SmsOperations", "Blocked number: $phoneNumber")
                    Toast.makeText(context, "Number blocked", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Log.e("SmsOperations", "Failed to block number: $phoneNumber")
                    Toast.makeText(context, "Failed to block number", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                Toast.makeText(context, "Blocking requires Android 7.0+", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: SecurityException) {
            Log.e("SmsOperations", "Permission denied to block number", e)
            Toast.makeText(context, "Permission required to block numbers", Toast.LENGTH_LONG).show()
            false
        } catch (e: Exception) {
            Log.e("SmsOperations", "Error blocking number", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Unblocks a phone number
     */
    fun unblockNumber(context: Context, phoneNumber: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val deleted = context.contentResolver.delete(
                    android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    "${android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                    arrayOf(phoneNumber)
                )
                if (deleted > 0) {
                    Log.d("SmsOperations", "Unblocked number: $phoneNumber")
                    Toast.makeText(context, "Number unblocked", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Log.w("SmsOperations", "Number not found in blocked list: $phoneNumber")
                    Toast.makeText(context, "Number was not blocked", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SmsOperations", "Error unblocking number", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Checks if a phone number is blocked
     */
    fun isNumberBlocked(context: Context, phoneNumber: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.contentResolver.query(
                    android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    arrayOf(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ID),
                    "${android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                    arrayOf(phoneNumber),
                    null
                )?.use { cursor ->
                    cursor.count > 0
                } ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SmsOperations", "Error checking if number is blocked", e)
            false
        }
    }
}
