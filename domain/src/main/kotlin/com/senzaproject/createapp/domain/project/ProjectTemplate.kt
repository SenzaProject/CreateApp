package com.senzaproject.createapp.domain.project

/** Template awal proyek. Template lain dari Prompt Master dijadwalkan pada versi berikutnya. */
enum class ProjectTemplate {
    /** Folder kosong dengan README.md. */
    EMPTY,

    /** Kerangka proyek Android (Gradle Kotlin DSL + satu Activity). Build-nya baru diuji pada Tahap 6. */
    ANDROID_KOTLIN,
}

/** Permintaan membuat proyek. [packageName] wajib valid untuk [ProjectTemplate.ANDROID_KOTLIN]. */
data class NewProjectRequest(
    val name: String,
    val packageName: String,
    val template: ProjectTemplate,
)
