package com.senzaproject.createapp.domain.apk

import com.senzaproject.createapp.domain.result.AppResult

/**
 * Akses ke APK/AAB hasil build di folder bersama. Implementasi memakai Storage Access Framework
 * (`:core:builder`). Semua fungsi bekerja pada keadaan file SAAT INI; tidak ada cache yang bisa usang.
 */
interface ArtifactStore {
    /** Semua APK/AAB yang ada di `out/<id proyek>/`. Folder `out` yang belum ada menghasilkan daftar kosong. */
    suspend fun list(treeUri: String): AppResult<List<ManagedArtifact>>

    /** Memeriksa keutuhan ZIP, menghitung SHA-256, dan (untuk APK) membaca metadata dari file. */
    suspend fun inspect(treeUri: String, artifact: ManagedArtifact): AppResult<ArtifactDetails>

    /** Menyalin ke cache privat untuk dipasang/dibagikan. Gagal bila ukuran salinan tidak sama dengan sumber. */
    suspend fun stage(treeUri: String, artifact: ManagedArtifact): AppResult<StagedArtifact>

    /** Menyalin ke lokasi pilihan pengguna ([destinationUri]); mengembalikan jumlah byte yang ditulis. */
    suspend fun export(treeUri: String, artifact: ManagedArtifact, destinationUri: String): AppResult<Long>

    /** Menghapus file (dan salinan cache-nya). Folder proyek yang menjadi kosong ikut dihapus. */
    suspend fun delete(treeUri: String, artifact: ManagedArtifact): AppResult<Unit>
}
