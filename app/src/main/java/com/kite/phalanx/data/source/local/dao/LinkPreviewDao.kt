package com.kite.phalanx.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kite.phalanx.data.source.local.entity.LinkPreviewEntity

/**
 * DAO for cached link previews.
 *
 * Per PRD Phase 5: Cache link previews to avoid repeated network requests.
 */
@Dao
interface LinkPreviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preview: LinkPreviewEntity)

    @Query("SELECT * FROM link_previews WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): LinkPreviewEntity?

    @Query("DELETE FROM link_previews WHERE expiresAt < :timestamp")
    suspend fun deleteExpired(timestamp: Long): Int

    @Query("DELETE FROM link_previews")
    suspend fun deleteAll()
}
