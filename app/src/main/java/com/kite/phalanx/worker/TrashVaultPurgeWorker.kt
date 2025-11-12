package com.kite.phalanx.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kite.phalanx.domain.repository.TrashVaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Phase 7: Trash Vault auto-purge worker.
 *
 * Per PRD Phase 3 & 7:
 * - Trash vault retains deleted messages for 30 days
 * - After 30 days, messages are permanently deleted (auto-purge)
 * - Runs periodically (daily) via WorkManager
 *
 * Responsibilities:
 * - Delete trashed messages older than 30 days
 * - Permanently remove messages from the trash vault
 * - Battery-friendly: runs when device is idle and charging
 */
@HiltWorker
class TrashVaultPurgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val trashVaultRepository: TrashVaultRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "trash_vault_purge"
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting trash vault auto-purge...")

            val now = System.currentTimeMillis()
            val purgedCount = trashVaultRepository.purgeExpiredMessages(now)

            Timber.d("Trash vault auto-purge completed: $purgedCount messages permanently deleted")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Trash vault auto-purge failed")
            Result.retry() // Retry on failure
        }
    }
}
