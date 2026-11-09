package com.senzaproject.createapp.core.terminal

import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.RunnerNotice
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalFailure

/** Isi bundle hasil dari Termux (lihat wiki RUN_COMMAND Intent, bagian "Result Extras"). */
internal data class TermuxResultData(
    val stdout: String?,
    val stderr: String?,
    val exitCode: Int?,
    val errCode: Int?,
    val errMessage: String?,
    val stdoutOriginalLength: Int?,
    val stderrOriginalLength: Int?,
)

/** Mengubah hasil Termux menjadi peristiwa terminal. Murni (tanpa Android) sehingga bisa diuji di JVM. */
internal object TermuxResultMapper {

    /** Termux mengisi `err` dengan `Activity.RESULT_OK` (-1) bila tidak ada error internal; 0 dan null juga dianggap OK. */
    private fun isOkCode(code: Int?): Boolean = code == null || code == -1 || code == 0

    fun toEvents(data: TermuxResultData): List<CommandEvent> {
        val events = ArrayList<CommandEvent>()
        val stdout = data.stdout.orEmpty()
        val stderr = data.stderr.orEmpty()

        val truncated = (data.stdoutOriginalLength ?: 0) > stdout.length ||
            (data.stderrOriginalLength ?: 0) > stderr.length
        if (truncated) events.add(CommandEvent.Notice(RunnerNotice.TERMUX_OUTPUT_TRUNCATED))

        if (stdout.isNotEmpty()) events.add(CommandEvent.Output(StreamKind.STDOUT, stdout))
        if (stderr.isNotEmpty()) events.add(CommandEvent.Output(StreamKind.STDERR, stderr))

        val message = data.errMessage?.trim().orEmpty()
        val hasInternalError = !isOkCode(data.errCode) || message.isNotEmpty()
        val exitCode = data.exitCode
        when {
            hasInternalError -> {
                val failure = if (message.contains("allow-external-apps", ignoreCase = true)) {
                    TerminalFailure.TERMUX_EXTERNAL_APPS_DISABLED
                } else {
                    TerminalFailure.TERMUX_ERROR
                }
                val detail = if (message.isNotEmpty()) message else "err=${data.errCode}"
                events.add(CommandEvent.Failed(failure, detail))
            }

            exitCode == null -> events.add(CommandEvent.Failed(TerminalFailure.TERMUX_NO_RESULT, null))

            else -> events.add(CommandEvent.Finished(exitCode))
        }
        return events
    }
}
