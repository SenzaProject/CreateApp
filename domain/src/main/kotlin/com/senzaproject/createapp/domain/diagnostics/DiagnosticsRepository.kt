package com.senzaproject.createapp.domain.diagnostics

import com.senzaproject.createapp.domain.result.AppResult

interface DiagnosticsRepository {
    suspend fun load(): AppResult<AppDiagnostics>
}
