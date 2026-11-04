package com.senzaproject.createapp.ui.projects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectFilter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProjectsViewModel(
    observeProjects: ObserveProjectsUseCase,
) : ViewModel() {

    /** Semua proyek; null selama belum dimuat. */
    val projects: StateFlow<List<Project>?> = observeProjects()
        .stateIn<List<Project>?>(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    // State Compose (sinkron) agar pengetikan di kolom pencarian tidak tertunda.
    var query by mutableStateOf("")
        private set

    var filter by mutableStateOf(ProjectFilter.ALL)
        private set

    fun onQueryChange(value: String) {
        query = value
    }

    fun onFilterChange(value: ProjectFilter) {
        filter = value
    }
}
