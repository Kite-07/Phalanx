package com.kite.phalanx.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kite.phalanx.data.source.local.dao.AllowBlockRuleDao
import com.kite.phalanx.data.source.local.dao.CachedExpansionDao
import com.kite.phalanx.data.source.local.dao.SignalDao
import com.kite.phalanx.data.source.local.dao.TrashedMessageDao
import com.kite.phalanx.data.source.local.dao.VerdictDao
import com.kite.phalanx.data.source.local.entity.AllowBlockRuleEntity
import com.kite.phalanx.data.source.local.entity.CachedExpansionEntity
import com.kite.phalanx.data.source.local.entity.SignalEntity
import com.kite.phalanx.data.source.local.entity.TrashedMessageEntity
import com.kite.phalanx.data.source.local.entity.VerdictEntity

/**
 * Room database for Phalanx security features.
 *
 * Phase 1: URL expansions, risk signals, and verdicts
 * Phase 3: Trash vault, allow/block lists (added in v2)
 * Phase 3 Enhancement: Thread grouping for trash vault (v2->v3)
 */
@Database(
    entities = [
        CachedExpansionEntity::class,
        SignalEntity::class,
        VerdictEntity::class,
        TrashedMessageEntity::class,
        AllowBlockRuleEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // Phase 1 DAOs
    abstract fun cachedExpansionDao(): CachedExpansionDao
    abstract fun signalDao(): SignalDao
    abstract fun verdictDao(): VerdictDao

    // Phase 3 DAOs
    abstract fun trashedMessageDao(): TrashedMessageDao
    abstract fun allowBlockRuleDao(): AllowBlockRuleDao

    companion object {
        const val DATABASE_NAME = "phalanx_security.db"

        /**
         * Migration from version 2 to 3: Add threadGroupId column to trashed_messages table.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add threadGroupId column (nullable) to trashed_messages table
                database.execSQL(
                    "ALTER TABLE trashed_messages ADD COLUMN threadGroupId TEXT DEFAULT NULL"
                )
                // Create index for efficient thread group queries
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_trashed_messages_threadGroupId ON trashed_messages(threadGroupId)"
                )
            }
        }
    }
}
