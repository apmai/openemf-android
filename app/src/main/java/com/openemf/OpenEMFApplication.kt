package com.openemf

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for OpenEMF.
 * Required for Hilt dependency injection.
 */
@HiltAndroidApp
class OpenEMFApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize any global components here
    }
}
