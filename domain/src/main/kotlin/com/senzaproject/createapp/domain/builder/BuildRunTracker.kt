package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.LineAssembler
import com.senzaproject.createapp.domain.terminal.RunnerNotice
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalFailure

const val DEFAULT_BUILD_LOG_LINES = 3000

/** Cara perintah ke Termux berakhir, dilihat dari sisi aplikasi. */
sealed interface RunEnd {
    data class Finished(val exitCode: Int) : RunEnd
    data class Failed(val failure: TerminalFailure, val detail: String?) : RunEnd

    /** Aplikasi berhenti menunggu (pembatalan) tanpa menerima hasil dari Termux. */
    data object Stopped : RunEnd
}

/** Keputusan akhir sebuah build sebelum pemeriksaan artefak oleh aplikasi. */
data class BuildResolution(val status: BuildStatus, val failure: BuildFailure?, val mayStillRun: Boolean = false)

/**
 * Menampung keluaran satu build: merakit baris, memisahkan penanda helper dari log Gradle, mengurai
 * log, lalu menentukan hasil akhir. Murni (tanpa Android dan tanpa coroutine) sehingga bisa diuji di JVM.
 *
 * TIDAK thread-safe; dipakai dari satu coroutine.
 */
class BuildRunTracker(
    private val maxLogLines: Int = DEFAULT_BUILD_LOG_LINES,
) {
    private val parser = GradleLogParser()
    private val stdoutLines = LineAssembler()
    private val stderrLines = LineAssembler()
    private val log = ArrayList<String>()

    var droppedLogLines: Int = 0
        private set

    var phase: BuildPhase? = null
        private set

    var gradleExitCode: Int? = null
        private set

    var helperError: HelperError? = null
        private set

    var helperDone: Boolean = false
        private set

    var cancelledMarker: Boolean = false
        private set

    var noResponseYet: Boolean = false
        private set

    var outputTruncated: Boolean = false
        private set

    val artifacts: List<BuildArtifact> get() = artifactList

    private val artifactList = ArrayList<BuildArtifact>()

    val logLines: List<String> get() = log

    val currentTask: String? get() = parser.currentTask

    val tasksSeen: Int get() = parser.taskCount

    val issues: List<BuildIssue> get() = parser.issues

    val issuesTruncated: Boolean get() = parser.issuesTruncated

    val errorCount: Int get() = parser.errorCount

    val warningCount: Int get() = parser.warningCount

    val gradleDuration: String? get() = parser.duration

    fun setPhase(value: BuildPhase) {
        phase = value
    }

    fun onOutput(stream: StreamKind, text: String) {
        val assembler = if (stream == StreamKind.STDERR) stderrLines else stdoutLines
        for (line in assembler.append(text)) handleLine(line)
    }

    fun onNotice(notice: RunnerNotice) {
        when (notice) {
            RunnerNotice.TERMUX_NO_RESPONSE_YET -> noResponseYet = true
            RunnerNotice.TERMUX_OUTPUT_TRUNCATED -> outputTruncated = true
        }
    }

    /** Memproses sisa teks tanpa baris baru. Panggil sekali setelah perintah berakhir. */
    fun finishOutput() {
        stdoutLines.flush()?.let { handleLine(it) }
        stderrLines.flush()?.let { handleLine(it) }
    }

    /** Teks log (tanpa penanda helper) untuk disimpan atau disalin. */
    fun logText(): String {
        val out = StringBuilder()
        if (droppedLogLines > 0) out.append("… ").append(droppedLogLines).append(" baris awal dibuang …\n")
        for (line in log) out.append(line).append('\n')
        return out.toString()
    }

    private fun handleLine(line: String) {
        val message = HelperProtocol.parse(line)
        if (message == null) {
            appendLog(line)
            parser.accept(line)
            return
        }
        when (message) {
            is HelperMessage.Phase -> phase = message.phase
            is HelperMessage.Exit -> gradleExitCode = message.code
            is HelperMessage.Artifact -> artifactList.add(BuildArtifact(message.kind, message.name, message.sizeBytes))
            is HelperMessage.Error -> if (helperError == null) helperError = message.error
            HelperMessage.Cancelled -> cancelledMarker = true
            HelperMessage.Done -> helperDone = true
            is HelperMessage.Version,
            is HelperMessage.LogFile,
            is HelperMessage.Check,
            is HelperMessage.CancelResult,
            HelperMessage.CleanOk,
            -> Unit
        }
    }

    private fun appendLog(line: String) {
        log.add(line)
        if (log.size > maxLogLines) {
            val excess = log.size - maxLogLines
            log.subList(0, excess).clear()
            droppedLogLines += excess
        }
    }

    /**
     * Menentukan hasil akhir. SUCCESS di sini berarti "perintah dan penanda helper konsisten"; aplikasi
     * masih harus memeriksa file artefak sebelum menyatakan build berhasil.
     */
    fun resolve(end: RunEnd, cancelRequested: Boolean): BuildResolution {
        if (cancelRequested || cancelledMarker) {
            return BuildResolution(
                status = BuildStatus.CANCELLED,
                failure = null,
                mayStillRun = end is RunEnd.Stopped,
            )
        }
        when (end) {
            is RunEnd.Failed -> return BuildResolution(BuildStatus.FAILED, BuildFailure.Runner(end.failure, end.detail))
            RunEnd.Stopped -> return BuildResolution(
                BuildStatus.FAILED,
                BuildFailure.Runner(TerminalFailure.TIMEOUT, null),
            )
            is RunEnd.Finished -> Unit
        }
        val exit = (end as RunEnd.Finished).exitCode

        val error = helperError
        if (error != null) return BuildResolution(BuildStatus.FAILED, BuildFailure.Helper(error))

        val gradleExit = gradleExitCode
        if (gradleExit != null && gradleExit != 0) {
            return BuildResolution(BuildStatus.FAILED, BuildFailure.GradleExit(gradleExit))
        }
        if (exit != 0) return BuildResolution(BuildStatus.FAILED, BuildFailure.GradleExit(exit))

        if (!helperDone || gradleExit != 0) {
            return BuildResolution(BuildStatus.FAILED, BuildFailure.IncompleteOutput)
        }
        if (artifactList.isEmpty()) {
            return BuildResolution(BuildStatus.FAILED, BuildFailure.Helper(HelperError.NO_ARTIFACT))
        }
        return BuildResolution(BuildStatus.SUCCESS, null)
    }
}
