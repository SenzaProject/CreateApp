package com.senzaproject.createapp.ui.apk

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.apk.ApkInfo
import com.senzaproject.createapp.apk.ApkInfoReader
import com.senzaproject.createapp.apk.ApkStager
import com.senzaproject.createapp.domain.builder.ArtifactEntry
import com.senzaproject.createapp.domain.builder.ArtifactManager
import com.senzaproject.createapp.domain.builder.ArtifactRules
import com.senzaproject.createapp.domain.builder.ArtifactSnapshot
import com.senzaproject.createapp.domain.builder.BuildSettingsStore
import com.senzaproject.createapp.domain.builder.InstallBlock
import com.senzaproject.createapp.domain.builder.SharedStoragePath
import com.senzaproject.createapp.domain.builder.WorkspaceLayout
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pesan singkat sekali tampil; teksnya ditentukan UI. */
enum class ApkMessage {
    BLOCK_NOT_APK,
    BLOCK_UNSIGNED,
    BLOCK_INVALID,
    BLOCK_NOT_CHECKED,
    BLOCK_MISSING,
    SHARE_BLOCKED,
    EXPORT_DONE,
    DELETE_DONE,
    NEED_INSTALL_PERMISSION,
    NO_APP_FOR_ACTION,
    PATH_COPIED,
    BUSY,
    DETAILS_UNREADABLE,
}

/** Operasi panjang yang sedang berjalan (menyalin file besar), untuk ditampilkan dengan kemajuan nyata. */
enum class ApkBusyKind { PREPARE_INSTALL, PREPARE_SHARE, EXPORT, DETAILS }

