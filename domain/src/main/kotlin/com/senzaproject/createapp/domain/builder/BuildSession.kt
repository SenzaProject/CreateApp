package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.CommandRequest
import com.senzaproject.createapp.domain.terminal.CommandRunner
import com.senzaproject.createapp.domain.terminal.LineAssembler
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEFAULT_CANCEL_GRACE_MILLIS = 60_000L

sealed interface ToolchainResult {
    data class Report(val report: ToolchainReport) : ToolchainResult
    data class Failed(val failure: TerminalFailure, val detail: String?) : ToolchainResult
}

sealed interface CleanResult {
    data object Done : CleanResult
    data object Busy : CleanResult
    data class Failed(val failure: TerminalFailure, val detail: String?) : CleanResult
}

/** Penampung hasil akhir perintah; dipakai agar lambda `collect` tidak perlu menugaskan variabel luar. */
private class RunOutcome {
    var end: RunEnd = RunEnd.Stopped
}

private class HelperRun(val messages: List<HelperMessage>, val end: RunEnd)

/**
 * Satu build pada satu waktu: menyalin proyek ke folder bersama, menjalankan helper Gradle di Termux,
 * memeriksa artefak, dan mencatat riwayat. Status akhir ditentukan oleh kode keluar proses nyata dan
 * pemeriksaan file, tidak pernah oleh animasi atau perkiraan.
 *
 * Berjalan di [scope] (berskala aplikasi) sehingga build tidak terhenti saat layar berganti.
 * [start] dan [cancel] aman dipanggil dari thread mana pun.
 *
 * Pembatalan: mengirim perintah `cancel` ke Termux (mematikan pohon proses Gradle) lalu menunggu hasil
 * akhir. Bila Termux tidak membalas dalam [cancelGraceMillis], aplikasi berhenti menunggu dan build
 * ditandai "mungkin masih berjalan" (lihat [BuildState.mayStillRun]).
 */
