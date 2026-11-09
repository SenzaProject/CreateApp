package com.senzaproject.createapp.domain.terminal

import kotlinx.coroutines.flow.Flow

/** Tempat perintah dijalankan. */
enum class TerminalBackend {
    /** Shell sistem Android (`/system/bin/sh`) di dalam proses Create App. */
    LOCAL_SHELL,

    /** Aplikasi Termux, melalui intent resmi `RUN_COMMAND`. */
    TERMUX,
}

/** Satu perintah shell yang akan dijalankan di [workingDirectory]. */
data class CommandRequest(
    val command: String,
    val workingDirectory: String,
)

enum class StreamKind { STDOUT, STDERR }

/** Alasan kegagalan menjalankan perintah (bukan kode keluar non-nol dari perintah itu sendiri). */
enum class TerminalFailure {
    INVALID_DIRECTORY,
    START_FAILED,
    IO_ERROR,
    TIMEOUT,
    TERMUX_NOT_INSTALLED,
    TERMUX_PERMISSION_DENIED,
    TERMUX_START_REJECTED,
    TERMUX_EXTERNAL_APPS_DISABLED,
    TERMUX_ERROR,
    TERMUX_NO_RESULT,
}

/** Informasi non-fatal dari backend selama perintah berjalan. */
enum class RunnerNotice {
    /** Termux belum membalas; kemungkinan `allow-external-apps` belum aktif atau perintah memang lama. */
    TERMUX_NO_RESPONSE_YET,

    /** Termux memotong output (batas ±100 KB per hasil). */
    TERMUX_OUTPUT_TRUNCATED,
}

/**
 * Peristiwa dari [CommandRunner]. Aliran selalu berakhir dengan tepat satu [Finished] atau [Failed],
 * kecuali bila dibatalkan oleh pemanggil (pembatalan coroutine) — itu tidak menghasilkan peristiwa.
 */
sealed interface CommandEvent {
    data class Output(val stream: StreamKind, val text: String) : CommandEvent
    data class Notice(val notice: RunnerNotice) : CommandEvent
    data class Finished(val exitCode: Int) : CommandEvent
    data class Failed(val failure: TerminalFailure, val detail: String? = null) : CommandEvent
}

/**
 * Menjalankan perintah. Membatalkan koleksi [Flow] (mis. membatalkan coroutine) harus menghentikan
 * perintah semampu backend; lihat dokumentasi tiap implementasi untuk batasannya.
 */
interface CommandRunner {
    val backend: TerminalBackend
    fun run(request: CommandRequest): Flow<CommandEvent>
}

/** Kondisi integrasi Termux di perangkat. */
sealed interface TermuxStatus {
    data object NotInstalled : TermuxStatus

    /** Termux terpasang tetapi izin `com.termux.permission.RUN_COMMAND` belum diberikan. */
    data object PermissionMissing : TermuxStatus

    /**
     * Termux terpasang dan izin diberikan. Opsi `allow-external-apps` di sisi Termux TIDAK bisa
     * dideteksi dari aplikasi lain; kegagalannya baru terlihat saat perintah dijalankan.
     */
    data class Ready(val versionName: String?) : TermuxStatus
}

interface TermuxEnvironment {
    fun status(): TermuxStatus
}

/** Memeriksa apakah [path] adalah folder yang valid untuk [backend]. */
fun interface DirectoryChecker {
    fun isDirectory(backend: TerminalBackend, path: String): Boolean
}
