package com.kite.phalanx.domain.repository

import com.kite.phalanx.data.source.local.entity.TrashedMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for trash vault operations.
 *
 * Phase 3 - Safety Rails: Trash Vault
 * Provides soft-delete, restore, and auto-purge functionality.
 */
interface TrashVaultRepository {

    /**
     * Move a message to trash vault (soft delete).
     *
     * @param messageId Original SMS provider message ID
     * @param sender Phone number or short code
     * @param body Message content
     * @param timestamp Original message timestamp
     * @param threadId Original conversation thread ID
     * @param isMms True if MMS, false if SMS
     * @param subscriptionId SIM subscription ID
     * @param threadGroupId Optional group ID for messages deleted together
     */
    suspend fun moveToTrash(
        messageId: Long,
        sender: String,
        body: String,
        timestamp: Long,
        threadId: Long = 0,
        isMms: Boolean = false,
        subscriptionId: Int = -1,
        threadGroupId: String? = null
    )

    /**
     * Restore a trashed message (returns metadata for SMS provider restoration).
     */
    suspend fun restoreMessage(messageId: Long): TrashedMessageEntity?

    /**
     * Get all trashed messages as a Flow for reactive UI.
     */
    fun getAllTrashedMessages(): Flow<List<TrashedMessageEntity>>

    /**
     * Get all trashed messages (one-time query).
     */
    suspend fun getAllTrashedMessagesList(): List<TrashedMessageEntity>

    /**
     * Purge expired messages (older than 30 days).
     * Returns count of purged messages.
     */
    suspend fun purgeExpiredMessages(): Int

    /**
     * Permanently delete a specific trashed message.
     */
    suspend fun permanentlyDelete(messageId: Long)

    /**
     * Get count of trashed messages.
     */
    suspend fun getCount(): Int

    /**
     * Check if a message is in trash.
     */
    suspend fun isInTrash(messageId: Long): Boolean

    /**
     * Get all messages in a thread group.
     */
    suspend fun getMessagesByThreadGroup(threadGroupId: String): List<TrashedMessageEntity>

    /**
     * Restore all messages in a thread group.
     */
    suspend fun restoreThreadGroup(threadGroupId: String): List<TrashedMessageEntity>

    /**
     * Permanently delete all messages in a thread group.
     */
    suspend fun permanentlyDeleteThreadGroup(threadGroupId: String): Int
}
