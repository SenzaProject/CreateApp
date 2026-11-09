package com.senzaproject.createapp.core.terminal

import java.io.File

/** Sekelompok proses turunan yang dapat dimatikan (halus lalu paksa). */
internal class DescendantGroup(
    val terminate: () -> Unit,
    val kill: () -> Unit,
)

/**
 * Mengumpulkan proses turunan tanpa `java.lang.ProcessHandle` (kelas itu tidak ada di android.jar,
 * sehingga tidak bisa dikompilasi). Sebagai gantinya:
 * - PID proses induk dibaca lewat refleksi: method `pid()` bila ada, jika tidak field `pid`.
 * - Turunan dicari dengan memindai `/proc/<pid>/stat` (kolom PPID).
 * - Sinyal dikirim lewat perintah `kill`, yang tersedia di Android (toybox) maupun JVM Linux.
 *
 * Semua kegagalan ditelan: bila PID tidak terbaca, grup kosong dikembalikan.
 */
internal object ProcessTree {

    fun capture(process: Process): DescendantGroup {
        val rootPid = pidOf(process)
        val pids = if (rootPid > 0) descendantsOf(rootPid) else emptyList()
        return DescendantGroup(
            terminate = { for (pid in pids) signal(pid, "-TERM") },
            kill = { for (pid in pids) if (isAlive(pid)) signal(pid, "-KILL") },
        )
    }

    fun pidOf(process: Process): Int {
        // Java 9+ (JVM uji, Android 13+): Process.pid() lewat refleksi karena tidak ada di android.jar.
        try {
            val method = Process::class.java.getMethod("pid")
            return (method.invoke(process) as Number).toInt()
        } catch (e: Throwable) {
            // lanjut ke field privat
        }
        // Android lama: field `pid` pada ProcessImpl/UNIXProcess.
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (e: Throwable) {
            -1
        }
    }

    fun descendantsOf(rootPid: Int): List<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        val entries = File("/proc").listFiles() ?: return emptyList()
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            val ppid = parentOf(pid) ?: continue
            children.getOrPut(ppid) { ArrayList() }.add(pid)
        }
        val result = ArrayList<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(rootPid)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (child in children[current].orEmpty()) {
                result.add(child)
                queue.add(child)
            }
        }
        return result
    }

    /** Format stat: `pid (nama) state ppid ...`; nama bisa memuat spasi/kurung, jadi cari ')' terakhir. */
    private fun parentOf(pid: Int): Int? = try {
        val stat = File("/proc/$pid/stat").readText()
        val afterName = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        afterName[1].toIntOrNull()
    } catch (e: Throwable) {
        null
    }

    /** Hidup bila masih ada di /proc dan bukan zombie (state `Z`, belum dituai induknya). */
    fun isAlive(pid: Int): Boolean = try {
        val stat = File("/proc/$pid/stat").readText()
        stat.getOrNull(stat.lastIndexOf(')') + 2) != 'Z'
    } catch (e: Throwable) {
        false
    }

    private fun signal(pid: Int, option: String) {
        try {
            ProcessBuilder("kill", option, pid.toString())
                .redirectErrorStream(true)
                .start()
                .also { it.outputStream.close() }
                .waitFor()
        } catch (e: Throwable) {
            // proses sudah hilang atau kill tidak tersedia; abaikan.
        }
    }
}
