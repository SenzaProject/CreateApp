package com.senzaproject.createapp.domain.project

import com.senzaproject.createapp.domain.result.AppResult

/** Operasi file dan folder di dalam satu proyek. Semua path relatif terhadap folder proyek. */
interface ProjectFilesRepository {
    /** Isi folder [dir] ("" = akar proyek): folder lebih dulu, lalu file, urut nama. */
    suspend fun list(projectId: String, dir: String): AppResult<List<FileNode>>

    /** Membuat file baru dengan isi awal sesuai ekstensinya (lihat [initialContentFor]). Gagal bila sudah ada. */
    suspend fun createFile(projectId: String, dir: String, name: String): AppResult<FileNode>

    suspend fun createFolder(projectId: String, dir: String, name: String): AppResult<FileNode>

    suspend fun rename(projectId: String, path: String, newName: String): AppResult<FileNode>

    /** Menghapus file atau folder (rekursif). Pemanggil wajib meminta konfirmasi pengguna lebih dulu. */
    suspend fun delete(projectId: String, path: String): AppResult<Unit>

    suspend fun readText(projectId: String, path: String): AppResult<String>

    /** Menulis seluruh isi file secara atomik: file lama tetap utuh bila penulisan gagal di tengah jalan. */
    suspend fun writeText(projectId: String, path: String, content: String): AppResult<Unit>
}

/** Batas ukuran file teks yang dapat dibuka/disimpan di Tahap 3 (editor besar: Tahap 4). */
const val MAX_TEXT_FILE_BYTES: Long = 1_048_576L
