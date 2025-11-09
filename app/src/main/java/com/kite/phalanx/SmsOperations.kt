package com.kite.phalanx

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
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
            uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes a sent SMS to the system database
     * This is required for default SMS apps to persist sent messages
     * Initially sets message type to OUTBOX - SmsSentReceiver will update it to SENT after successful send
     */
    fun writeSentSms(
        context: Context,
        recipient: String,
        messageBody: String,
        timestamp: Long,
        subscriptionId: Int = -1
    ): Uri? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, messageBody)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 1) // Sent messages are read
                put(Telephony.Sms.SEEN, 1) // Sent messages are seen
                // Start with OUTBOX type - SmsSentReceiver will update to SENT after successful send
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                // Set initial status to pending (32 = STATUS_PENDING)
                put(Telephony.Sms.STATUS, 32)
                if (subscriptionId != -1) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            }

            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes all messages from a specific sender/thread
     *
     * Phase 3 Integration: Supports soft-delete via trash vault (30-day retention).
     * - If moveToTrashUseCase is provided: moves all messages to trash (soft delete)
     * - If moveToTrashUseCase is null: performs immediate permanent deletion (backward compatible)
     *
     * @param context Application context
     * @param sender Message sender phone number
     * @param moveToTrashUseCase Optional use case for soft-delete. If null, performs hard delete.
     * @return true if deleted/moved to trash successfully
     */
    suspend fun deleteThread(
        context: Context,
        sender: String,
        moveToTrashUseCase: com.kite.phalanx.domain.usecase.MoveToTrashUseCase? = null
    ): Boolean {
        return try {
            // Check if app is default SMS app using both methods
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            val currentPackage = context.packageName

            // On Android 10+, also check via RoleManager
            val isDefaultViaRole = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    val isDefault = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false
                    isDefault
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }

            val isDefaultSmsApp = (defaultSmsPackage == currentPackage) || isDefaultViaRole

            if (!isDefaultSmsApp) {
                Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app first", Toast.LENGTH_LONG).show()
                return false
            }

            // If trash vault use case provided, perform soft delete for all messages
            if (moveToTrashUseCase != null) {
                // Generate a unique thread group ID for this deletion
                val threadGroupId = java.util.UUID.randomUUID().toString()

                // Query all messages for this sender
                val messagesToTrash = mutableListOf<Pair<Long, android.content.ContentValues>>()

                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.SUBSCRIPTION_ID
                )

                // First try exact match with the sender
                var selection = "${Telephony.Sms.ADDRESS} = ?"
                var selectionArgs = arrayOf(sender)

                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                    val subIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)

                    while (cursor.moveToNext()) {
                        val messageId = cursor.getLong(idIndex)
                        val values = android.content.ContentValues().apply {
                            put("body", cursor.getString(bodyIndex) ?: "")
                            put("date", cursor.getLong(dateIndex))
                            put("thread_id", cursor.getLong(threadIdIndex))
                            put("subscription_id", cursor.getInt(subIdIndex))
                        }
                        messagesToTrash.add(messageId to values)
                    }
                }

                // If no exact matches found, try normalized matching as fallback
                // This handles cases where phone number format varies (e.g., +1234567890 vs 234567890)
                if (messagesToTrash.isEmpty()) {
                    val normalizedSender = sender.replace(Regex("[^0-9]"), "").takeLast(10)

                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        projection,
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                        val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                        val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                        val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                        val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                        val subIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)

                        while (cursor.moveToNext()) {
                            val address = cursor.getString(addressIndex) ?: continue
                            val normalizedAddress = address.replace(Regex("[^0-9]"), "").takeLast(10)

                            // Match on last 10 digits (standard phone number length)
                            if (normalizedAddress == normalizedSender && normalizedSender.length >= 7) {
                                val messageId = cursor.getLong(idIndex)
                                val values = android.content.ContentValues().apply {
                                    put("body", cursor.getString(bodyIndex) ?: "")
                                    put("date", cursor.getLong(dateIndex))
                                    put("thread_id", cursor.getLong(threadIdIndex))
                                    put("subscription_id", cursor.getInt(subIdIndex))
                                }
                                messagesToTrash.add(messageId to values)
                            }
                        }
                    }
                }

                // Move all messages to trash with the same threadGroupId
                var movedCount = 0
                messagesToTrash.forEach { (messageId, values) ->
                    try {
                        moveToTrashUseCase.execute(
                            messageId = messageId,
                            sender = sender,
                            body = values.getAsString("body") ?: "",
                            timestamp = values.getAsLong("date") ?: 0L,
                            threadId = values.getAsLong("thread_id") ?: 0L,
                            isMms = false,
                            subscriptionId = values.getAsInteger("subscription_id") ?: -1,
                            threadGroupId = threadGroupId // All messages share the same group ID
                        )

                        // After moving to trash, perform hard delete from SMS provider
                        context.contentResolver.delete(
                            Telephony.Sms.CONTENT_URI,
                            "${Telephony.Sms._ID} = ?",
                            arrayOf(messageId.toString())
                        )
                        movedCount++
                    } catch (e: Exception) {
                        // Continue with next message even if one fails
                    }
                }

                if (movedCount > 0) {
                    val message = if (movedCount == 1) {
                        "Moved 1 message to trash"
                    } else {
                        "Moved conversation ($movedCount messages) to trash"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No messages found to delete", Toast.LENGTH_SHORT).show()
                }

                return movedCount > 0
            }

            // Fallback: Hard delete (backward compatible)
            // Try deleting with exact address match
            var deleted = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(sender)
            )

            // If no messages deleted, try normalized matching to handle different number formats
            if (deleted == 0) {
                val normalizedSender = sender.replace(Regex("[^0-9]"), "").takeLast(10)

                // Only attempt fallback if we have a valid phone number
                if (normalizedSender.length >= 7) {
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
                            val address = cursor.getString(addressIndex) ?: continue
                            val normalizedAddress = address.replace(Regex("[^0-9]"), "").takeLast(10)

                            // Match on last 10 digits (standard phone number length)
                            if (normalizedAddress == normalizedSender) {
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
                }
            }

            if (deleted > 0) {
                Toast.makeText(context, "Deleted $deleted messages", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No messages found to delete", Toast.LENGTH_SHORT).show()
            }

            deleted > 0
        } catch (e: SecurityException) {
            Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app", Toast.LENGTH_LONG).show()
            false
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Deletes a single message by timestamp and sender.
     *
     * Phase 3 Integration: Supports soft-delete via trash vault (30-day retention).
     * - If moveToTrashUseCase is provided: queries message details and moves to trash (soft delete)
     * - If moveToTrashUseCase is null: performs immediate permanent deletion (backward compatible)
     *
     * @param context Application context
     * @param sender Message sender phone number
     * @param timestamp Message timestamp in milliseconds
     * @param moveToTrashUseCase Optional use case for soft-delete. If null, performs hard delete.
     * @return true if deleted/moved to trash successfully
     */
    suspend fun deleteMessage(
        context: Context,
        sender: String,
        timestamp: Long,
        moveToTrashUseCase: com.kite.phalanx.domain.usecase.MoveToTrashUseCase? = null
    ): Boolean {
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
                    false
                }
            } else {
                false
            }

            val isDefaultSmsApp = (defaultSmsPackage == currentPackage) || isDefaultViaRole

            if (!isDefaultSmsApp) {
                Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app first", Toast.LENGTH_LONG).show()
                return false
            }

            // If trash vault use case provided, perform soft delete
            if (moveToTrashUseCase != null) {
                // Query the message to get full details for trash vault
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.SUBSCRIPTION_ID
                )

                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} = ?",
                    arrayOf(sender, timestamp.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val messageId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                        val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                        val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                        val subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID))

                        // Move to trash vault (soft delete)
                        moveToTrashUseCase.execute(
                            messageId = messageId,
                            sender = sender,
                            body = body,
                            timestamp = timestamp,
                            threadId = threadId,
                            isMms = false,
                            subscriptionId = subscriptionId
                        )

                        // After moving to trash, perform hard delete from SMS provider
                        context.contentResolver.delete(
                            Telephony.Sms.CONTENT_URI,
                            "${Telephony.Sms._ID} = ?",
                            arrayOf(messageId.toString())
                        )

                        Toast.makeText(context, "Message moved to trash", Toast.LENGTH_SHORT).show()
                        return true
                    } else {
                        Toast.makeText(context, "Message not found", Toast.LENGTH_SHORT).show()
                        return false
                    }
                }

                return false
            }

            // Fallback: Hard delete (backward compatible)
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
            Toast.makeText(context, "Cannot delete: Set Phalanx as default SMS app", Toast.LENGTH_LONG).show()
            false
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
     * Marks messages as seen
     */
    fun markAsSeen(context: Context, sender: String): Boolean {
        return try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.SEEN, 1)
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
                    Toast.makeText(context, "Number blocked", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Toast.makeText(context, "Failed to block number", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                Toast.makeText(context, "Blocking requires Android 7.0+", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Permission required to block numbers", Toast.LENGTH_LONG).show()
            false
        } catch (e: Exception) {
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
                    Toast.makeText(context, "Number unblocked", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Toast.makeText(context, "Number was not blocked", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
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
            false
        }
    }
}
