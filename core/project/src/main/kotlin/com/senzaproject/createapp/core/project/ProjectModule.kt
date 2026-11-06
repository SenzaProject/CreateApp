package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Modul Project (Tahap 3): penyimpanan proyek berbasis folder, operasi file, serta impor/ekspor SAF.
 * Statusnya IN_PROGRESS sampai kriteria Tahap 3 terbukti di perangkat (lihat docs/TAHAP-3.md).
 */
object ProjectModule {
    val info = ModuleInfo(
        id = ":core:project",
        name = "Project",
        plannedStage = 3,
        status = ModuleStatus.IN_PROGRESS,
    )
}