class BuildSession(
    private val runner: CommandRunner,
    private val workspace: BuildWorkspace,
    private val commands: BuildCommandFactory,
    private val history: BuildHistoryStore,
    private val scope: CoroutineScope,
    private val termuxHome: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val cancelGraceMillis: Long = DEFAULT_CANCEL_GRACE_MILLIS,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Log langsung dari folder bersama; null = tidak dipakai (hanya hasil akhir yang tampil). */
    private val liveLog: BuildLiveLogSource? = null,
    private val liveIntervalMillis: Long = DEFAULT_LIVE_LOG_INTERVAL_MILLIS,
    private val newRunToken: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(BuildState())

    val state: StateFlow<BuildState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var collector: Job? = null
    private var cancelRequested = false

    /** Memulai build. Mengembalikan false bila masih ada build yang berjalan. */
    fun start(request: BuildRequest): Boolean {
        synchronized(lock) {
            if (mutableState.value.isActive) return false
            cancelRequested = false
            collector = null
            mutableState.value = BuildState(
                status = BuildStatus.RUNNING,
                request = request,
                phase = BuildPhase.SYNC,
                startedAtMillis = clock(),
            )
            job = scope.launch { execute(request) }
        }
        return true
    }

    /** Meminta pembatalan. Tidak berpengaruh bila tidak ada build yang berjalan. */
    fun cancel() {
        val pair = synchronized(lock) {
            if (!mutableState.value.isActive || cancelRequested) {
                null
            } else {
                cancelRequested = true
                mutableState.update { it.copy(status = BuildStatus.CANCELLING) }
                Pair(collector, job)
            }
        } ?: return

        val activeCollector = pair.first
        if (activeCollector == null) {
            // Masih menyalin proyek: cukup batalkan coroutine-nya.
            pair.second?.cancel()
            return
        }
        scope.launch { sendCancelCommand() }
        scope.launch {
            delay(cancelGraceMillis)
            activeCollector.cancel()
        }
    }

    /** Mengosongkan hasil build yang sudah selesai agar layar kembali ke keadaan awal. */
    fun reset() {
        synchronized(lock) {
            if (!mutableState.value.isActive) mutableState.value = BuildState()
        }
    }

    /** Memeriksa lingkungan build di Termux. Tidak mengubah apa pun. */
    suspend fun checkToolchain(sourcePath: String?): ToolchainResult {
        val run = runHelper(commands.check(sourcePath))
        return when (val end = run.end) {
            is RunEnd.Failed -> ToolchainResult.Failed(end.failure, end.detail)
            RunEnd.Stopped -> ToolchainResult.Failed(TerminalFailure.TIMEOUT, null)
            is RunEnd.Finished -> {
                val report = ToolchainReport.fromMessages(run.messages)
                if (report.items.isEmpty()) {
                    ToolchainResult.Failed(TerminalFailure.TERMUX_NO_RESULT, null)
                } else {
                    ToolchainResult.Report(report)
                }
            }
        }
    }

    /** Menghapus folder kerja build di Termux (salinan proyek dan hasil kompilasi) untuk menghemat ruang. */
    suspend fun cleanTermuxWork(): CleanResult {
        val run = runHelper(commands.clean())
        val end = run.end
        if (end is RunEnd.Failed) return CleanResult.Failed(end.failure, end.detail)
        if (end is RunEnd.Stopped) return CleanResult.Failed(TerminalFailure.TIMEOUT, null)
        if (run.messages.any { it is HelperMessage.Error && it.error == HelperError.ALREADY_RUNNING }) {
            return CleanResult.Busy
        }
        if (run.messages.any { it is HelperMessage.CleanOk }) return CleanResult.Done
        return CleanResult.Failed(TerminalFailure.TERMUX_NO_RESULT, null)
    }

    // --- Pelaksanaan build ---

    private suspend fun execute(request: BuildRequest) {
        val startedAt = mutableState.value.startedAtMillis
        val tracker = BuildRunTracker()
        try {
            val sync = workspace.sync(request.projectId, request.sharedTreeUri)
            if (sync is AppResult.Failure) {
                conclude(
                    request,
                    tracker,
                    startedAt,
                    BuildResolution(BuildStatus.FAILED, BuildFailure.Sync(sync.error)),
                    emptyList(),
                )
                return
            }

            val runToken = if (liveLog != null) newRunToken() else null
            val command = commands.build(
                sourcePath = WorkspaceLayout.sourcePath(request.sharedRootPath, request.projectId),
                projectId = request.projectId,
                type = request.type,
                outputRootPath = WorkspaceLayout.outputRootPath(request.sharedRootPath),
                runToken = runToken,
            )
            tracker.setPhase(BuildPhase.PREPARE)
            publish(tracker)

            val end = runCommand(command, tracker, request, runToken)
            tracker.finishOutput()

            var resolution = tracker.resolve(end, isCancelRequested())
            var artifacts: List<BuildArtifact> = tracker.artifacts.toList()
            if (resolution.status == BuildStatus.SUCCESS) {
                tracker.setPhase(BuildPhase.VERIFY)
                publish(tracker)
                when (val verified = workspace.verifyArtifacts(request.sharedTreeUri, request.projectId, artifacts)) {
                    is AppResult.Success -> {
                        artifacts = verified.value
                        val invalid = artifacts.filter { it.validation != ArtifactValidation.VALID }
                        if (invalid.isNotEmpty()) {
                            resolution = BuildResolution(
                                BuildStatus.FAILED,
                                BuildFailure.ArtifactInvalid(invalid.map { it.name }),
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        resolution = BuildResolution(BuildStatus.FAILED, BuildFailure.Sync(verified.error))
                    }
                }
            }
            conclude(request, tracker, startedAt, resolution, artifacts)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                tracker.finishOutput()
                conclude(
                    request,
                    tracker,
                    startedAt,
                    BuildResolution(BuildStatus.CANCELLED, null),
                    emptyList(),
                )
            }
            throw e
        } catch (e: Exception) {
            conclude(
                request,
                tracker,
                startedAt,
                BuildResolution(BuildStatus.FAILED, BuildFailure.Runner(TerminalFailure.START_FAILED, e.message)),
                emptyList(),
            )
        }
    }

    private fun isCancelRequested(): Boolean = synchronized(lock) { cancelRequested }

    /** Menjalankan perintah build dan mengumpulkan keluarannya. Berakhir saat perintah selesai atau dihentikan. */
    private suspend fun runCommand(
        command: String,
        tracker: BuildRunTracker,
        request: BuildRequest,
        runToken: String?,
    ): RunEnd {
        val outcome = RunOutcome()
        val newCollector = synchronized(lock) {
            if (cancelRequested) {
                null
            } else {
                val launched = scope.launch {
                    runner.run(CommandRequest(command, termuxHome)).collect { event ->
                        consumeBuildEvent(event, tracker, outcome)
                    }
                }
                collector = launched
                launched
            }
        } ?: return RunEnd.Stopped
        val poller = if (liveLog != null && runToken != null) scope.launch { pollLive(request, runToken) } else null
        try {
            newCollector.join()
        } finally {
            poller?.cancel()
        }
        return outcome.end
    }

    /**
     * Selama perintah berjalan, membaca log langsung yang ditulis helper ke folder bersama dan
     * menampilkannya. Hanya log dengan token run ini yang dipakai, jadi sisa build lama tidak tampil.
     * Kegagalan membaca diabaikan (tampilan langsung hanyalah bonus; hasil akhir tetap dari Termux).
     */
    private suspend fun pollLive(request: BuildRequest, token: String) {
        val source = liveLog ?: return
        while (true) {
            delay(liveIntervalMillis)
            val text = try {
                source.read(request.sharedTreeUri, request.projectId, DEFAULT_LIVE_LOG_MAX_BYTES)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val view = text?.let { LiveLog.parse(it, token) } ?: continue
            mutableState.update { current ->
                if (!current.isActive) {
                    current
                } else {
                    current.copy(
                        liveLines = view.lines,
                        liveTask = view.currentTask,
                        liveTasksSeen = view.tasksSeen,
                        liveDroppedLines = view.droppedLines,
                    )
                }
            }
        }
    }

    private fun consumeBuildEvent(event: CommandEvent, tracker: BuildRunTracker, outcome: RunOutcome) {
        when (event) {
            is CommandEvent.Output -> {
                tracker.onOutput(event.stream, event.text)
                publish(tracker)
            }

            is CommandEvent.Notice -> {
                tracker.onNotice(event.notice)
                publish(tracker)
            }

            is CommandEvent.Finished -> outcome.end = RunEnd.Finished(event.exitCode)
            is CommandEvent.Failed -> outcome.end = RunEnd.Failed(event.failure, event.detail)
        }
    }

    private fun publish(tracker: BuildRunTracker) {
        mutableState.update { current ->
            current.copy(
                phase = tracker.phase,
                currentTask = tracker.currentTask,
                tasksSeen = tracker.tasksSeen,
                issues = tracker.issues.toList(),
                issuesTruncated = tracker.issuesTruncated,
                artifacts = tracker.artifacts.toList(),
                logLines = tracker.logLines.toList(),
                droppedLogLines = tracker.droppedLogLines,
                gradleDuration = tracker.gradleDuration,
                exitCode = tracker.gradleExitCode,
                noResponseYet = tracker.noResponseYet,
                outputTruncated = tracker.outputTruncated,
            )
        }
    }

    /** Mencatat riwayat lebih dulu, baru mengumumkan hasil akhir, agar UI yang memuat ulang riwayat melihat catatan baru. */
    private suspend fun conclude(
        request: BuildRequest,
        tracker: BuildRunTracker,
        startedAt: Long,
        resolution: BuildResolution,
        artifacts: List<BuildArtifact>,
    ) {
        val finishedAt = clock()
        val failure = resolution.failure
        val record = BuildRecord(
            id = newId(),
            projectId = request.projectId,
            projectName = request.projectName,
            type = request.type,
            status = resolution.status,
            startedAtMillis = startedAt,
            durationMillis = (finishedAt - startedAt).coerceAtLeast(0L),
            tasksSeen = tracker.tasksSeen,
            errorCount = tracker.errorCount,
            warningCount = tracker.warningCount,
            artifactNames = artifacts.map { it.name },
            failureCode = failure?.code,
            failureDetail = failureDetail(failure),
        )
        withContext(NonCancellable) {
            try {
                history.append(record, tracker.logText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Riwayat yang gagal tersimpan tidak boleh mengubah hasil build.
            }
        }
        mutableState.update { current ->
            current.copy(
                status = resolution.status,
                phase = null,
                currentTask = tracker.currentTask,
                tasksSeen = tracker.tasksSeen,
                issues = tracker.issues.toList(),
                issuesTruncated = tracker.issuesTruncated,
                artifacts = artifacts,
                logLines = tracker.logLines.toList(),
                droppedLogLines = tracker.droppedLogLines,
                failure = failure,
                finishedAtMillis = finishedAt,
                gradleDuration = tracker.gradleDuration,
                exitCode = tracker.gradleExitCode,
                noResponseYet = false,
                outputTruncated = tracker.outputTruncated,
                mayStillRun = resolution.mayStillRun,
                liveLines = emptyList(),
                liveTask = null,
                liveTasksSeen = 0,
                liveDroppedLines = 0,
            )
        }
    }

    private fun failureDetail(failure: BuildFailure?): String? = when (failure) {
        null -> null
        is BuildFailure.Runner -> failure.detail
        is BuildFailure.Sync -> failure.error.message
        is BuildFailure.Helper -> null
        is BuildFailure.GradleExit -> failure.code.toString()
        BuildFailure.IncompleteOutput -> null
        is BuildFailure.ArtifactInvalid -> failure.names.joinToString(", ")
    }

    // --- Perintah pembantu (check, clean, cancel) ---

    private suspend fun sendCancelCommand() {
        try {
            runner.run(CommandRequest(commands.cancel(), termuxHome)).collect { }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Gagal mengirim pembatalan: build tetap ditandai dibatalkan setelah batas tunggu.
        }
    }

    private suspend fun runHelper(command: String): HelperRun {
        val outcome = RunOutcome()
        val messages = ArrayList<HelperMessage>()
        val assembler = LineAssembler()
        runner.run(CommandRequest(command, termuxHome)).collect { event ->
            consumeHelperEvent(event, assembler, messages, outcome)
        }
        assembler.flush()?.let { line -> HelperProtocol.parse(line)?.let { messages.add(it) } }
        return HelperRun(messages, outcome.end)
    }

    private fun consumeHelperEvent(
        event: CommandEvent,
        assembler: LineAssembler,
        messages: MutableList<HelperMessage>,
        outcome: RunOutcome,
    ) {
        when (event) {
            is CommandEvent.Output -> {
                for (line in assembler.append(event.text)) {
                    HelperProtocol.parse(line)?.let { messages.add(it) }
                }
            }

            is CommandEvent.Notice -> Unit
            is CommandEvent.Finished -> outcome.end = RunEnd.Finished(event.exitCode)
            is CommandEvent.Failed -> outcome.end = RunEnd.Failed(event.failure, event.detail)
        }
    }
}
