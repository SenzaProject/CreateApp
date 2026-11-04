package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Repository sementara: penyimpanan proyek BELUM ada (Tahap 3), sehingga selalu mengembalikan
 * daftar kosong. Dipakai agar UI Tahap 2 tampil jujur tanpa data palsu. Akan diganti
 * implementasi berbasis penyimpanan nyata pada Tahap 3.
 */
class EmptyProjectRepository : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
}
