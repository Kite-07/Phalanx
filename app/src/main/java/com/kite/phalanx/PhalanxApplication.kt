package com.kite.phalanx

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kite.phalanx.domain.repository.SenderPackRepository
import com.kite.phalanx.domain.usecase.MigrateTrustedDomainsUseCase
import com.kite.phalanx.worker.TrashPurgeWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application class for Phalanx.
 *
 * Responsibilities:
 * - Hilt dependency injection setup
 * - WorkManager initialization for background tasks
 * - Phase 3: Trash vault auto-purge scheduling
 * - Phase 3: Migration from legacy TrustedDomainsPreferences to AllowBlockListRepository
 * - Phase 4: Sender pack initialization for sender intelligence
 */
@HiltAndroidApp
class PhalanxApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var migrateTrustedDomainsUseCase: MigrateTrustedDomainsUseCase

    @Inject
    lateinit var senderPackRepository: SenderPackRepository

    // Application-scoped coroutine for one-time migrations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Run one-time migrations
        runMigrations()

        // Load sender intelligence pack (Phase 4)
        loadSenderPack()

        // Schedule periodic trash vault auto-purge
        scheduleTrashPurge()
    }

    /**
     * Run one-time data migrations (Phase 3).
     *
     * - Migrate legacy TrustedDomainsPreferences to Room-based AllowBlockListRepository
     */
    private fun runMigrations() {
        applicationScope.launch {
            try {
                val migratedCount = migrateTrustedDomainsUseCase.execute()
                if (migratedCount != null) {
                    android.util.Log.i("PhalanxApp", "Migrated $migratedCount trusted domains to allow list")
                }
            } catch (e: Exception) {
                android.util.Log.e("PhalanxApp", "Migration failed: ${e.message}", e)
            }
        }
    }

    /**
     * Load sender intelligence pack for current region (Phase 4).
     *
     * Defaults to India (IN) region. User can change region in Security Settings.
     * Pack loading is non-blocking and failures are logged but don't crash the app.
     */
    private fun loadSenderPack() {
        applicationScope.launch {
            try {
                // Get saved region preference (default: IN for India)
                val prefs = getSharedPreferences("security_settings", MODE_PRIVATE)
                val region = prefs.getString("sender_pack_region", "IN") ?: "IN"

                // Load and verify sender pack
                val result = senderPackRepository.loadPack(region)

                if (result.isValid) {
                    android.util.Log.i("PhalanxApp", "Loaded sender pack for region: $region (version: ${result.pack?.version})")
                } else {
                    android.util.Log.w("PhalanxApp", "Failed to load sender pack: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PhalanxApp", "Sender pack loading failed: ${e.message}", e)
            }
        }
    }

    /**
     * Provide WorkManager configuration with Hilt worker factory.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Schedule daily trash vault auto-purge (Phase 3 - Safety Rails).
     *
     * Deletes messages older than 30 days from trash vault.
     * Runs once per day with battery-friendly constraints.
     */
    private fun scheduleTrashPurge() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)        // Only run when battery is not low
            .setRequiresDeviceIdle(false)          // Can run while device is in use
            .setRequiresCharging(false)            // Can run on battery
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // No network needed
            .build()

        val purgeWorkRequest = PeriodicWorkRequestBuilder<TrashPurgeWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .addTag(TrashPurgeWorker.TAG)
            .build()

        // Enqueue with KEEP policy - don't replace existing scheduled work
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TrashPurgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            purgeWorkRequest
        )
    }
}
