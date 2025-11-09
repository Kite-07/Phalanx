package com.kite.phalanx.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.kite.phalanx.data.source.local.entity.TrashedMessageEntity
import com.kite.phalanx.domain.repository.TrashVaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Use case for restoring a message from trash vault.
 *
 * Phase 3 - Safety Rails: Trash Vault
 * Restores message to original thread in SMS provider.
 */
class RestoreMessageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashVaultRepository: TrashVaultRepository
) {

    /**
     * Restore a trashed message back to SMS provider.
     *
     * @param messageId Message ID to restore
     * @return True if successfully restored, false if message not found in trash
     */
    suspend fun execute(messageId: Long): Boolean {
        // Get trashed message metadata
        val trashedMessage = trashVaultRepository.restoreMessage(messageId)
            ?: return false // Message not in trash

        // Restore to SMS provider
        return restoreToProvider(trashedMessage)
    }

    /**
     * Restore all messages in a thread group back to SMS provider.
     *
     * @param threadGroupId Thread group ID to restore
     * @return Number of messages successfully restored
     */
    suspend fun executeThreadGroup(threadGroupId: String): Int {
        // Get all trashed messages in the group
        val trashedMessages = trashVaultRepository.restoreThreadGroup(threadGroupId)
        if (trashedMessages.isEmpty()) return 0

        // Restore all messages to SMS provider
        var restoredCount = 0
        trashedMessages.forEach { message ->
            if (restoreToProvider(message)) {
                restoredCount++
            }
        }
        return restoredCount
    }

    /**
     * Write message back to SMS provider database.
     */
    private fun restoreToProvider(message: TrashedMessageEntity): Boolean {
        return try {
            val values = ContentValues().apply {
                // Note: Don't set _ID - let provider assign new ID
                put(Telephony.Sms.ADDRESS, message.sender)
                put(Telephony.Sms.BODY, message.body)
                put(Telephony.Sms.DATE, message.timestamp)
                put(Telephony.Sms.DATE_SENT, message.timestamp)
                put(Telephony.Sms.READ, 1) // Mark as read (already seen)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (message.subscriptionId != -1) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, message.subscriptionId)
                }
            }

            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            uri != null
        } catch (e: Exception) {
            android.util.Log.e("RestoreMessageUseCase", "Failed to restore message", e)
            false
        }
    }
}
