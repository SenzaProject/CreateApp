package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppResult

data class SyncedWorkspace(val files: Int, val bytes: Long)

/**
 * Pembaca log Gradle yang ditulis helper Termux ke folder bersama selama build berjalan. Termux sendiri
 * hanya mengirim keluaran perintah setelah perintah selesai, jadi file inilah satu-satunya sumber
 * kemajuan langsung.
 */
interface LiveLogSource {
    /**
     * Teks baru sejak pemanggilan sebelumnya. Hanya baris lengkap yang dikembalikan, kecuali [flush]
     * true (sisa tanpa baris baru ikut). String kosong bila belum ada isi baru; null bila file belum
     * ada atau tidak dapat dibaca. Tidak pernah melempar pengecualian (selain pembatalan coroutine).
     */
    suspend fun readNew(flush: Boolean): String?
}

/** Pembaca yang tidak pernah mendapat isi: build tetap berjalan, log hanya muncul setelah selesai. */
object NoLiveLog : LiveLogSource {
    override suspend fun readNew(flush: Boolean): String? = null
}

/**
 * Jembatan antara proyek privat Create App dan Termux: menyalin proyek ke folder bersama yang dipilih
 * pengguna, dan memeriksa artefak hasil build di folder itu. Implementasi memakai Storage Access Framework.
 */
interface BuildWorkspace {
    /** Mengganti isi `src/<projectId>` di folder bersama dengan isi proyek saat ini. */
    suspend fun sync(projectId: String, treeUri: String): AppResult<SyncedWorkspace>

    /**
     * Menyiapkan pembacaan log langsung `logs/<projectId>.log`. File lama dihapus lebih dulu supaya isi
     * build sebelumnya tidak terbaca sebagai milik build ini. Kegagalan apa pun menghasilkan [NoLiveLog].
     */
    suspend fun openLiveLog(treeUri: String, projectId: String): LiveLogSource = NoLiveLog

    /**
     * Memeriksa tiap artefak yang dilaporkan helper di `out/<projectId>`: ada, ukurannya sama, dan
     * struktur APK/AAB-nya valid. Mengembalikan daftar yang sama dengan [BuildArtifact.validation] terisi.
     */
    suspend fun verifyArtifacts(
        treeUri: String,
        projectId: String,
        reported: List<BuildArtifact>,
    ): AppResult<List<BuildArtifact>>
}
