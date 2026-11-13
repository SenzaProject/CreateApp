package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppResult

data class SyncedWorkspace(val files: Int, val bytes: Long)

/**
 * Jembatan antara proyek privat Create App dan Termux: menyalin proyek ke folder bersama yang dipilih
 * pengguna, dan memeriksa artefak hasil build di folder itu. Implementasi memakai Storage Access Framework.
 */
interface BuildWorkspace {
    /** Mengganti isi `src/<projectId>` di folder bersama dengan isi proyek saat ini. */
    suspend fun sync(projectId: String, treeUri: String): AppResult<SyncedWorkspace>

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
