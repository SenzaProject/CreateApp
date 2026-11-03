package com.senzaproject.createapp.core.terminal

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Kerangka modul Terminal. BELUM ada fitur yang diimplementasikan di sini:
 * modul ini baru dihubungkan ke proyek pada Tahap 1 dan diisi pada Tahap 5.
 */
object TerminalModule {
    val info = ModuleInfo(
        id = ":core:terminal",
        name = "Terminal",
        plannedStage = 5,
        status = ModuleStatus.PLANNED,
    )
}
