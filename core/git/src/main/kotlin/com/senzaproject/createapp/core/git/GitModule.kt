package com.senzaproject.createapp.core.git

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Kerangka modul Git. BELUM ada fitur yang diimplementasikan di sini:
 * modul ini baru dihubungkan ke proyek pada Tahap 1 dan diisi pada Tahap 9.
 */
object GitModule {
    val info = ModuleInfo(
        id = ":core:git",
        name = "Git",
        plannedStage = 9,
        status = ModuleStatus.PLANNED,
    )
}
