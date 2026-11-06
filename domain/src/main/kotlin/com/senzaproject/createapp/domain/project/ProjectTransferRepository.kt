package com.senzaproject.createapp.domain.project

import com.senzaproject.createapp.domain.result.AppResult

/**
 * Impor/ekspor proyek lewat Storage Access Framework. URI diteruskan sebagai String agar
 * lapisan domain tetap bebas dari Android.
 */
interface ProjectTransferRepository {
    /** Menyalin isi folder yang dipilih pengguna ([treeUri]) menjadi proyek baru di penyimpanan aplikasi. */
    suspend fun importFromTree(treeUri: String): AppResult<Project>

    /** Menyalin proyek ke folder yang dipilih pengguna. Mengembalikan jumlah file yang disalin. */
    suspend fun exportToTree(projectId: String, treeUri: String): AppResult<Int>
}
