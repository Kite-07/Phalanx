package com.kite.phalanx.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kite.phalanx.data.source.local.dao.AllowBlockRuleDao
import com.kite.phalanx.data.source.local.dao.CachedExpansionDao
import com.kite.phalanx.data.source.local.dao.LinkPreviewDao
import com.kite.phalanx.data.source.local.dao.SignalDao
import com.kite.phalanx.data.source.local.dao.TrashedMessageDao
import com.kite.phalanx.data.source.local.dao.VerdictDao
import com.kite.phalanx.data.source.local.entity.AllowBlockRuleEntity
import com.kite.phalanx.data.source.local.entity.CachedExpansionEntity
import com.kite.phalanx.data.source.local.entity.LinkPreviewEntity
import com.kite.phalanx.data.source.local.entity.SignalEntity
import com.kite.phalanx.data.source.local.entity.TrashedMessageEntity
import com.kite.phalanx.data.source.local.entity.VerdictEntity

/**
 * Room database for Phalanx security features.
 *
 * Phase 1: URL expansions, risk signals, and verdicts
 * Phase 3: Trash vault, allow/block lists (added in v2)
 * Phase 3 Enhancement: Thread grouping for trash vault (v2->v3)
 * Phase 5: Link preview caching (added in v4)
 */
@Database(
    entities = [
        CachedExpansionEntity::class,
        SignalEntity::class,
        VerdictEntity::class,
        TrashedMessageEntity::class,
        AllowBlockRuleEntity::class,
        LinkPreviewEntity::class
    ],
    version = 4,
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

    // Phase 5 DAOs
    abstract fun linkPreviewDao(): LinkPreviewDao

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

        /**
         * Migration from version 3 to 4: Add link_previews table (Phase 5).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create link_previews table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS link_previews (
                        url TEXT PRIMARY KEY NOT NULL,
                        title TEXT,
                        faviconData BLOB,
                        fetchedAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        error TEXT
                    )
                    """.trimIndent()
                )
                // Create index on fetchedAt for efficient expiration queries
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_link_previews_fetchedAt ON link_previews(fetchedAt)"
                )
            }
        }
    }
}
