package com.senzaproject.createapp.domain.project

import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    /** Daftar proyek yang tersedia; emisi baru setiap kali daftar berubah. */
    fun observeProjects(): Flow<List<Project>>

    suspend fun getProject(id: String): AppResult<Project>

    suspend fun createProject(request: NewProjectRequest): AppResult<Project>

    /** Mengganti nama tampilan proyek. Id dan folder proyek tidak berubah. */
    suspend fun renameProject(id: String, newName: String): AppResult<Project>

    suspend fun setFavorite(id: String, favorite: Boolean): AppResult<Unit>

    /** Menghapus proyek beserta seluruh isinya. Pemanggil wajib meminta konfirmasi pengguna lebih dulu. */
    suspend fun deleteProject(id: String): AppResult<Unit>
}
