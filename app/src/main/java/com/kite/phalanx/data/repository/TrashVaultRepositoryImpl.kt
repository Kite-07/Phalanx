package com.kite.phalanx.data.repository

import com.kite.phalanx.data.source.local.dao.TrashedMessageDao
import com.kite.phalanx.data.source.local.entity.TrashedMessageEntity
import com.kite.phalanx.domain.repository.TrashVaultRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Implementation of trash vault repository.
 *
 * Phase 3 - Safety Rails: Trash Vault
 * Handles soft-delete with 30-day retention period.
 */
class TrashVaultRepositoryImpl @Inject constructor(
    private val trashedMessageDao: TrashedMessageDao
) : TrashVaultRepository {

    companion object {
        private const val RETENTION_DAYS = 30
        private val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
    }

    override suspend fun moveToTrash(
        messageId: Long,
        sender: String,
        body: String,
        timestamp: Long,
        threadId: Long,
        isMms: Boolean,
        subscriptionId: Int,
        threadGroupId: String?
    ) {
        val now = System.currentTimeMillis()
        val trashedMessage = TrashedMessageEntity(
            messageId = messageId,
            sender = sender,
            body = body,
            timestamp = timestamp,
            trashedAt = now,
            expiresAt = now + RETENTION_MILLIS,
            originalThreadId = threadId,
            isMms = isMms,
            subscriptionId = subscriptionId,
            threadGroupId = threadGroupId
        )
        trashedMessageDao.insert(trashedMessage)
    }

    override suspend fun restoreMessage(messageId: Long): TrashedMessageEntity? {
        val trashedMessage = trashedMessageDao.getTrashedMessage(messageId)
        if (trashedMessage != null) {
            // Remove from trash vault after successful restore
            trashedMessageDao.delete(messageId)
        }
        return trashedMessage
    }

    override fun getAllTrashedMessages(): Flow<List<TrashedMessageEntity>> {
        return trashedMessageDao.getAllTrashedMessages()
    }

    override suspend fun getAllTrashedMessagesList(): List<TrashedMessageEntity> {
        return trashedMessageDao.getAllTrashedMessagesList()
    }

    override suspend fun purgeExpiredMessages(): Int {
        val now = System.currentTimeMillis()
        return trashedMessageDao.deleteExpiredMessages(now)
    }

    override suspend fun permanentlyDelete(messageId: Long) {
        trashedMessageDao.delete(messageId)
    }

    override suspend fun getCount(): Int {
        return trashedMessageDao.getCount()
    }

    override suspend fun isInTrash(messageId: Long): Boolean {
        return trashedMessageDao.getTrashedMessage(messageId) != null
    }

    override suspend fun getMessagesByThreadGroup(threadGroupId: String): List<TrashedMessageEntity> {
        return trashedMessageDao.getMessagesByThreadGroup(threadGroupId)
    }

    override suspend fun restoreThreadGroup(threadGroupId: String): List<TrashedMessageEntity> {
        val messages = trashedMessageDao.getMessagesByThreadGroup(threadGroupId)
        if (messages.isNotEmpty()) {
            // Remove all messages in the group from trash vault after retrieval
            trashedMessageDao.deleteThreadGroup(threadGroupId)
        }
        return messages
    }

    override suspend fun permanentlyDeleteThreadGroup(threadGroupId: String): Int {
        return trashedMessageDao.deleteThreadGroup(threadGroupId)
    }
}
