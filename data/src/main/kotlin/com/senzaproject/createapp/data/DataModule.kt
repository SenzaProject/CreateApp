package com.senzaproject.createapp.data

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus

object DataModule {
    val info = ModuleInfo(
        id = ":data",
        name = "Data",
        plannedStage = 1,
        status = ModuleStatus.IN_PROGRESS,
    )
}
