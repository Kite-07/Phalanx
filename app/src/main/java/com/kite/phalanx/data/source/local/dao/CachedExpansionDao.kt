package com.kite.phalanx.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kite.phalanx.data.source.local.entity.CachedExpansionEntity

/**
 * DAO for cached URL expansions.
 */
@Dao
interface CachedExpansionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expansion: CachedExpansionEntity)

    @Query("SELECT * FROM cached_expansions WHERE originalUrl = :url LIMIT 1")
    suspend fun getByOriginalUrl(url: String): CachedExpansionEntity?

    @Query("DELETE FROM cached_expansions WHERE expiresAt < :timestamp")
    suspend fun deleteExpired(timestamp: Long): Int

    @Query("DELETE FROM cached_expansions")
    suspend fun deleteAll()
}
