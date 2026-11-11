package com.senzaproject.createapp.core.terminal

import java.util.stream.Stream

/** Sekelompok proses turunan yang dapat dimatikan (halus lalu paksa). */
internal class DescendantGroup(
    val terminate: () -> Unit,
    val kill: () -> Unit,
)

/**
 * Mengumpulkan proses turunan. `java.lang.ProcessHandle` tidak ada di android.jar (hanya ada
 * saat runtime di Android 13+), jadi dipanggil lewat refleksi dan gagal dengan aman bila tidak ada.
 */
internal object ProcessTree {

    private val EMPTY = DescendantGroup(terminate = {}, kill = {})

    fun capture(process: Process): DescendantGroup = try {
        val handleClass = Class.forName("java.lang.ProcessHandle")
        val toHandle = Process::class.java.getMethod("toHandle")
        val descendants = handleClass.getMethod("descendants")
        val destroy = handleClass.getMethod("destroy")
        val isAlive = handleClass.getMethod("isAlive")
        val destroyForcibly = handleClass.getMethod("destroyForcibly")

        val self = toHandle.invoke(process)
        val handles = ArrayList<Any>()
        val stream = descendants.invoke(self) as Stream<*>
        stream.iterator().forEach { if (it != null) handles.add(it) }

        DescendantGroup(
            terminate = { for (h in handles) runCatching { destroy.invoke(h) } },
            kill = {
                for (h in handles) runCatching {
                    if (isAlive.invoke(h) as Boolean) destroyForcibly.invoke(h)
                }
            },
        )
    } catch (_: Throwable) {
        EMPTY
    }
}
