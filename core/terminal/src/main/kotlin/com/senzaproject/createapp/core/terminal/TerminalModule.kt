package com.senzaproject.createapp.core.terminal

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Modul Terminal (Tahap 5): shell lokal Android dan integrasi Termux lewat `RUN_COMMAND`.
 * Status IN_PROGRESS (bukan READY) sampai CI hijau dan uji perangkat lulus.
 */
object TerminalModule {
    val info = ModuleInfo(
        id = ":core:terminal",
        name = "Terminal",
        plannedStage = 5,
        status = ModuleStatus.IN_PROGRESS,
    )
}
