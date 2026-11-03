package com.senzaproject.createapp.core.editor

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Kerangka modul Editor. BELUM ada fitur yang diimplementasikan di sini:
 * modul ini baru dihubungkan ke proyek pada Tahap 1 dan diisi pada Tahap 4.
 */
object EditorModule {
    val info = ModuleInfo(
        id = ":core:editor",
        name = "Editor",
        plannedStage = 4,
        status = ModuleStatus.PLANNED,
    )
}
