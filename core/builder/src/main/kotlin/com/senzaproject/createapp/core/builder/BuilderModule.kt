package com.senzaproject.createapp.core.builder

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Kerangka modul Builder. BELUM ada fitur yang diimplementasikan di sini:
 * modul ini baru dihubungkan ke proyek pada Tahap 1 dan diisi pada Tahap 6.
 */
object BuilderModule {
    val info = ModuleInfo(
        id = ":core:builder",
        name = "Builder",
        plannedStage = 6,
        status = ModuleStatus.PLANNED,
    )
}
