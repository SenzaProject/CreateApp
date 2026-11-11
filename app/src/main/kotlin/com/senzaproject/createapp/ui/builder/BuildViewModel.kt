package com.senzaproject.createapp.ui.builder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.builder.BuildHistoryStore
import com.senzaproject.createapp.domain.builder.BuildRecord
import com.senzaproject.createapp.domain.builder.BuildRequest
import com.senzaproject.createapp.domain.builder.BuildSession
import com.senzaproject.createapp.domain.builder.BuildSettingsStore
import com.senzaproject.createapp.domain.builder.BuildState
import com.senzaproject.createapp.domain.builder.BuildType
import com.senzaproject.createapp.domain.builder.CleanResult
import com.senzaproject.createapp.domain.builder.SharedStoragePath
import com.senzaproject.createapp.domain.builder.ToolchainReport
import com.senzaproject.createapp.domain.builder.ToolchainResult
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.terminal.TermuxEnvironment
import com.senzaproject.createapp.domain.terminal.TermuxStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pesan singkat sekali tampil; teksnya ditentukan UI. */
enum class BuildMessage {
    NEED_PROJECT,
    NEED_TERMUX,
    NEED_FOLDER,
    FOLDER_UNSUPPORTED,
    FOLDER_SAVED,
    BUSY,
    PICKER_UNAVAILABLE,
    CLEAN_DONE,
    CLEAN_BUSY,
    CLEAN_FAILED,
}

/** Hasil pemeriksaan lingkungan yang ditampilkan di kartu Lingkungan. */
sealed interface ToolchainState {
    data object Unknown : ToolchainState
    data object Checking : ToolchainState
    data class Ready(val report: ToolchainReport) : ToolchainState
    data class Failed(val result: ToolchainResult.Failed) : ToolchainState
}

class OpenedLog(val record: BuildRecord, val text: String?)

/**
 * Berskala Activity (lihat NavHost). Build sendiri berjalan di [BuildSession] yang berskala aplikasi,
 * jadi menutup layar tidak menghentikan build; ViewModel ini hanya memegang pilihan pengguna.
 */
class BuildViewModel(
    private val session: BuildSession,
    private val history: BuildHistoryStore,
    private val settings: BuildSettingsStore,
    observeProjects: ObserveProjectsUseCase,
    private val termux: TermuxEnvironment,
) : ViewModel() {

    val build: StateFlow<BuildState> = session.state

    val projects: StateFlow<List<Project>> = observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedProjectId: String? by mutableStateOf(null)
        private set

    var selectedType: BuildType by mutableStateOf(BuildType.DEBUG)
        private set

    var treeUri: String? by mutableStateOf(settings.sharedTreeUri)
        private set

    var termuxStatus: TermuxStatus by mutableStateOf(termux.status())
        private set

    var toolchain: ToolchainState by mutableStateOf(ToolchainState.Unknown)
        private set

    var historyRecords: List<BuildRecord> by mutableStateOf(emptyList())
        private set

    var message: BuildMessage? by mutableStateOf(null)
        private set

    var openedLog: OpenedLog? by mutableStateOf(null)
        private set

    /** Path folder bersama yang dilihat Termux, atau null bila belum dipilih / tidak didukung. */
    val sharedPath: String?
        get() = treeUri?.let { SharedStoragePath.fromTreeUri(it) }

    init {
        // Muat ulang riwayat saat awal dan setiap kali build selesai (status berubah ke keadaan tidak aktif).
        viewModelScope.launch {
            session.state
                .map { it.status }
                .distinctUntilChanged()
                .collect { historyRecords = history.load() }
        }
    }

    /** Proyek yang berlaku: pilihan pengguna bila masih ada, jika tidak proyek pertama. */
    fun currentProject(): Project? {
        val list = projects.value
        return list.firstOrNull { it.id == selectedProjectId } ?: list.firstOrNull()
    }

    fun selectProject(id: String) {
        selectedProjectId = id
    }

    fun selectType(type: BuildType) {
        selectedType = type
    }

    fun refreshTermux() {
        termuxStatus = termux.status()
    }

    fun onFolderPicked(uri: String) {
        if (SharedStoragePath.fromTreeUri(uri) == null) {
            message = BuildMessage.FOLDER_UNSUPPORTED
            return
        }
        settings.sharedTreeUri = uri
        treeUri = uri
        toolchain = ToolchainState.Unknown
        message = BuildMessage.FOLDER_SAVED
    }

    fun onPickerUnavailable() {
        message = BuildMessage.PICKER_UNAVAILABLE
    }

    fun checkToolchain() {
        val path = readyPathOrMessage() ?: return
        toolchain = ToolchainState.Checking
        viewModelScope.launch {
            toolchain = when (val result = session.checkToolchain(path)) {
                is ToolchainResult.Report -> ToolchainState.Ready(result.report)
                is ToolchainResult.Failed -> ToolchainState.Failed(result)
            }
        }
    }

    fun cleanTermuxWork() {
        if (termuxStatus !is TermuxStatus.Ready) {
            message = BuildMessage.NEED_TERMUX
            return
        }
        viewModelScope.launch {
            message = when (session.cleanTermuxWork()) {
                CleanResult.Done -> BuildMessage.CLEAN_DONE
                CleanResult.Busy -> BuildMessage.CLEAN_BUSY
                is CleanResult.Failed -> BuildMessage.CLEAN_FAILED
            }
        }
    }

    fun startBuild() {
        val project = currentProject()
        if (project == null) {
            message = BuildMessage.NEED_PROJECT
            return
        }
        val path = readyPathOrMessage() ?: return
        val uri = treeUri ?: return
        val request = BuildRequest(
            projectId = project.id,
            projectName = project.name,
            type = selectedType,
            sharedTreeUri = uri,
            sharedRootPath = path,
        )
        if (!session.start(request)) message = BuildMessage.BUSY
    }

    fun cancelBuild() {
        session.cancel()
    }

    fun dismissResult() {
        session.reset()
    }

    fun openLog(record: BuildRecord) {
        viewModelScope.launch {
            openedLog = OpenedLog(record, history.readLog(record.id))
        }
    }

    fun closeLog() {
        openedLog = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            history.clear()
            historyRecords = emptyList()
        }
    }

    fun consumeMessage() {
        message = null
    }

    /** Memastikan Termux siap dan folder bersama valid; bila tidak, mengisi [message] dan mengembalikan null. */
    private fun readyPathOrMessage(): String? {
        refreshTermux()
        if (termuxStatus !is TermuxStatus.Ready) {
            message = BuildMessage.NEED_TERMUX
            return null
        }
        val path = sharedPath
        if (path == null) {
            message = BuildMessage.NEED_FOLDER
            return null
        }
        return path
    }
}
