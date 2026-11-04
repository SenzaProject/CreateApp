package com.senzaproject.createapp.di

import android.app.Application
import com.senzaproject.createapp.BuildConfig
import com.senzaproject.createapp.R
import com.senzaproject.createapp.core.builder.BuilderModule
import com.senzaproject.createapp.core.common.AndroidLogger
import com.senzaproject.createapp.core.common.AppDispatchers
import com.senzaproject.createapp.core.common.CommonModule
import com.senzaproject.createapp.core.common.CrashReporter
import com.senzaproject.createapp.core.common.DefaultAppDispatchers
import com.senzaproject.createapp.core.common.LogFileSink
import com.senzaproject.createapp.core.editor.EditorModule
import com.senzaproject.createapp.core.git.GitModule
import com.senzaproject.createapp.core.project.EmptyProjectRepository
import com.senzaproject.createapp.core.project.ProjectModule
import com.senzaproject.createapp.core.terminal.TerminalModule
import com.senzaproject.createapp.data.DataModule
import com.senzaproject.createapp.data.diagnostics.AndroidDeviceInfoSource
import com.senzaproject.createapp.data.diagnostics.DiagnosticsRepositoryImpl
import com.senzaproject.createapp.domain.diagnostics.AppMetadata
import com.senzaproject.createapp.domain.diagnostics.DiagnosticsRepository
import com.senzaproject.createapp.domain.diagnostics.GetAppDiagnosticsUseCase
import com.senzaproject.createapp.domain.logging.Logger
import com.senzaproject.createapp.domain.module.DomainModule
import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.ProjectRepository
import java.io.File

/**
 * Dependency injection manual (service locator ringan) yang dibuat sekali di [Application].
 *
 * Dipilih untuk Tahap 1 agar build tetap sederhana dan stabil (tanpa KSP/kapt). Bila graf
 * dependensi sudah terlalu besar, migrasi ke Hilt/Koin dievaluasi di tahap berikutnya.
 */
class AppContainer(application: Application) {

    private val logDirectory = File(application.filesDir, "logs")

    val dispatchers: AppDispatchers = DefaultAppDispatchers

    val logger: Logger = AndroidLogger(
        fileSink = LogFileSink(File(logDirectory, "app.log")),
    )

    val crashReporter = CrashReporter(
        crashFile = File(logDirectory, "last_crash.txt"),
        logger = logger,
    )

    /** Semua modul proyek beserta statusnya yang jujur. */
    val modules: List<ModuleInfo> = listOf(
        ModuleInfo(id = ":app", name = "App", plannedStage = 1, status = ModuleStatus.IN_PROGRESS),
        DomainModule.info,
        DataModule.info,
        CommonModule.info,
        ProjectModule.info,
        EditorModule.info,
        TerminalModule.info,
        BuilderModule.info,
        GitModule.info,
    )

    private val diagnosticsRepository: DiagnosticsRepository = DiagnosticsRepositoryImpl(
        metadata = AppMetadata(
            appName = application.getString(R.string.app_name),
            packageName = application.packageName,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            isDebug = BuildConfig.DEBUG,
        ),
        deviceInfoSource = AndroidDeviceInfoSource(),
        modules = modules,
        ioDispatcher = dispatchers.io,
    )

    val getAppDiagnostics = GetAppDiagnosticsUseCase(diagnosticsRepository)

    /** Sementara kosong sampai penyimpanan proyek nyata dibuat pada Tahap 3. */
    private val projectRepository: ProjectRepository = EmptyProjectRepository()

    val observeProjects = ObserveProjectsUseCase(projectRepository)
}
