package com.senzaproject.createapp.ui.apk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.apk.ArtifactDetails
import com.senzaproject.createapp.domain.apk.ArtifactListing
import com.senzaproject.createapp.domain.apk.ArtifactNames
import com.senzaproject.createapp.domain.apk.ArtifactStore
import com.senzaproject.createapp.domain.apk.BuildHistoryLinker
import com.senzaproject.createapp.domain.apk.HistoryEntry
import com.senzaproject.createapp.domain.apk.InstallBlock
import com.senzaproject.createapp.domain.apk.InstallPolicy
import com.senzaproject.createapp.domain.apk.ManagedArtifact
import com.senzaproject.createapp.domain.apk.ProjectArtifacts
import com.senzaproject.createapp.domain.builder.BuildHistoryStore
import com.senzaproject.createapp.domain.builder.BuildSettingsStore
import com.senzaproject.createapp.domain.builder.SharedStoragePath
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Peristiwa sekali pakai yang harus ditangani layar (membuka intent, menampilkan pesan). */
sealed interface ApkEvent {
    data class Install(val contentUri: String) : ApkEvent
    data class Share(val contentUri: String, val mime: String) : ApkEvent
    data class Blocked(val reason: InstallBlock) : ApkEvent
    data class Failed(val error: AppError) : ApkEvent
    data object NeedFolder : ApkEvent
    data class Exported(val bytes: Long) : ApkEvent
    data object Deleted : ApkEvent
}

