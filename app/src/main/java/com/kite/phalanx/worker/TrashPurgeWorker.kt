package com.kite.phalanx.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kite.phalanx.domain.repository.TrashVaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for auto-purging expired trashed messages.
 *
 * Phase 3 - Safety Rails: Trash Vault Auto-Purge
 * Runs daily to delete messages older than 30 days from trash vault.
 */
@HiltWorker
class TrashPurgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trashVaultRepository: TrashVaultRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "TrashPurgeWorker"
        const val WORK_NAME = "trash_purge_periodic"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting trash vault auto-purge...")

        return try {
            // Purge expired messages (older than 30 days)
            val purgedCount = trashVaultRepository.purgeExpiredMessages()

            Log.d(TAG, "Auto-purge completed: $purgedCount messages deleted")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-purge failed", e)

            // Retry on failure (WorkManager will handle backoff)
            Result.retry()
        }
    }
}
