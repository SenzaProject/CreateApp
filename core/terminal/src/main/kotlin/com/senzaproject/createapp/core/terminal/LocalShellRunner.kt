package com.senzaproject.createapp.core.terminal

import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.CommandRequest
import com.senzaproject.createapp.domain.terminal.CommandRunner
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

private const val BUFFER_SIZE = 8192

/**
 * Menjalankan perintah lewat shell sistem (`/system/bin/sh -c <perintah>`) di proses Create App.
 *
 * - stdin ditutup segera (perintah interaktif yang menunggu masukan mendapat EOF, bukan menggantung).
 * - stdout dan stderr dibaca dengan polling non-blocking (`available()`), sehingga membatalkan koleksi
 *   selalu bisa menghentikan proses dan tidak pernah menggantung pada pembacaan pipa.
 * - Pembatalan: SIGTERM, tunggu [GRACE_MILLIS] ms, lalu SIGKILL. Bila [killDescendants] aktif
 *   (Android 13+), proses turunan ikut dimatikan; di Android 8–12 proses turunan dari perintah
 *   majemuk bisa tetap hidup sampai selesai sendiri.
 *
 * Batasan Android: program di folder data aplikasi tidak bisa dieksekusi langsung (Android 10+); gunakan
 * `sh skrip.sh` atau perintah sistem (toybox). Untuk alat pengembangan penuh gunakan backend Termux.
 *
 * @param shellPath lokasi shell; bisa diganti (mis. `/bin/sh`) saat pengujian di JVM.
 * @param onProcessStarted kait untuk pengujian.
 */
class LocalShellRunner(
    private val homeDirectory: String,
    private val killDescendants: Boolean,
    private val shellPath: String = DEFAULT_SHELL_PATH,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onProcessStarted: (Process) -> Unit = {},
) : CommandRunner {

    override val backend: TerminalBackend = TerminalBackend.LOCAL_SHELL

    override fun run(request: CommandRequest): Flow<CommandEvent> = channelFlow<CommandEvent> {
        val directory = File(request.workingDirectory)
        if (!directory.isDirectory) {
            send(CommandEvent.Failed(TerminalFailure.INVALID_DIRECTORY, request.workingDirectory))
            return@channelFlow
        }
        val process = try {
            start(request.command, directory)
        } catch (e: IOException) {
            send(CommandEvent.Failed(TerminalFailure.START_FAILED, e.message))
            return@channelFlow
        }
        onProcessStarted(process)

        val stdout = PipeReader(process.inputStream, StreamKind.STDOUT)
        val stderr = PipeReader(process.errorStream, StreamKind.STDERR)
        try {
            while (true) {
                val progressed = pump(stdout) or pump(stderr)
                if (progressed) continue
                if (!process.isAlive) {
                    // Proses sudah selesai: kuras sisa isi pipa sebelum berhenti agar tidak ada output hilang.
                    while (pump(stdout) or pump(stderr)) {
                        // terus sampai kosong
                    }
                    break
                }
                delay(POLL_INTERVAL_MILLIS)
            }
            sendRemainder(stdout)
            sendRemainder(stderr)
            send(CommandEvent.Finished(process.exitValue()))
        } finally {
            if (process.isAlive) terminate(process)
            stdout.close()
            stderr.close()
        }
    }.flowOn(ioDispatcher)

    private fun start(command: String, directory: File): Process {
        val builder = ProcessBuilder(shellPath, "-c", command)
        builder.directory(directory)
        val environment = builder.environment()
        environment["HOME"] = homeDirectory
        environment["PWD"] = directory.path
        environment["TERM"] = "dumb"
        val process = builder.start()
        try {
            process.outputStream.close()
        } catch (e: IOException) {
            // stdin sudah tertutup; tidak masalah.
        }
        return process
    }

    private suspend fun ProducerScope<CommandEvent>.pump(reader: PipeReader): Boolean {
        val read = reader.readAvailable() ?: return false
        if (read.isNotEmpty()) send(CommandEvent.Output(reader.kind, read))
        return true
    }

    private suspend fun ProducerScope<CommandEvent>.sendRemainder(reader: PipeReader) {
        val rest = reader.finish()
        if (rest.isNotEmpty()) send(CommandEvent.Output(reader.kind, rest))
    }

    private fun terminate(process: Process) {
        val descendants = if (killDescendants) {
            try {
                ProcessTree.capture(process)
            } catch (e: Throwable) {
                // Termasuk LinkageError bila ProcessHandle tidak ada di perangkat ini.
                null
            }
        } else {
            null
        }
        process.destroy()
        descendants?.terminate?.invoke()
        try {
            if (!process.waitFor(GRACE_MILLIS, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        }
        descendants?.kill?.invoke()
    }

    private class PipeReader(private val stream: InputStream, val kind: StreamKind) {
        private val buffer = ByteArray(BUFFER_SIZE)
        private val decoder = Utf8ChunkDecoder()

        /** Membaca byte yang tersedia saat ini tanpa memblokir; null bila tidak ada apa pun. */
        fun readAvailable(): String? {
            val available = try {
                stream.available()
            } catch (e: IOException) {
                return null
            }
            if (available <= 0) return null
            val count = try {
                stream.read(buffer, 0, minOf(available, buffer.size))
            } catch (e: IOException) {
                return null
            }
            if (count <= 0) return null
            return decoder.decode(buffer, count)
        }

        fun finish(): String = decoder.finish()

        fun close() {
            try {
                stream.close()
            } catch (e: IOException) {
                // diabaikan
            }
        }
    }

    companion object {
        const val DEFAULT_SHELL_PATH = "/system/bin/sh"
        const val POLL_INTERVAL_MILLIS = 10L
        const val GRACE_MILLIS = 500L
    }
}
