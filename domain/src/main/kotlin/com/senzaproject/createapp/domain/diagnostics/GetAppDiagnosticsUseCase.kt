package com.senzaproject.createapp.domain.diagnostics

import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.map

/** Mengambil diagnostik aplikasi dengan daftar modul terurut berdasarkan tahap lalu id. */
class GetAppDiagnosticsUseCase(
    private val repository: DiagnosticsRepository,
) {
    suspend operator fun invoke(): AppResult<AppDiagnostics> =
        repository.load().map { diagnostics ->
            diagnostics.copy(
                modules = diagnostics.modules.sortedWith(
                    compareBy({ it.plannedStage }, { it.id }),
                ),
            )
        }
}
