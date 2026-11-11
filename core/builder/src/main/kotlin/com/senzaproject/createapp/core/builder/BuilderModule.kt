package com.senzaproject.createapp.core.builder

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Modul Builder (Tahap 6): penyimpanan riwayat build dan pengaturan folder bersama. Logika build
 * (sesi, parser log, perintah) ada di `:domain` (paket `builder`); proses Gradle dijalankan di Termux.
 * Status IN_PROGRESS sampai CI hijau dan uji perangkat di docs/TAHAP-6.md lulus.
 */
object BuilderModule {
    val info = ModuleInfo(
        id = ":core:builder",
        name = "Builder",
        plannedStage = 6,
        status = ModuleStatus.IN_PROGRESS,
    )
}
