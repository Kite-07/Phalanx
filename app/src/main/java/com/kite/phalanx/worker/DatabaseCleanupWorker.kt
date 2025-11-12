package com.kite.phalanx.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kite.phalanx.data.source.local.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Phase 7: Database cleanup worker for cache hardening.
 *
 * Responsibilities:
 * - Remove expired URL expansion cache entries
 * - Remove expired link preview cache entries
 * - Remove old verdicts (30+ days)
 * - Clean up orphaned signals
 * - Vacuum database to reclaim space
 *
 * Per PRD Phase 7: Cache/Storage Hardening
 * - Runs periodically (daily) via WorkManager
 * - Battery-friendly: only runs when device is idle and charging
 */
@HiltWorker
class DatabaseCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "database_cleanup"

        // Cleanup thresholds
        private const val VERDICT_RETENTION_DAYS = 30L
        private const val VERDICT_RETENTION_MS = VERDICT_RETENTION_DAYS * 24 * 60 * 60 * 1000
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting database cleanup...")

            val now = System.currentTimeMillis()
            val cutoffTimestamp = now - VERDICT_RETENTION_MS

            // 1. Clean expired URL expansion cache
            val expiredExpansions = database.cachedExpansionDao().deleteExpired(now)
            Timber.d("Removed $expiredExpansions expired URL expansions")

            // 2. Clean expired link preview cache
            val expiredPreviews = database.linkPreviewDao().deleteExpired(now)
            Timber.d("Removed $expiredPreviews expired link previews")

            // 3. Clean old verdicts (30+ days)
            val oldVerdicts = database.verdictDao().deleteOlderThan(cutoffTimestamp)
            Timber.d("Removed $oldVerdicts old verdicts (>30 days)")

            // 4. Clean orphaned signals (signals whose verdicts were deleted)
            val orphanedSignals = database.signalDao().deleteOrphaned()
            Timber.d("Removed $orphanedSignals orphaned signals")

            // 5. Vacuum database to reclaim space (SQLite VACUUM)
            database.query("VACUUM", null)
            Timber.d("Database vacuumed successfully")

            Timber.d("Database cleanup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Database cleanup failed")
            Result.retry() // Retry on failure
        }
    }
}