class ApkBusy(val kind: ApkBusyKind, val fileName: String, val doneBytes: Long, val totalBytes: Long) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (doneBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

/** Sekali jalan: UI membuat Intent dari ini lalu memanggil [ApkManagerViewModel.consumeLaunch]. */
sealed interface ApkLaunch {
    data class Install(val uri: Uri) : ApkLaunch
    data class Share(val uri: Uri, val mimeType: String, val title: String) : ApkLaunch
}

class ApkExportRequest(val entry: ArtifactEntry, val suggestedName: String) {
    /** Diisi UI setelah pemilih dokumen dibuka, supaya rotasi layar tidak membukanya dua kali. */
    var launched: Boolean = false
}

class ApkDetails(val entry: ArtifactEntry, val info: ApkInfo?)

class ApkManagerViewModel(
    private val manager: ArtifactManager,
    private val stager: ApkStager,
    private val settings: BuildSettingsStore,
    private val observeProjects: ObserveProjectsUseCase,
    private val readInfo: suspend (java.io.File) -> ApkInfo?,
) : ViewModel() {

    var treeUri: String? by mutableStateOf(settings.sharedTreeUri)
        private set

    var snapshot: ArtifactSnapshot? by mutableStateOf(null)
        private set

    var loading: Boolean by mutableStateOf(false)
        private set

    var loadError: AppError? by mutableStateOf(null)
        private set

    var busy: ApkBusy? by mutableStateOf(null)
        private set

    var message: ApkMessage? by mutableStateOf(null)
        private set

    /** Kegagalan operasi (ditampilkan lewat teks kategori error, bukan pesan mentah). */
    var errorMessage: AppError? by mutableStateOf(null)
        private set

    var launch: ApkLaunch? by mutableStateOf(null)
        private set

    var exportRequest: ApkExportRequest? by mutableStateOf(null)
        private set

    var pendingDelete: ArtifactEntry? by mutableStateOf(null)
        private set

    var details: ApkDetails? by mutableStateOf(null)
        private set

    private var refreshJob: Job? = null
    private var verifyJob: Job? = null
    private var operationJob: Job? = null

    /** Path folder bersama yang dilihat Termux/aplikasi File, atau null bila belum dipilih. */
    val sharedPath: String?
        get() = treeUri?.let { SharedStoragePath.fromTreeUri(it) }

    /** Path folder hasil untuk satu artefak: `<folder bersama>/out/<id proyek>`. */
    fun outputPath(entry: ArtifactEntry): String? =
        sharedPath?.let { WorkspaceLayout.outputPath(it, entry.projectId) }

    /** Id dokumen untuk membuka folder hasil di aplikasi File. */
    fun outputDocumentId(entry: ArtifactEntry): String? = outputPath(entry)?.let { SharedStoragePath.documentIdForPath(it) }

    // --- Memuat daftar ---

    fun refresh() {
        val uri = settings.sharedTreeUri
        treeUri = uri
        refreshJob?.cancel()
        verifyJob?.cancel()
        if (uri == null) {
            snapshot = null
            loadError = null
            loading = false
            return
        }
        loading = true
        refreshJob = viewModelScope.launch {
            try {
                val names = observeProjects().first().associate { it.id to it.name }
                when (val result = manager.refresh(uri, names)) {
                    is AppResult.Success -> {
                        snapshot = result.value
                        loadError = null
                        verifyPending(uri)
                    }

                    is AppResult.Failure -> {
                        snapshot = null
                        loadError = result.error
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    /** Memeriksa isi tiap file yang belum diperiksa satu per satu; daftar sudah tampil lebih dulu. */
    private fun verifyPending(uri: String) {
        verifyJob?.cancel()
        val pending = snapshot?.entries?.filter { it.validation == com.senzaproject.createapp.domain.builder.ArtifactValidation.NOT_CHECKED }
            ?: return
        verifyJob = viewModelScope.launch {
            for (entry in pending) {
                val checked = manager.verify(uri, entry)
                val current = snapshot ?: return@launch
                snapshot = current.copy(
                    entries = current.entries.map { if (it.stored == checked.stored) checked else it },
                )
            }
        }
    }

    // --- Pasang dan bagikan ---

    fun install(entry: ArtifactEntry) {
        entry.installBlock?.let {
            message = it.toMessage()
            return
        }
        runStaged(entry, ApkBusyKind.PREPARE_INSTALL) { staged ->
            launch = ApkLaunch.Install(staged.uri)
        }
    }

    fun share(entry: ArtifactEntry) {
        if (!entry.canShare) {
            message = ApkMessage.SHARE_BLOCKED
            return
        }
        runStaged(entry, ApkBusyKind.PREPARE_SHARE) { staged ->
            launch = ApkLaunch.Share(staged.uri, staged.mimeType, entry.name)
        }
    }

    fun showDetails(entry: ArtifactEntry) {
        if (entry.kind != com.senzaproject.createapp.domain.builder.ArtifactKind.APK) {
            // AAB bukan APK: Android tidak bisa membacanya sebagai paket, tampilkan data dari daftar saja.
            details = ApkDetails(entry, null)
            return
        }
        if (!entry.canShare) {
            message = ApkMessage.DETAILS_UNREADABLE
            return
        }
        runStaged(entry, ApkBusyKind.DETAILS) { staged ->
            val info = withContext(Dispatchers.IO) { readInfo(staged.file) }
            details = ApkDetails(entry, info)
            if (info == null) message = ApkMessage.DETAILS_UNREADABLE
        }
    }

    fun closeDetails() {
        details = null
    }

    fun consumeLaunch() {
        launch = null
    }

    fun onNoAppForAction() {
        message = ApkMessage.NO_APP_FOR_ACTION
    }

    fun onNeedInstallPermission() {
        message = ApkMessage.NEED_INSTALL_PERMISSION
    }

    fun onPathCopied() {
        message = ApkMessage.PATH_COPIED
    }

    private fun runStaged(entry: ArtifactEntry, kind: ApkBusyKind, onReady: suspend (com.senzaproject.createapp.apk.StagedArtifact) -> Unit) {
        val uri = treeUri
        if (uri == null) return
        if (busy != null) {
            message = ApkMessage.BUSY
            return
        }
        busy = ApkBusy(kind, entry.name, 0L, entry.sizeBytes)
        operationJob = viewModelScope.launch {
            try {
                when (val result = stager.stage(uri, entry) { done -> updateProgress(done) }) {
                    is AppResult.Success -> onReady(result.value)
                    is AppResult.Failure -> errorMessage = result.error
                }
            } finally {
                busy = null
            }
        }
    }

    // --- Ekspor ---

    /** Menyiapkan permintaan ekspor; UI membuka pemilih dokumen dengan [ApkExportRequest.suggestedName]. */
    fun requestExport(entry: ArtifactEntry) {
        if (!entry.canShare) {
            message = ApkMessage.SHARE_BLOCKED
            return
        }
        if (busy != null) {
            message = ApkMessage.BUSY
            return
        }
        exportRequest = ApkExportRequest(entry, ArtifactRules.exportFileName(entry.projectName, entry.name))
    }

    /** Dipanggil dengan hasil pemilih dokumen; [destination] null berarti dibatalkan. */
    fun finishExport(destination: Uri?) {
        val request = exportRequest ?: return
        exportRequest = null
        val uri = treeUri
        if (destination == null || uri == null) return
        busy = ApkBusy(ApkBusyKind.EXPORT, request.entry.name, 0L, request.entry.sizeBytes)
        operationJob = viewModelScope.launch {
            try {
                when (val result = stager.exportTo(uri, request.entry, destination) { done -> updateProgress(done) }) {
                    is AppResult.Success -> message = ApkMessage.EXPORT_DONE
                    is AppResult.Failure -> errorMessage = result.error
                }
            } finally {
                busy = null
            }
        }
    }

    // --- Hapus (selalu dengan konfirmasi) ---

    fun requestDelete(entry: ArtifactEntry) {
        pendingDelete = entry
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    fun confirmDelete() {
        val entry = pendingDelete ?: return
        pendingDelete = null
        val uri = treeUri ?: return
        viewModelScope.launch {
            when (val result = manager.delete(uri, entry)) {
                is AppResult.Success -> {
                    message = ApkMessage.DELETE_DONE
                    // Salinan sementara file yang sama tidak boleh tertinggal.
                    stager.clear()
                }

                is AppResult.Failure -> errorMessage = result.error
            }
            // Selalu baca ulang: tampilan harus mengikuti keadaan file sebenarnya, berhasil atau tidak.
            refresh()
        }
    }

    // --- Pembatalan operasi dan pesan ---

    fun cancelBusy() {
        operationJob?.cancel()
        busy = null
    }

    fun consumeMessage() {
        message = null
    }

    fun consumeError() {
        errorMessage = null
    }

    private fun updateProgress(done: Long) {
        val current = busy ?: return
        // Hindari pembaruan UI untuk setiap potongan 64 KB.
        if (done == current.totalBytes || done - current.doneBytes >= PROGRESS_STEP) {
            busy = ApkBusy(current.kind, current.fileName, done, current.totalBytes)
        }
    }

    override fun onCleared() {
        super.onCleared()
        operationJob?.cancel()
    }

    private companion object {
        const val PROGRESS_STEP = 256L * 1024
    }
}

private fun InstallBlock.toMessage(): ApkMessage = when (this) {
    InstallBlock.NOT_APK -> ApkMessage.BLOCK_NOT_APK
    InstallBlock.UNSIGNED -> ApkMessage.BLOCK_UNSIGNED
    InstallBlock.INVALID_FILE -> ApkMessage.BLOCK_INVALID
    InstallBlock.NOT_CHECKED -> ApkMessage.BLOCK_NOT_CHECKED
    InstallBlock.MISSING -> ApkMessage.BLOCK_MISSING
}
