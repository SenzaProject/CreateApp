package com.senzaproject.createapp.domain.project

import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    /** Daftar proyek yang tersedia; emisi baru setiap kali daftar berubah. */
    fun observeProjects(): Flow<List<Project>>
}
