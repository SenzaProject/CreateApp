package com.senzaproject.createapp.di

import android.app.Application
import android.os.Build
import com.senzaproject.createapp.BuildConfig
import com.senzaproject.createapp.R
import com.senzaproject.createapp.core.builder.BuilderModule
import com.senzaproject.createapp.core.builder.FileBuildHistoryStore
import com.senzaproject.createapp.core.builder.SharedPrefsBuildSettingsStore
import com.senzaproject.createapp.core.common.AndroidLogger
import com.senzaproject.createapp.core.common.AppDispatchers
import com.senzaproject.createapp.core.common.CommonModule
import com.senzaproject.createapp.core.common.CrashReporter
import com.senzaproject.createapp.core.common.DefaultAppDispatchers
import com.senzaproject.createapp.core.common.LogFileSink
import com.senzaproject.createapp.core.editor.EditorModule
import com.senzaproject.createapp.core.git.GitModule
import com.senzaproject.createapp.core.project.FileProjectFilesRepository
import com.senzaproject.createapp.core.project.FileProjectRepository
import com.senzaproject.createapp.core.project.ProjectModule
import com.senzaproject.createapp.core.project.ProjectStorage
import com.senzaproject.createapp.core.project.SafArtifactStore
import com.senzaproject.createapp.core.project.SafBuildLiveLog
import com.senzaproject.createapp.core.project.SafBuildWorkspace
import com.senzaproject.createapp.core.project.SafProjectTransfer
import com.senzaproject.createapp.core.terminal.AndroidTermuxEnvironment
import com.senzaproject.createapp.core.terminal.LocalShellRunner
import com.senzaproject.createapp.core.terminal.TerminalModule
import com.senzaproject.createapp.core.terminal.TermuxCommandRunner
import com.senzaproject.createapp.core.terminal.TermuxPaths
import com.senzaproject.createapp.data.DataModule
import com.senzaproject.createapp.data.diagnostics.AndroidDeviceInfoSource
import com.senzaproject.createapp.apk.ApkInfo
import com.senzaproject.createapp.apk.ApkInfoReader
import com.senzaproject.createapp.apk.ApkStager
import com.senzaproject.createapp.data.diagnostics.DiagnosticsRepositoryImpl
import com.senzaproject.createapp.domain.builder.ArtifactManager
import com.senzaproject.createapp.domain.builder.ArtifactStore
import com.senzaproject.createapp.domain.builder.BuildCommandFactory
import com.senzaproject.createapp.domain.builder.BuildHistoryStore
import com.senzaproject.createapp.domain.builder.BuildLiveLogSource
import com.senzaproject.createapp.domain.builder.BuildSession
import com.senzaproject.createapp.domain.builder.BuildSettingsStore
import com.senzaproject.createapp.domain.builder.BuildWorkspace
import com.senzaproject.createapp.domain.diagnostics.AppMetadata
import com.senzaproject.createapp.domain.diagnostics.DiagnosticsRepository
import com.senzaproject.createapp.domain.diagnostics.GetAppDiagnosticsUseCase
import com.senzaproject.createapp.domain.logging.Logger
import com.senzaproject.createapp.domain.module.DomainModule
import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus
import com.senzaproject.createapp.domain.project.ObserveProjectsUseCase
import com.senzaproject.createapp.domain.project.ProjectFilesRepository
import com.senzaproject.createapp.domain.project.ProjectRepository
import com.senzaproject.createapp.domain.project.ProjectTransferRepository
import com.senzaproject.createapp.domain.terminal.DirectoryChecker
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import com.senzaproject.createapp.domain.terminal.TerminalSession
import com.senzaproject.createapp.domain.terminal.TermuxEnvironment
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

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

    /** Proyek disimpan di penyimpanan privat aplikasi (`filesDir/projects`): tanpa izin penyimpanan luas. */
    private val projectStorage = ProjectStorage(root = File(application.filesDir, "projects"))

    val projectRepository: ProjectRepository = FileProjectRepository(
        storage = projectStorage,
        ioDispatcher = dispatchers.io,
        logger = logger,
    )

    val projectFiles: ProjectFilesRepository = FileProjectFilesRepository(
        storage = projectStorage,
        ioDispatcher = dispatchers.io,
    )

    /** Impor/ekspor folder lewat Storage Access Framework. */
    val projectTransfer: ProjectTransferRepository = SafProjectTransfer(
        context = application,
        storage = projectStorage,
        ioDispatcher = dispatchers.io,
    )

    val observeProjects = ObserveProjectsUseCase(projectRepository)

    /** Path folder proyek untuk dipakai sebagai direktori kerja shell lokal. Melempar bila id tidak valid. */
    fun projectDirectoryPath(projectId: String): String = projectStorage.projectDir(projectId).absolutePath

    // --- Terminal (Tahap 5) ---

    val termuxEnvironment: TermuxEnvironment = AndroidTermuxEnvironment(application)

    /** Runner Termux untuk Terminal (batas tunggu bawaan 30 menit). */
    private val termuxRunner = TermuxCommandRunner(
        context = application,
        environment = termuxEnvironment,
    )

    private val appHomeDirectory: String = application.filesDir.absolutePath

    /**
     * Satu sesi terminal untuk seluruh proses aplikasi sehingga scrollback dan riwayat bertahan saat
     * berpindah layar. Shell lokal memakai `filesDir` sebagai awal; Termux memakai home Termux.
     */
    val terminalSession = TerminalSession(
        runners = mapOf(
            TerminalBackend.LOCAL_SHELL to LocalShellRunner(
                homeDirectory = appHomeDirectory,
                // ProcessHandle (untuk mematikan proses turunan) baru ada di Android 13.
                killDescendants = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            ),
            TerminalBackend.TERMUX to termuxRunner,
        ),
        homeDirectories = mapOf(
            TerminalBackend.LOCAL_SHELL to appHomeDirectory,
            TerminalBackend.TERMUX to TermuxPaths.HOME,
        ),
        // Sistem file Termux tidak bisa dibaca dari aplikasi ini, jadi `cd` di Termux tidak divalidasi di sini.
        directoryChecker = DirectoryChecker { backend, path ->
            backend == TerminalBackend.TERMUX || File(path).isDirectory
        },
    )

    // --- Build Gradle (Tahap 6) ---

    val buildSettings: BuildSettingsStore = SharedPrefsBuildSettingsStore(application)

    val buildHistory: BuildHistoryStore = FileBuildHistoryStore(
        directory = File(application.filesDir, "builds"),
        ioDispatcher = dispatchers.io,
    )

    /** Penyalinan proyek ke folder bersama untuk Termux, dan pemeriksaan APK/AAB hasil build. */
    val buildWorkspace: BuildWorkspace = SafBuildWorkspace(
        context = application,
        storage = projectStorage,
        ioDispatcher = dispatchers.io,
    )

    /** Log langsung yang ditulis helper Termux ke folder bersama selama build (dibaca, tidak pernah ditulis). */
    private val buildLiveLog: BuildLiveLogSource = SafBuildLiveLog(
        context = application,
        ioDispatcher = dispatchers.io,
    )

    // --- APK Manager (Tahap 7) ---

    /** Membaca, menghapus, dan menyalin APK/AAB di folder `out/` pada folder bersama. */
    val artifactStore: ArtifactStore = SafArtifactStore(
        context = application,
        ioDispatcher = dispatchers.io,
    )

    val artifactManager: ArtifactManager = ArtifactManager(
        store = artifactStore,
        history = buildHistory,
    )

    /** Salinan sementara untuk dipasang/dibagikan lewat FileProvider, dan ekspor ke tujuan pilihan pengguna. */
    val apkStager: ApkStager = ApkStager(
        context = application,
        manager = artifactManager,
        ioDispatcher = dispatchers.io,
    )

    val readApkInfo: suspend (File) -> ApkInfo? = { file -> ApkInfoReader.read(application, file) }

    /** Helper shell yang dipasang ke Termux; dibaca dari aset hanya saat pertama dibutuhkan. */
    private val buildCommands: BuildCommandFactory by lazy {
        val script = application.assets.open(BUILD_HELPER_ASSET).use { it.readBytes() }
        BuildCommandFactory(Base64.getEncoder().encodeToString(script))
    }

    /**
     * Satu sesi build untuk seluruh proses aplikasi, dengan scope sendiri agar build tidak ikut
     * berhenti saat layar berganti. Batas tunggu Termux dinaikkan ke 2 jam karena build Gradle di
     * ponsel (terutama yang pertama, mengunduh dependensi) bisa jauh lebih lama dari perintah biasa.
     */
    val buildSession: BuildSession by lazy {
        BuildSession(
            runner = TermuxCommandRunner(
                context = application,
                environment = termuxEnvironment,
                maxWaitMillis = BUILD_MAX_WAIT_MILLIS,
            ),
            workspace = buildWorkspace,
            commands = buildCommands,
            history = buildHistory,
            scope = CoroutineScope(SupervisorJob() + dispatchers.default),
            termuxHome = TermuxPaths.HOME,
            liveLog = buildLiveLog,
        )
    }

    private companion object {
        const val BUILD_HELPER_ASSET = "createapp-build.sh"
        const val BUILD_MAX_WAIT_MILLIS = 2L * 60 * 60 * 1000
    }
}
