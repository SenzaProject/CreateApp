package com.senzaproject.createapp.ui.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.project.FileNode
import com.senzaproject.createapp.domain.project.NewFileType
import com.senzaproject.createapp.domain.project.ProjectFilesRepository
import com.senzaproject.createapp.domain.project.ProjectRepository
import com.senzaproject.createapp.domain.project.fileNameFor
import com.senzaproject.createapp.domain.project.parentOf
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExplorerUiState(
    val projectName: String = "",
    /** Folder yang sedang dibuka, relatif terhadap proyek; kosong = akar. */
    val currentDir: String = "",
    /** Isi folder; null selama dimuat. */
    val entries: List<FileNode>? = null,
    /** Kegagalan memuat folder (bukan kegagalan operasi). */
    val error: AppError? = null,
)

/** Pesan satu kali untuk pengguna (ditampilkan sebagai snackbar). */
sealed interface ExplorerMessage {
    data class Created(val name: String) : ExplorerMessage
    data class Renamed(val name: String) : ExplorerMessage
    data class Deleted(val name: String) : ExplorerMessage
    data class Failed(val error: AppError) : ExplorerMessage
}

/** File Explorer satu proyek: jelajah folder, buat, ganti nama, dan hapus file/folder. */
class ExplorerViewModel(
    private val projects: ProjectRepository,
    private val files: ProjectFilesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    var message by mutableStateOf<ExplorerMessage?>(null)
        private set

    /** True selama operasi yang mengubah file berjalan. */
    var isBusy by mutableStateOf(false)
        private set

    private var projectId: String? = null
    private var refreshJob: Job? = null

    /**
     * Dipanggil setiap kali layar tampil. Untuk proyek yang sama (mis. kembali dari Editor) daftar
     * hanya disegarkan agar ukuran dan waktu ubah file yang baru disimpan ikut terbarui.
     */
    fun load(id: String) {
        if (projectId == id) {
            refresh()
            return
        }
        projectId = id
        _state.value = ExplorerUiState()
        viewModelScope.launch {
            when (val result = projects.getProject(id)) {
                is AppResult.Success -> {
                    _state.update { it.copy(projectName = result.value.name) }
                    refresh()
                }

                is AppResult.Failure -> _state.update { it.copy(entries = emptyList(), error = result.error) }
            }
        }
    }

    fun refresh() {
        val id = projectId ?: return
        val dir = _state.value.currentDir
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            when (val result = files.list(id, dir)) {
                is AppResult.Success -> _state.update { it.copy(entries = result.value, error = null) }
                is AppResult.Failure -> _state.update { it.copy(entries = emptyList(), error = result.error) }
            }
        }
    }

    fun openDirectory(path: String) {
        _state.update { it.copy(currentDir = path, entries = null, error = null) }
        refresh()
    }

    /** Naik satu folder. Mengembalikan false bila sudah di akar (pemanggil lalu keluar dari layar). */
    fun navigateUp(): Boolean {
        val dir = _state.value.currentDir
        if (dir.isEmpty()) return false
        openDirectory(parentOf(dir))
        return true
    }

    fun consumeMessage() {
        message = null
    }

    /** [onResult]: null bila sukses, atau error agar dialog menampilkannya langsung. */
    fun createFile(baseName: String, type: NewFileType?, onResult: (AppError?) -> Unit) {
        val dir = _state.value.currentDir
        val name = if (type == null) baseName else fileNameFor(baseName, type)
        launchOperation(ExplorerMessage.Created(name), onResult) { id -> files.createFile(id, dir, name) }
    }

    fun createFolder(name: String, onResult: (AppError?) -> Unit) {
        val dir = _state.value.currentDir
        launchOperation(ExplorerMessage.Created(name), onResult) { id -> files.createFolder(id, dir, name) }
    }

    fun rename(node: FileNode, newName: String, onResult: (AppError?) -> Unit) {
        launchOperation(ExplorerMessage.Renamed(newName), onResult) { id -> files.rename(id, node.path, newName) }
    }

    /** Pemanggil (UI) sudah meminta konfirmasi pengguna. */
    fun delete(node: FileNode) {
        val onResult: (AppError?) -> Unit = { error ->
            if (error != null) message = ExplorerMessage.Failed(error)
        }
        launchOperation(ExplorerMessage.Deleted(node.name), onResult) { id -> files.delete(id, node.path) }
    }

    private fun launchOperation(
        success: ExplorerMessage,
        onResult: (AppError?) -> Unit,
        operation: suspend (projectId: String) -> AppResult<*>,
    ) {
        val id = projectId ?: return
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                when (val result = operation(id)) {
                    is AppResult.Success -> {
                        message = success
                        onResult(null)
                    }

                    is AppResult.Failure -> onResult(result.error)
                }
                refresh()
            } finally {
                isBusy = false
            }
        }
    }
}