class ApkManagerViewModel(
    private val store: ArtifactStore,
    private val history: BuildHistoryStore,
    private val settings: BuildSettingsStore,
    observeProjects: ObserveProjectsUseCase,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var loading: Boolean by mutableStateOf(false)
        private set

    var groups: List<ProjectArtifacts> by mutableStateOf(emptyList())
        private set

    var historyEntries: List<HistoryEntry> by mutableStateOf(emptyList())
        private set

    var listError: AppError? by mutableStateOf(null)
        private set

    /** Kunci artefak yang sedang diproses (memeriksa, menyalin, menghapus); tombolnya dinonaktifkan. */
    var busyKey: String? by mutableStateOf(null)
        private set

    var selected: ManagedArtifact? by mutableStateOf(null)
        private set

    var selectedDetails: ArtifactDetails? by mutableStateOf(null)
        private set

    var detailsLoading: Boolean by mutableStateOf(false)
        private set

    var pendingDelete: ManagedArtifact? by mutableStateOf(null)
        private set

    var event: ApkEvent? by mutableStateOf(null)
        private set

    private var all: List<ManagedArtifact> = emptyList()
    private val detailsCache = HashMap<String, Pair<ManagedArtifact, ArtifactDetails>>()

    val treeUri: String? get() = settings.sharedTreeUri

    /** Path folder bersama (dilihat Termux), atau null bila belum dipilih. */
    val sharedPath: String? get() = treeUri?.let { SharedStoragePath.fromTreeUri(it) }

    fun projectName(projectId: String): String =
        projects.value.firstOrNull { it.id == projectId }?.name ?: projectId

    fun refresh() {
        val uri = treeUri
        if (uri == null) {
            all = emptyList()
            groups = emptyList()
            historyEntries = emptyList()
            return
        }
        viewModelScope.launch {
            loading = true
            when (val result = store.list(uri)) {
                is AppResult.Success -> {
                    all = result.value
                    groups = ArtifactListing.group(all)
                    listError = null
                    // Detail lama dibuang bila filenya berubah (ukuran atau waktu ubah beda).
                    val live = all.associateBy { it.key }
                    detailsCache.entries.removeAll { (key, cached) -> live[key] != cached.first }
                    // Artefak yang sedang dibuka tetap sinkron dengan isi folder.
                    selected = selected?.let { live[it.key] }
                    if (selected == null) selectedDetails = null
                    historyEntries = BuildHistoryLinker.link(history.load(), all)
                }

                is AppResult.Failure -> {
                    listError = result.error
                    all = emptyList()
                    groups = emptyList()
                }
            }
            loading = false
        }
    }

    // --- Detail ---

    fun open(artifact: ManagedArtifact) {
        selected = artifact
        selectedDetails = detailsCache[artifact.key]?.second
        if (selectedDetails == null) loadDetails(artifact)
    }

    fun closeDetails() {
        selected = null
        selectedDetails = null
        detailsLoading = false
    }

    private fun loadDetails(artifact: ManagedArtifact) {
        val uri = treeUri ?: return
        viewModelScope.launch {
            detailsLoading = true
            when (val result = store.inspect(uri, artifact)) {
                is AppResult.Success -> {
                    detailsCache[artifact.key] = Pair(artifact, result.value)
                    if (selected?.key == artifact.key) selectedDetails = result.value
                }

                is AppResult.Failure -> event = ApkEvent.Failed(result.error)
            }
            detailsLoading = false
        }
    }

    // --- Aksi ---

    /** Memeriksa file, menerapkan [InstallPolicy], lalu menyalin ke cache dan meminta layar membuka pemasang. */
    fun install(artifact: ManagedArtifact, canRequestInstalls: Boolean) {
        val uri = treeUri ?: run {
            event = ApkEvent.NeedFolder
            return
        }
        viewModelScope.launch {
            busyKey = artifact.key
            try {
                val details = detailsFor(uri, artifact) ?: return@launch
                val block = InstallPolicy.check(artifact, details, canRequestInstalls)
                if (block != null) {
                    event = ApkEvent.Blocked(block)
                    return@launch
                }
                when (val staged = store.stage(uri, artifact)) {
                    is AppResult.Success -> event = ApkEvent.Install(staged.value.contentUri)
                    is AppResult.Failure -> event = ApkEvent.Failed(staged.error)
                }
            } finally {
                busyKey = null
            }
        }
    }

    fun share(artifact: ManagedArtifact) {
        val uri = treeUri ?: run {
            event = ApkEvent.NeedFolder
            return
        }
        viewModelScope.launch {
            busyKey = artifact.key
            try {
                when (val staged = store.stage(uri, artifact)) {
                    is AppResult.Success ->
                        event = ApkEvent.Share(staged.value.contentUri, ArtifactNames.mimeType(artifact.kind))

                    is AppResult.Failure -> event = ApkEvent.Failed(staged.error)
                }
            } finally {
                busyKey = null
            }
        }
    }

    fun export(artifact: ManagedArtifact, destinationUri: String) {
        val uri = treeUri ?: run {
            event = ApkEvent.NeedFolder
            return
        }
        viewModelScope.launch {
            busyKey = artifact.key
            try {
                event = when (val result = store.export(uri, artifact, destinationUri)) {
                    is AppResult.Success -> ApkEvent.Exported(result.value)
                    is AppResult.Failure -> ApkEvent.Failed(result.error)
                }
            } finally {
                busyKey = null
            }
        }
    }

    fun askDelete(artifact: ManagedArtifact) {
        pendingDelete = artifact
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    fun confirmDelete() {
        val artifact = pendingDelete ?: return
        pendingDelete = null
        val uri = treeUri ?: return
        viewModelScope.launch {
            busyKey = artifact.key
            try {
                when (val result = store.delete(uri, artifact)) {
                    is AppResult.Success -> {
                        detailsCache.remove(artifact.key)
                        if (selected?.key == artifact.key) closeDetails()
                        event = ApkEvent.Deleted
                    }

                    is AppResult.Failure -> event = ApkEvent.Failed(result.error)
                }
            } finally {
                busyKey = null
            }
            // Daftar dimuat ulang dari isi folder agar yang tampil selalu yang benar-benar ada.
            refresh()
        }
    }

    fun consumeEvent() {
        event = null
    }

    private suspend fun detailsFor(uri: String, artifact: ManagedArtifact): ArtifactDetails? {
        detailsCache[artifact.key]?.let { if (it.first == artifact) return it.second }
        return when (val result = store.inspect(uri, artifact)) {
            is AppResult.Success -> {
                detailsCache[artifact.key] = Pair(artifact, result.value)
                if (selected?.key == artifact.key) selectedDetails = result.value
                result.value
            }

            is AppResult.Failure -> {
                event = ApkEvent.Failed(result.error)
                null
            }
        }
    }
}
