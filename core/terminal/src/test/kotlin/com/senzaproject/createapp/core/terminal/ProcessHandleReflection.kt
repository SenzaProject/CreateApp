package com.senzaproject.createapp.core.terminal

import java.util.stream.Stream

/** `ProcessHandle` tidak ada di android.jar, jadi dipanggil lewat refleksi (JVM host punya). */
internal fun descendantHandles(process: Process): List<Any> {
    val handleClass = Class.forName("java.lang.ProcessHandle")
    val self = Process::class.java.getMethod("toHandle").invoke(process)
    val stream = handleClass.getMethod("descendants").invoke(self) as Stream<*>
    return stream.iterator().asSequence().filterNotNull().toList()
}

internal fun handleAlive(handle: Any): Boolean =
    Class.forName("java.lang.ProcessHandle").getMethod("isAlive").invoke(handle) as Boolean
