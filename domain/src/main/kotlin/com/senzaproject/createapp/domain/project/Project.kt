package com.senzaproject.createapp.domain.project

/**
 * Proyek yang dikelola Create App.
 *
 * [id] adalah nama folder proyek (huruf, angka, `_`, `-`) dan tidak berubah saat proyek diganti nama.
 * [packageName] boleh kosong untuk proyek non-Android atau hasil impor tanpa package yang terdeteksi.
 */
data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val lastModifiedMillis: Long,
    val sizeBytes: Long,
    val isFavorite: Boolean = false,
)
