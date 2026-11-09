package com.senzaproject.createapp.ui.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import com.senzaproject.createapp.domain.terminal.TerminalSession
import com.senzaproject.createapp.domain.terminal.TerminalState
import com.senzaproject.createapp.domain.terminal.TermuxEnvironment
import com.senzaproject.createapp.domain.terminal.TermuxStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Berskala Activity (lihat NavHost). Perintah berjalan di [viewModelScope]: bila Activity benar-benar
 * ditutup, perintah dibatalkan dan prosesnya dimatikan; saat layar diputar atau pindah halaman,
 * perintah tetap berjalan.
 */
class TerminalViewModel(
    private val session: TerminalSession,
    private val termux: TermuxEnvironment,
    observeProjects: ObserveProjectsUseCase,
    private val projectDirectory: (String) -> String,
) : ViewModel() {

    val terminal: StateFlow<TerminalState> = session.state

    val projects: StateFlow<List<Project>> = observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var input by mutableStateOf("")
        private set

    var termuxStatus: TermuxStatus by mutableStateOf(termux.status())
        private set

    private var runningJob: Job? = null

    fun onInputChange(value: String) {
        input = value
    }

    fun submit() {
        val text = input
        if (text.isBlank() || terminal.value.isRunning) return
        input = ""
        runningJob = viewModelScope.launch { session.submit(text) }
    }

    fun stop() {
        runningJob?.cancel()
    }

    fun clear() {
        session.clear()
    }

    fun historyUp() {
        val previous = session.historyPrevious(input) ?: return
        input = previous
    }

    fun historyDown() {
        val next = session.historyNext() ?: return
        input = next
    }

    fun selectBackend(backend: TerminalBackend) {
        if (session.selectBackend(backend)) refreshTermux()
    }

    /** Dipanggil saat layar kembali tampil dan setelah dialog izin, karena pengguna bisa mengubahnya di luar aplikasi. */
    fun refreshTermux() {
        termuxStatus = termux.status()
    }

    fun useProjectDirectory(projectId: String) {
        val path = try {
            projectDirectory(projectId)
        } catch (e: Exception) {
            return
        }
        session.changeDirectory(path)
    }

    fun goHome() {
        session.goHome()
    }
}
