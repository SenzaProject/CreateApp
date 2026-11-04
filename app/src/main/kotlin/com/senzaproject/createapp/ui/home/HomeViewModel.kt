package com.senzaproject.createapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.core.common.AppDispatchers
import com.senzaproject.createapp.core.common.CrashReporter
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectFilter
import com.senzaproject.createapp.domain.project.filterProjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    observeProjects: ObserveProjectsUseCase,
    private val crashReporter: CrashReporter,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    /** Proyek terbaru; null selama daftar belum dimuat. */
    val recentProjects: StateFlow<List<Project>?> = observeProjects()
        .map { projects ->
            filterProjects(projects, query = "", filter = ProjectFilter.RECENT).take(RECENT_LIMIT)
        }
        .stateIn<List<Project>?>(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _lastCrash = MutableStateFlow<String?>(null)
    val lastCrash: StateFlow<String?> = _lastCrash.asStateFlow()

    init {
        viewModelScope.launch {
            _lastCrash.value = withContext(dispatchers.io) { crashReporter.readLastCrash() }
        }
    }

    fun clearCrashReport() {
        viewModelScope.launch {
            withContext(dispatchers.io) { crashReporter.clear() }
            _lastCrash.value = null
        }
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
