package com.senzaproject.createapp.ui.projects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.project.NewProjectRequest
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectFilter
import com.senzaproject.createapp.domain.project.ProjectRepository
import com.senzaproject.createapp.domain.project.ProjectTransferRepository
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pesan satu kali untuk pengguna (ditampilkan sebagai snackbar). */
sealed interface ProjectsMessage {
    data class Created(val name: String) : ProjectsMessage
    data class Renamed(val name: String) : ProjectsMessage
    data class Deleted(val name: String) : ProjectsMessage
    data class Imported(val name: String) : ProjectsMessage
    data class Exported(val fileCount: Int) : ProjectsMessage
    data class Failed(val error: AppError) : ProjectsMessage
}

class ProjectsViewModel(
    observeProjects: ObserveProjectsUseCase,
    private val repository: ProjectRepository,
    private val transfer: ProjectTransferRepository,
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

    /** True selama operasi yang mengubah penyimpanan berjalan; tombol terkait dinonaktifkan. */
    var isBusy by mutableStateOf(false)
        private set

    var message by mutableStateOf<ProjectsMessage?>(null)
        private set

    private var pendingExportId: String? = null

    fun onQueryChange(value: String) {
        query = value
    }

    fun onFilterChange(value: ProjectFilter) {
        filter = value
    }

    fun consumeMessage() {
        message = null
    }

    /** [onResult] dipanggil dengan null bila sukses, atau error agar dialog menampilkannya langsung. */
    fun createProject(request: NewProjectRequest, onResult: (AppError?) -> Unit) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                when (val result = repository.createProject(request)) {
                    is AppResult.Success -> {
                        message = ProjectsMessage.Created(result.value.name)
                        onResult(null)
                    }

                    is AppResult.Failure -> onResult(result.error)
                }
            } finally {
                isBusy = false
            }
        }
    }

    fun renameProject(project: Project, newName: String, onResult: (AppError?) -> Unit) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                when (val result = repository.renameProject(project.id, newName)) {
                    is AppResult.Success -> {
                        message = ProjectsMessage.Renamed(result.value.name)
                        onResult(null)
                    }

                    is AppResult.Failure -> onResult(result.error)
                }
            } finally {
                isBusy = false
            }
        }
    }

    fun toggleFavorite(project: Project) {
        viewModelScope.launch {
            when (val result = repository.setFavorite(project.id, !project.isFavorite)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> message = ProjectsMessage.Failed(result.error)
            }
        }
    }

    /** Pemanggil (UI) sudah meminta konfirmasi pengguna. */
    fun deleteProject(project: Project) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                when (val result = repository.deleteProject(project.id)) {
                    is AppResult.Success -> message = ProjectsMessage.Deleted(project.name)
                    is AppResult.Failure -> message = ProjectsMessage.Failed(result.error)
                }
            } finally {
                isBusy = false
            }
        }
    }

    /** [treeUri] adalah folder hasil pemilih SAF; null (pemilih dibatalkan) diabaikan oleh UI. */
    fun importProject(treeUri: String) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                when (val result = transfer.importFromTree(treeUri)) {
                    is AppResult.Success -> message = ProjectsMessage.Imported(result.value.name)
                    is AppResult.Failure -> message = ProjectsMessage.Failed(result.error)
                }
            } finally {
                isBusy = false
            }
        }
    }

    /** Mencatat proyek yang akan diekspor sebelum pemilih folder dibuka. */
    fun beginExport(project: Project) {
        pendingExportId = project.id
    }

    /** Dipanggil dengan hasil pemilih folder; null berarti pengguna membatalkan. */
    fun finishExport(treeUri: String?) {
        val id = pendingExportId
        pendingExportId = null
        if (id == null || treeUri == null || isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                when (val result = transfer.exportToTree(id, treeUri)) {
                    is AppResult.Success -> message = ProjectsMessage.Exported(result.value)
                    is AppResult.Failure -> message = ProjectsMessage.Failed(result.error)
                }
            } finally {
                isBusy = false
            }
        }
    }

    /** Perangkat tidak punya pemilih folder sistem. */
    fun onPickerUnavailable() {
        message = ProjectsMessage.Failed(AppError.Unsupported("Pemilih folder sistem tidak tersedia"))
    }
}
