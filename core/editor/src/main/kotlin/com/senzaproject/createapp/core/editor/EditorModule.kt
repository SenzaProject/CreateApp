package com.senzaproject.createapp.core.editor

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

/**
 * Modul Editor. Tahap 2: hanya metrik teks untuk editor awal (posisi kursor, jumlah baris).
 * Syntax highlighting, auto-indent, undo/redo, dan multi-tab dikerjakan pada Tahap 4.
 */
object EditorModule {
    val info = ModuleInfo(
        id = ":core:editor",
        name = "Editor",
        plannedStage = 4,
        status = ModuleStatus.IN_PROGRESS,
    )
}
