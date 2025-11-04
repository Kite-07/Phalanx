package com.kite.phalanx

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Phalanx.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class PhalanxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application initialization will go here
    }
}
