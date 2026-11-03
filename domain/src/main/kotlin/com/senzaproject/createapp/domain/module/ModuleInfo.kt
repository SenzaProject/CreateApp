package com.senzaproject.createapp.domain.module

/** Status implementasi modul. READY hanya jika fiturnya benar-benar berjalan dan sudah teruji di perangkat. */
enum class ModuleStatus { PLANNED, IN_PROGRESS, READY }

/**
 * Deskripsi modul untuk diagnostik.
 *
 * @property id path modul Gradle, mis. ":core:editor"
 * @property name nama singkat modul
 * @property plannedStage nomor tahap di roadmap tempat modul diimplementasikan
 */
data class ModuleInfo(
    val id: String,
    val name: String,
    val plannedStage: Int,
    val status: ModuleStatus,
)

/** Info modul :domain sendiri. */
object DomainModule {
    val info = ModuleInfo(
        id = ":domain",
        name = "Domain",
        plannedStage = 1,
        status = ModuleStatus.IN_PROGRESS,
    )
}
