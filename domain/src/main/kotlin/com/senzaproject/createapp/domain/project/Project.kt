package com.senzaproject.createapp.domain.project

/** Proyek Android yang dikelola Create App. Penyimpanan nyata dikerjakan pada Tahap 3. */
data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val lastModifiedMillis: Long,
    val sizeBytes: Long,
    val isFavorite: Boolean = false,
)
