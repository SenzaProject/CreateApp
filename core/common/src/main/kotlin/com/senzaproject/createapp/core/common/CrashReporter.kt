package com.senzaproject.createapp.core.common

import com.senzaproject.createapp.domain.logging.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyimpan laporan crash terakhir ke file agar dapat dibaca pada peluncuran berikutnya.
 * Setelah laporan ditulis, penanganan crash bawaan sistem tetap dijalankan.
 */
class CrashReporter(
    private val crashFile: File,
    private val logger: Logger,
) {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashFile.parentFile?.mkdirs()
                crashFile.writeText(LogRedactor.redact(buildReport(thread, throwable)))
            }
            logger.e(TAG, "Crash tidak tertangani di thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Mengembalikan isi laporan crash terakhir, atau null bila tidak ada. Panggil dari thread I/O. */
    fun readLastCrash(): String? =
        runCatching { if (crashFile.exists()) crashFile.readText() else null }.getOrNull()

    /** Menghapus laporan crash. Mengembalikan true bila file sudah tidak ada. */
    fun clear(): Boolean =
        runCatching { !crashFile.exists() || crashFile.delete() }.getOrDefault(false)

    private fun buildReport(thread: Thread, throwable: Throwable): String = buildString {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        append("Waktu: ").append(time).append('\n')
        append("Thread: ").append(thread.name).append('\n')
        append('\n')
        append(throwable.stackTraceToString())
    }

    private companion object {
        const val TAG = "CrashReporter"
    }
}
