package com.kite.phalanx.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Phase 7: WorkManager scheduler for periodic background tasks.
 *
 * Per PRD Phase 7: Freshness & Reliability
 * - Schedules database cleanup (daily)
 * - Schedules trash vault auto-purge (daily)
 * - Schedules sender pack updates (weekly, unmetered network only)
 * - Schedules PSL updates (monthly, unmetered network only)
 * - Battery-friendly constraints: only runs when idle and charging
 * - Network-aware constraints: only unmetered networks for updates
 */
object WorkerScheduler {

    /**
     * Schedule all Phase 7 periodic workers.
     *
     * Called from PhalanxApplication.onCreate() to ensure workers are scheduled
     * when the app starts.
     *
     * @param context Application context
     */
    fun scheduleAllWorkers(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Database cleanup worker: runs daily when device is idle and charging
        scheduleDatabaseCleanup(workManager)

        // Trash vault auto-purge: runs daily when device is idle and charging
        scheduleTrashVaultPurge(workManager)

        // Sender pack updates: runs weekly on unmetered network only
        scheduleSenderPackUpdate(workManager)

        // PSL updates: runs monthly on unmetered network only
        schedulePSLUpdate(workManager)
    }

    /**
     * Schedule database cleanup worker (daily).
     *
     * Constraints:
     * - Requires device to be idle (not actively used)
     * - Requires device to be charging (battery-friendly)
     * - Runs once per day
     */
    private fun scheduleDatabaseCleanup(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true) // Only when device is idle
            .setRequiresCharging(true)   // Only when charging (battery-friendly)
            .build()

        val cleanupRequest = PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(
            repeatInterval = 1, // Repeat every 1 day
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        // Use KEEP policy: don't replace existing worker if already scheduled
        workManager.enqueueUniquePeriodicWork(
            DatabaseCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    /**
     * Schedule trash vault auto-purge worker (daily).
     *
     * Constraints:
     * - Requires device to be idle (not actively used)
     * - Requires device to be charging (battery-friendly)
     * - Runs once per day
     */
    private fun scheduleTrashVaultPurge(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true) // Only when device is idle
            .setRequiresCharging(true)   // Only when charging (battery-friendly)
            .build()

        val purgeRequest = PeriodicWorkRequestBuilder<TrashVaultPurgeWorker>(
            repeatInterval = 1, // Repeat every 1 day
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        // Use KEEP policy: don't replace existing worker if already scheduled
        workManager.enqueueUniquePeriodicWork(
            TrashVaultPurgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            purgeRequest
        )
    }

    /**
     * Schedule sender pack update worker (weekly).
     *
     * Constraints:
     * - Requires unmetered network (Wi-Fi) to avoid mobile data charges
     * - Runs once per week to fetch updated sender intelligence
     */
    private fun scheduleSenderPackUpdate(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<SenderPackUpdateWorker>(
            repeatInterval = 7, // Repeat every 7 days (weekly)
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        // Use KEEP policy: don't replace existing worker if already scheduled
        workManager.enqueueUniquePeriodicWork(
            SenderPackUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    /**
     * Schedule PSL (Public Suffix List) update worker (monthly).
     *
     * Constraints:
     * - Requires unmetered network (Wi-Fi) to avoid mobile data charges
     * - Runs once per month (PSL doesn't change frequently)
     */
    private fun schedulePSLUpdate(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<PSLUpdateWorker>(
            repeatInterval = 30, // Repeat every 30 days (monthly)
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        // Use KEEP policy: don't replace existing worker if already scheduled
        workManager.enqueueUniquePeriodicWork(
            PSLUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    /**
     * Cancel all scheduled workers.
     * Used for testing or when user disables background tasks.
     */
    fun cancelAllWorkers(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(DatabaseCleanupWorker.WORK_NAME)
        workManager.cancelUniqueWork(TrashVaultPurgeWorker.WORK_NAME)
        workManager.cancelUniqueWork(SenderPackUpdateWorker.WORK_NAME)
        workManager.cancelUniqueWork(PSLUpdateWorker.WORK_NAME)
    }
}
