package com.senzaproject.createapp.core.terminal

/** Sekelompok proses turunan yang dapat dimatikan (halus lalu paksa). */
internal class DescendantGroup(
    val terminate: () -> Unit,
    val kill: () -> Unit,
)

/**
 * Mengumpulkan proses turunan. `ProcessHandle` baru ada di Android 13 (API 33), jadi semua
 * rujukan ke kelas itu sengaja dikurung di berkas ini dan hanya dipanggil bila API tersedia
 * (lihat parameter `killDescendants` pada [LocalShellRunner]).
 */
internal object ProcessTree {

    @Suppress("NewApi")
    fun capture(process: Process): DescendantGroup {
        val handles = ArrayList<ProcessHandle>()
        process.toHandle().descendants().forEach { handles.add(it) }
        return DescendantGroup(
            terminate = { for (handle in handles) handle.destroy() },
            kill = { for (handle in handles) if (handle.isAlive) handle.destroyForcibly() },
        )
    }
}
