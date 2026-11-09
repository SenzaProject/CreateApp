package com.senzaproject.createapp.domain.terminal

/** Pesan dari terminal sendiri (bukan output perintah). UI memetakannya ke teks yang dapat dilokalisasi. */
sealed interface StatusMessage {
    data class ExitCode(val code: Int) : StatusMessage
    /** Dihentikan pengguna. Di Termux itu hanya berhenti menunggu hasil; prosesnya bisa tetap berjalan. */
    data class Cancelled(val backend: TerminalBackend) : StatusMessage
    data object OutputLimit : StatusMessage
    data class Failure(val failure: TerminalFailure, val detail: String?) : StatusMessage
    data class DirectoryNotFound(val path: String) : StatusMessage
    data object CdUsage : StatusMessage
    data class Notice(val notice: RunnerNotice) : StatusMessage
    data object Help : StatusMessage
}

sealed interface TerminalLine {
    /** Id unik dan naik terus; dipakai sebagai kunci daftar di UI. */
    val id: Long

    /** Perintah yang diketik pengguna beserta direktori kerjanya saat itu. */
    data class Command(
        override val id: Long,
        val workingDirectory: String,
        val text: String,
    ) : TerminalLine

    data class Output(
        override val id: Long,
        val text: String,
        val isError: Boolean,
    ) : TerminalLine

    data class Status(
        override val id: Long,
        val message: StatusMessage,
    ) : TerminalLine
}

data class TerminalState(
    val backend: TerminalBackend,
    val workingDirectory: String,
    val lines: List<TerminalLine>,
    val isRunning: Boolean,
)
