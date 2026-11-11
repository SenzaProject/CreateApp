package com.senzaproject.createapp.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.senzaproject.createapp.ui.builder.BuildViewModel
import com.senzaproject.createapp.ui.editor.EditorViewModel
import com.senzaproject.createapp.ui.explorer.ExplorerViewModel
import com.senzaproject.createapp.ui.home.HomeViewModel
import com.senzaproject.createapp.ui.projects.ProjectsViewModel
import com.senzaproject.createapp.ui.settings.SettingsViewModel
import com.senzaproject.createapp.ui.terminal.TerminalViewModel

/** Pabrik ViewModel tunggal yang menyuntikkan dependensi dari [AppContainer]. */
class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            observeProjects = container.observeProjects,
            crashReporter = container.crashReporter,
            dispatchers = container.dispatchers,
        ) as T

        modelClass.isAssignableFrom(ProjectsViewModel::class.java) -> ProjectsViewModel(
            observeProjects = container.observeProjects,
            repository = container.projectRepository,
            transfer = container.projectTransfer,
        ) as T

        modelClass.isAssignableFrom(ExplorerViewModel::class.java) -> ExplorerViewModel(
            projects = container.projectRepository,
            files = container.projectFiles,
        ) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            getAppDiagnostics = container.getAppDiagnostics,
            logger = container.logger,
        ) as T

        modelClass.isAssignableFrom(EditorViewModel::class.java) -> EditorViewModel(
            files = container.projectFiles,
        ) as T

        modelClass.isAssignableFrom(TerminalViewModel::class.java) -> TerminalViewModel(
            session = container.terminalSession,
            termux = container.termuxEnvironment,
            observeProjects = container.observeProjects,
            projectDirectory = container::projectDirectoryPath,
        ) as T

        modelClass.isAssignableFrom(BuildViewModel::class.java) -> BuildViewModel(
            session = container.buildSession,
            history = container.buildHistory,
            settings = container.buildSettings,
            observeProjects = container.observeProjects,
            termux = container.termuxEnvironment,
        ) as T

        else -> throw IllegalArgumentException("ViewModel tidak dikenal: ${modelClass.name}")
    }
}
