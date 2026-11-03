package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Kerangka modul Project. BELUM ada fitur yang diimplementasikan di sini:
 * modul ini baru dihubungkan ke proyek pada Tahap 1 dan diisi pada Tahap 3.
 */
object ProjectModule {
    val info = ModuleInfo(
        id = ":core:project",
        name = "Project",
        plannedStage = 3,
        status = ModuleStatus.PLANNED,
    )
}
