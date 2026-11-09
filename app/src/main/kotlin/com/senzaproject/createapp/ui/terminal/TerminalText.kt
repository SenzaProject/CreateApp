package com.senzaproject.createapp.ui.terminal

import android.content.Context
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.terminal.RunnerNotice
import com.senzaproject.createapp.domain.terminal.StatusMessage
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import com.senzaproject.createapp.domain.terminal.TerminalLine

private const val MAX_DETAIL_CHARS = 500

/** Teks yang dapat dilokalisasi untuk pesan dari terminal sendiri. */
fun StatusMessage.toText(context: Context): String = when (this) {
    is StatusMessage.ExitCode ->
        if (code == 0) context.getString(R.string.terminal_exit_ok) else context.getString(R.string.terminal_exit_fail, code)

    is StatusMessage.Cancelled ->
        if (backend == TerminalBackend.TERMUX) {
            context.getString(R.string.terminal_cancelled_termux)
        } else {
            context.getString(R.string.terminal_cancelled_local)
        }

    StatusMessage.OutputLimit -> context.getString(R.string.terminal_output_limit)
    is StatusMessage.Failure -> failure.toText(context, detail)
    is StatusMessage.DirectoryNotFound -> context.getString(R.string.terminal_dir_not_found, path)
    StatusMessage.CdUsage -> context.getString(R.string.terminal_cd_usage)
    is StatusMessage.Notice -> notice.toText(context)
    StatusMessage.Help -> context.getString(R.string.terminal_help)
}

/** Pesan yang ditampilkan sebagai kesalahan (merah). */
fun StatusMessage.isError(): Boolean = when (this) {
    is StatusMessage.ExitCode -> code != 0
    is StatusMessage.Failure -> true
    is StatusMessage.DirectoryNotFound -> true
    StatusMessage.CdUsage -> true
    StatusMessage.OutputLimit -> true
    is StatusMessage.Cancelled -> false
    is StatusMessage.Notice -> false
    StatusMessage.Help -> false
}

private fun TerminalFailure.toText(context: Context, detail: String?): String {
    val base = context.getString(
        when (this) {
            TerminalFailure.INVALID_DIRECTORY -> R.string.terminal_failure_invalid_directory
            TerminalFailure.START_FAILED -> R.string.terminal_failure_start
            TerminalFailure.IO_ERROR -> R.string.terminal_failure_io
            TerminalFailure.TIMEOUT -> R.string.terminal_failure_timeout
            TerminalFailure.TERMUX_NOT_INSTALLED -> R.string.terminal_failure_termux_not_installed
            TerminalFailure.TERMUX_PERMISSION_DENIED -> R.string.terminal_failure_termux_permission
            TerminalFailure.TERMUX_START_REJECTED -> R.string.terminal_failure_termux_rejected
            TerminalFailure.TERMUX_EXTERNAL_APPS_DISABLED -> R.string.terminal_failure_termux_external
            TerminalFailure.TERMUX_ERROR -> R.string.terminal_failure_termux_error
            TerminalFailure.TERMUX_NO_RESULT -> R.string.terminal_failure_termux_no_result
        },
    )
    val extra = detail?.trim().orEmpty()
    if (extra.isEmpty()) return base
    val shown = if (extra.length > MAX_DETAIL_CHARS) extra.substring(0, MAX_DETAIL_CHARS) + "…" else extra
    return base + "\n" + shown
}

private fun RunnerNotice.toText(context: Context): String = context.getString(
    when (this) {
        RunnerNotice.TERMUX_NO_RESPONSE_YET -> R.string.terminal_notice_no_response
        RunnerNotice.TERMUX_OUTPUT_TRUNCATED -> R.string.terminal_notice_truncated
    },
)

/** Seluruh scrollback sebagai teks biasa (untuk disalin dan dikirim sebagai laporan). */
fun terminalTranscript(lines: List<TerminalLine>, context: Context): String {
    val out = StringBuilder()
    for (line in lines) {
        when (line) {
            is TerminalLine.Command -> out.append(line.workingDirectory).append(" $ ").append(line.text)
            is TerminalLine.Output -> out.append(line.text)
            is TerminalLine.Status -> out.append(line.message.toText(context))
        }
        out.append('\n')
    }
    return out.toString()
}
