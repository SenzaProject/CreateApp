package com.senzaproject.createapp.core.common

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Menulis log ke file pada thread latar belakang tunggal (tidak memblokir UI).
 * File diputar menjadi "<nama>.1" saat melewati [maxBytes]. Kegagalan menulis log
 * tidak pernah melempar pengecualian ke pemanggil.
 */
class LogFileSink(
    private val file: File,
    private val maxBytes: Long = 512 * 1024L,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "createapp-log").apply { isDaemon = true }
    }

    // Hanya dipakai di thread executor, sehingga aman walau SimpleDateFormat tidak thread-safe.
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestamp = System.currentTimeMillis()
        executor.execute {
            runCatching { write(timestamp, level, tag, message, throwable) }
        }
    }

    private fun write(timestamp: Long, level: String, tag: String, message: String, throwable: Throwable?) {
        file.parentFile?.mkdirs()
        if (file.exists() && file.length() > maxBytes) {
            val rotated = File(file.parentFile, file.name + ".1")
            if (rotated.exists()) rotated.delete()
            file.renameTo(rotated)
        }
        val line = buildString {
            append(timeFormat.format(Date(timestamp)))
            append(' ').append(level).append('/').append(tag).append(": ").append(message).append('\n')
            if (throwable != null) append(throwable.stackTraceToString()).append('\n')
        }
        file.appendText(LogRedactor.redact(line))
    }
}
