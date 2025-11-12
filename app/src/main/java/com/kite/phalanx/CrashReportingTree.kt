package com.kite.phalanx

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Timber tree for production builds that logs errors to Firebase Crashlytics.
 *
 * Behavior:
 * - DEBUG and INFO logs: Silently ignored (not logged)
 * - WARN logs: Logged to Crashlytics as non-fatal events
 * - ERROR logs: Logged to Crashlytics with full exception details
 *
 * Privacy: Only logs error messages and stack traces, never user data.
 * This tree ensures debug logs never reach production while critical errors
 * are reported for monitoring app health.
 *
 * Note: android.util.Log constants are still used here for priority comparison
 * since this is a Timber.Tree implementation that handles logging at the system level.
 */
class CrashReportingTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Ignore debug and info logs in production
        if (priority == Log.DEBUG || priority == Log.INFO || priority == Log.VERBOSE) {
            return
        }

        // Log warnings and errors to Crashlytics
        val crashlytics = FirebaseCrashlytics.getInstance()

        // Add custom keys for better crash grouping
        tag?.let { crashlytics.setCustomKey("log_tag", it) }
        crashlytics.setCustomKey("log_priority", priorityToString(priority))

        when (priority) {
            Log.WARN -> {
                // Log warnings as non-fatal events
                crashlytics.log("WARN: $message")
                t?.let { crashlytics.recordException(it) }
            }
            Log.ERROR -> {
                // Log errors with full details
                crashlytics.log("ERROR: $message")
                if (t != null) {
                    crashlytics.recordException(t)
                } else {
                    // If no exception provided, create one for stack trace
                    crashlytics.recordException(Exception(message))
                }
            }
        }
    }

    private fun priorityToString(priority: Int): String = when (priority) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        Log.ASSERT -> "ASSERT"
        else -> "UNKNOWN"
    }
}
