package com.senzaproject.createapp.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.senzaproject.createapp.ui.home.HomeViewModel

/** Pabrik ViewModel tunggal yang menyuntikkan dependensi dari [AppContainer]. */
class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            getAppDiagnostics = container.getAppDiagnostics,
            crashReporter = container.crashReporter,
            dispatchers = container.dispatchers,
            logger = container.logger,
        ) as T

        else -> throw IllegalArgumentException("ViewModel tidak dikenal: ${modelClass.name}")
    }
}
