package com.kite.phalanx.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kite.phalanx.data.source.local.entity.SignalEntity

/**
 * DAO for risk signals.
 */
@Dao
interface SignalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signal: SignalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(signals: List<SignalEntity>)

    @Query("SELECT * FROM signals WHERE messageId = :messageId")
    suspend fun getSignalsForMessage(messageId: Long): List<SignalEntity>

    @Query("DELETE FROM signals WHERE messageId = :messageId")
    suspend fun deleteSignalsForMessage(messageId: Long)

    @Query("DELETE FROM signals")
    suspend fun deleteAll()
}
