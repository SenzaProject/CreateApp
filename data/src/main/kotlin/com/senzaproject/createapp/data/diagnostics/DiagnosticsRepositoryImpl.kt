package com.senzaproject.createapp.data.diagnostics

import com.senzaproject.createapp.domain.diagnostics.AppDiagnostics
import com.senzaproject.createapp.domain.diagnostics.AppMetadata
import com.senzaproject.createapp.domain.diagnostics.DiagnosticsRepository
import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.runCatchingApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class DiagnosticsRepositoryImpl(
    private val metadata: AppMetadata,
    private val deviceInfoSource: DeviceInfoSource,
    private val modules: List<ModuleInfo>,
    private val ioDispatcher: CoroutineDispatcher,
) : DiagnosticsRepository {

    override suspend fun load(): AppResult<AppDiagnostics> = withContext(ioDispatcher) {
        runCatchingApp {
            AppDiagnostics(
                app = metadata,
                device = deviceInfoSource.read(),
                modules = modules,
            )
        }
    }
}
