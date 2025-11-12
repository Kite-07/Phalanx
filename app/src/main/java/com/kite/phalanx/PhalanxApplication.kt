package com.kite.phalanx

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kite.phalanx.BuildConfig
import com.kite.phalanx.domain.repository.SenderPackRepository
import com.kite.phalanx.domain.usecase.MigrateTrustedDomainsUseCase
import com.kite.phalanx.worker.WorkerScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application class for Phalanx.
 *
 * Responsibilities:
 * - Hilt dependency injection setup
 * - WorkManager initialization for background tasks
 * - Phase 3: Migration from legacy TrustedDomainsPreferences to AllowBlockListRepository
 * - Phase 4: Sender pack initialization for sender intelligence
 * - Phase 7: Schedule periodic workers (database cleanup, trash purge, updates)
 * - Phase 7: Cold-start optimization (defer non-critical tasks)
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

        // Initialize Firebase Crashlytics
        // Note: Crashlytics is disabled in debug builds (no crash reporting for developers)
        // and automatically enabled in release builds for production monitoring
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Initialize Timber logging
        // Debug builds: Log everything to logcat with class tags
        // Release builds: Only log WARN and ERROR to Firebase Crashlytics
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }

        // Phase 7: Cold-start optimization
        // Defer non-critical initialization to reduce cold-start time
        // Critical tasks run immediately, non-critical tasks deferred by 500ms

        // Immediate: Run one-time migrations (critical for data consistency)
        runMigrations()

        // Deferred: Load sender pack and schedule workers (non-critical for startup)
        deferNonCriticalTasks()
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
                    Timber.i("Migrated $migratedCount trusted domains to allow list")
                }
            } catch (e: Exception) {
                Timber.e(e, "Migration failed: ${e.message}")
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    /**
     * Defer non-critical initialization tasks (Phase 7 cold-start optimization).
     *
     * Defers:
     * - Sender pack loading (Phase 4): Can happen after UI is ready
     * - Worker scheduling (Phase 7): Background tasks, no urgency
     *
     * Deferral strategy: Wait 500ms after onCreate() completes, allowing
     * the UI to render and the user to see the app immediately.
     */
    private fun deferNonCriticalTasks() {
        applicationScope.launch {
            // Delay 500ms to allow UI to render first
            delay(500)

            // Load sender pack (non-blocking, happens in background)
            loadSenderPack()

            // Schedule periodic workers (non-blocking, WorkManager handles scheduling)
            WorkerScheduler.scheduleAllWorkers(applicationContext)

            Timber.d("Non-critical tasks initialized (deferred 500ms)")
        }
    }

    /**
     * Load sender intelligence pack for current region (Phase 4).
     *
     * Defaults to India (IN) region. User can change region in Security Settings.
     * Pack loading is non-blocking and failures are logged but don't crash the app.
     */
    private suspend fun loadSenderPack() {
        try {
            // Get saved region preference (default: IN for India)
            val prefs = getSharedPreferences("security_settings", MODE_PRIVATE)
            val region = prefs.getString("sender_pack_region", "IN") ?: "IN"

            // Load and verify sender pack
            val result = senderPackRepository.loadPack(region)

            if (result.isValid) {
                Timber.i("Loaded sender pack for region: $region (version: ${result.pack?.version})")
            } else {
                Timber.w("Failed to load sender pack: ${result.errorMessage}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Sender pack loading failed: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Provide WorkManager configuration with Hilt worker factory.
     *
     * Phase 7: Lazy initialization - configuration is only created when needed,
     * reducing cold-start overhead.
     */
    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
