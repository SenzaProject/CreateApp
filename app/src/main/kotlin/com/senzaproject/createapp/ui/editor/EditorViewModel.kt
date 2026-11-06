package com.senzaproject.createapp.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.project.ProjectFilesRepository
import com.senzaproject.createapp.domain.project.nameOf
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Pesan satu kali untuk pengguna (ditampilkan sebagai snackbar). */
sealed interface EditorMessage {
    data object Saved : EditorMessage
    data class SaveFailed(val error: AppError) : EditorMessage
    data class LoadFailed(val error: AppError) : EditorMessage
}

/**
 * Menyimpan buffer teks editor selama proses aplikasi hidup (bertahan saat pindah layar dan rotasi).
 *
 * Dua mode:
 * - Buffer sementara (dari Beranda): teks tidak disimpan ke file.
 * - File proyek (dari File Explorer, lewat [openProjectFile]): teks dimuat dari file dan dapat disimpan
 *   dengan [save]. Penyimpanan otomatis dan undo/redo dikerjakan pada Tahap 4.
 *
 * Buffer sementara disimpan terpisah, sehingga membuka lalu menutup file tidak menghapusnya.
 */
class EditorViewModel(
    private val files: ProjectFilesRepository,
) : ViewModel() {

    /** File yang sedang dibuka; [path] relatif terhadap proyek. */
    data class OpenFile(val projectId: String, val path: String) {
        val name: String get() = nameOf(path)
    }

    // State Compose (sinkron) agar kursor dan pengetikan tidak terganggu pembaruan asinkron.
    var value by mutableStateOf(TextFieldValue(""))
        private set

    var openFile by mutableStateOf<OpenFile?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var message by mutableStateOf<EditorMessage?>(null)
        private set

    /** Teks yang terakhir diketahui sama dengan isi file di penyimpanan. */
    private var savedText by mutableStateOf("")

    private var scratch = TextFieldValue("")
    private var loadJob: Job? = null

    /** Ada perubahan pada file yang belum disimpan. Selalu false pada buffer sementara. */
    val isDirty: Boolean get() = openFile != null && value.text != savedText

    fun onValueChange(newValue: TextFieldValue) {
        if (isLoading) return
        value = newValue
    }

    /** Membuka file proyek; buffer sementara disimpan dan dipulihkan oleh [closeFile]. */
    fun openProjectFile(projectId: String, path: String) {
        val target = OpenFile(projectId, path)
        if (openFile == target) return
        if (openFile == null) scratch = value
        loadJob?.cancel()
        openFile = target
        value = TextFieldValue("")
        savedText = ""
        isLoading = true
        loadJob = viewModelScope.launch {
            try {
                when (val result = files.readText(projectId, path)) {
                    is AppResult.Success -> {
                        value = TextFieldValue(result.value)
                        savedText = result.value
                    }

                    is AppResult.Failure -> {
                        message = EditorMessage.LoadFailed(result.error)
                        restoreScratch()
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    /** Menyimpan file yang terbuka. [onSaved] dipanggil hanya bila penyimpanan berhasil. */
    fun save(onSaved: () -> Unit = {}) {
        val file = openFile ?: return
        if (isSaving || isLoading) return
        val text = value.text
        viewModelScope.launch {
            isSaving = true
            try {
                when (val result = files.writeText(file.projectId, file.path, text)) {
                    is AppResult.Success -> {
                        savedText = text
                        message = EditorMessage.Saved
                        onSaved()
                    }

                    is AppResult.Failure -> message = EditorMessage.SaveFailed(result.error)
                }
            } finally {
                isSaving = false
            }
        }
    }

    /** Menutup file dan mengembalikan buffer sementara. Perubahan yang belum disimpan dibuang. */
    fun closeFile() {
        if (openFile == null) return
        loadJob?.cancel()
        isLoading = false
        restoreScratch()
    }

    fun consumeMessage() {
        message = null
    }

    private fun restoreScratch() {
        openFile = null
        value = scratch
        savedText = ""
    }
}
