package com.kite.phalanx.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kite.phalanx.data.source.local.dao.CachedExpansionDao
import com.kite.phalanx.data.source.local.dao.SignalDao
import com.kite.phalanx.data.source.local.dao.VerdictDao
import com.kite.phalanx.data.source.local.entity.CachedExpansionEntity
import com.kite.phalanx.data.source.local.entity.SignalEntity
import com.kite.phalanx.data.source.local.entity.VerdictEntity

/**
 * Room database for Phalanx Phase 1 security features.
 * Stores cached URL expansions, risk signals, and verdicts.
 */
@Database(
    entities = [
        CachedExpansionEntity::class,
        SignalEntity::class,
        VerdictEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedExpansionDao(): CachedExpansionDao
    abstract fun signalDao(): SignalDao
    abstract fun verdictDao(): VerdictDao

    companion object {
        const val DATABASE_NAME = "phalanx_security.db"
    }
}
