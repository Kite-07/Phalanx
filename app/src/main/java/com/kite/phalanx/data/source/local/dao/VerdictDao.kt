package com.kite.phalanx.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kite.phalanx.data.source.local.entity.VerdictEntity

/**
 * DAO for message verdicts.
 */
@Dao
interface VerdictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(verdict: VerdictEntity)

    @Query("SELECT * FROM verdicts WHERE messageId = :messageId LIMIT 1")
    suspend fun getVerdictForMessage(messageId: Long): VerdictEntity?

    @Query("DELETE FROM verdicts WHERE messageId = :messageId")
    suspend fun deleteVerdictForMessage(messageId: Long)

    @Query("DELETE FROM verdicts")
    suspend fun deleteAll()
}
