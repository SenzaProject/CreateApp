package com.senzaproject.createapp

import android.app.Application
import com.senzaproject.createapp.di.AppContainer

class CreateAppApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashReporter.install()
        container.logger.i(TAG, "Create App ${BuildConfig.VERSION_NAME} dimulai")
    }

    private companion object {
        const val TAG = "CreateApp"
    }
}
