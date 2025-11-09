package com.kite.phalanx.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kite.phalanx.data.source.local.entity.TrashedMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for trash vault operations.
 *
 * Phase 3 - Safety Rails: Trash Vault
 * Supports soft-delete, restore, and auto-purge workflows.
 */
@Dao
interface TrashedMessageDao {

    /**
     * Move a message to trash vault.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trashedMessage: TrashedMessageEntity)

    /**
     * Get a specific trashed message by ID.
     */
    @Query("SELECT * FROM trashed_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getTrashedMessage(messageId: Long): TrashedMessageEntity?

    /**
     * Get all trashed messages ordered by most recently trashed.
     */
    @Query("SELECT * FROM trashed_messages ORDER BY trashedAt DESC")
    fun getAllTrashedMessages(): Flow<List<TrashedMessageEntity>>

    /**
     * Get all trashed messages (suspend version for one-time queries).
     */
    @Query("SELECT * FROM trashed_messages ORDER BY trashedAt DESC")
    suspend fun getAllTrashedMessagesList(): List<TrashedMessageEntity>

    /**
     * Get expired messages for auto-purge (expiresAt <= current time).
     */
    @Query("SELECT * FROM trashed_messages WHERE expiresAt <= :currentTimeMillis")
    suspend fun getExpiredMessages(currentTimeMillis: Long): List<TrashedMessageEntity>

    /**
     * Delete a specific trashed message (permanent delete after restore or manual purge).
     */
    @Query("DELETE FROM trashed_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)

    /**
     * Delete multiple messages by IDs (for batch purge).
     */
    @Query("DELETE FROM trashed_messages WHERE messageId IN (:messageIds)")
    suspend fun deleteByIds(messageIds: List<Long>)

    /**
     * Delete all expired messages (auto-purge cleanup).
     */
    @Query("DELETE FROM trashed_messages WHERE expiresAt <= :currentTimeMillis")
    suspend fun deleteExpiredMessages(currentTimeMillis: Long): Int

    /**
     * Delete all trashed messages (emergency clear/reset).
     */
    @Query("DELETE FROM trashed_messages")
    suspend fun deleteAll()

    /**
     * Count total trashed messages.
     */
    @Query("SELECT COUNT(*) FROM trashed_messages")
    suspend fun getCount(): Int

    /**
     * Get all messages in a thread group.
     */
    @Query("SELECT * FROM trashed_messages WHERE threadGroupId = :threadGroupId ORDER BY timestamp ASC")
    suspend fun getMessagesByThreadGroup(threadGroupId: String): List<TrashedMessageEntity>

    /**
     * Delete all messages in a thread group (permanent delete).
     */
    @Query("DELETE FROM trashed_messages WHERE threadGroupId = :threadGroupId")
    suspend fun deleteThreadGroup(threadGroupId: String): Int

    /**
     * Batch insert multiple messages (for thread deletion).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trashedMessages: List<TrashedMessageEntity>)
}
