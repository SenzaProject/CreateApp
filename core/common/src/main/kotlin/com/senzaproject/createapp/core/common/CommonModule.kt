package com.senzaproject.createapp.core.common

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

object CommonModule {
    val info = ModuleInfo(
        id = ":core:common",
        name = "Common",
        plannedStage = 1,
        status = ModuleStatus.IN_PROGRESS,
    )
}
