package com.senzaproject.createapp.core.terminal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.CommandRequest
import com.senzaproject.createapp.domain.terminal.CommandRunner
import com.senzaproject.createapp.domain.terminal.RunnerNotice
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import com.senzaproject.createapp.domain.terminal.TermuxEnvironment
import com.senzaproject.createapp.domain.terminal.TermuxStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Menjalankan perintah di Termux lewat intent resmi `RUN_COMMAND` (perintah latar belakang, hasil
 * dikembalikan lewat PendingIntent).
 *
 * Prasyarat (semuanya di luar kendali aplikasi ini; lihat `docs/TAHAP-5.md`):
 * 1. Termux terpasang (build F-Droid/GitHub) dan `<queries>` ada di manifest.
 * 2. Izin `com.termux.permission.RUN_COMMAND` diberikan kepada Create App.
 * 3. `allow-external-apps=true` di `~/.termux/termux.properties` pada Termux.
 *
 * Batasan yang sengaja dinyatakan terbuka:
 * - Output TIDAK mengalir: stdout/stderr baru tampil setelah perintah selesai, dan Termux memotong
 *   hasil ±100 KB.
 * - Membatalkan hanya BERHENTI MENUNGGU hasil. Termux tidak menyediakan cara membunuh perintah latar
 *   belakang dari aplikasi lain, jadi proses di Termux bisa tetap berjalan sampai selesai.
 * - Direktori kerja harus berada di sistem file Termux atau penyimpanan bersama; folder privat
 *   proyek Create App tidak bisa diakses Termux (UID berbeda).
 */
class TermuxCommandRunner(
    private val context: Context,
    private val environment: TermuxEnvironment,
    private val noResponseHintMillis: Long = DEFAULT_NO_RESPONSE_HINT_MILLIS,
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
) : CommandRunner {

    override val backend: TerminalBackend = TerminalBackend.TERMUX

    /** Pembungkus agar "tidak ada bundle" (data null) dapat dibedakan dari "waktu habis" (null dari timeout). */
    private class Received(val data: TermuxResultData?)

    override fun run(request: CommandRequest): Flow<CommandEvent> = channelFlow<CommandEvent> {
        when (environment.status()) {
            TermuxStatus.NotInstalled -> {
                send(CommandEvent.Failed(TerminalFailure.TERMUX_NOT_INSTALLED))
                return@channelFlow
            }

            TermuxStatus.PermissionMissing -> {
                send(CommandEvent.Failed(TerminalFailure.TERMUX_PERMISSION_DENIED))
                return@channelFlow
            }

            is TermuxStatus.Ready -> Unit
        }
        if (request.command.length > MAX_COMMAND_CHARS) {
            send(CommandEvent.Failed(TerminalFailure.START_FAILED, "perintah terlalu panjang"))
            return@channelFlow
        }

        val executionId = nextExecutionId()
        val action = RESULT_ACTION_PREFIX + executionId
        val results = Channel<Received>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                results.trySend(Received(TermuxResultReader.fromIntent(intent)))
            }
        }
        val pendingIntent = createPendingIntent(executionId, action)

        registerReceiver(receiver, action)
        try {
            val failure = startTermuxService(request, pendingIntent)
            if (failure != null) {
                send(failure)
                return@channelFlow
            }

            val hint = launch {
                delay(noResponseHintMillis)
                send(CommandEvent.Notice(RunnerNotice.TERMUX_NO_RESPONSE_YET))
            }
            val received = withTimeoutOrNull(maxWaitMillis) { results.receive() }
            hint.cancel()

            if (received == null) {
                send(CommandEvent.Failed(TerminalFailure.TIMEOUT))
            } else {
                val data = received.data
                if (data == null) {
                    send(CommandEvent.Failed(TerminalFailure.TERMUX_NO_RESULT))
                } else {
                    for (event in TermuxResultMapper.toEvents(data)) send(event)
                }
            }
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // sudah dilepas
            }
            pendingIntent.cancel()
        }
    }

    private fun createPendingIntent(executionId: Int, action: String): PendingIntent {
        // Intent eksplisit ke paket sendiri. FLAG_MUTABLE wajib: Termux menyisipkan bundle hasil ke
        // intent ini; dengan FLAG_IMMUTABLE hasilnya tidak pernah sampai.
        val resultIntent = Intent(action).setPackage(context.packageName)
        var flags = PendingIntent.FLAG_ONE_SHOT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, executionId, resultIntent, flags)
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiver(receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /** Mengirim intent ke Termux; mengembalikan peristiwa kegagalan, atau null bila terkirim. */
    private fun startTermuxService(request: CommandRequest, pendingIntent: PendingIntent): CommandEvent.Failed? {
        val intent = Intent()
            .setClassName(TermuxPaths.PACKAGE, TermuxPaths.RUN_COMMAND_SERVICE)
            .setAction(TermuxPaths.ACTION_RUN_COMMAND)
            .putExtra(TermuxPaths.EXTRA_COMMAND_PATH, TermuxPaths.PREFIX + "/bin/bash")
            .putExtra(TermuxPaths.EXTRA_ARGUMENTS, arrayOf("-c", request.command))
            .putExtra(TermuxPaths.EXTRA_WORKDIR, request.workingDirectory)
            .putExtra(TermuxPaths.EXTRA_BACKGROUND, true)
            .putExtra(TermuxPaths.EXTRA_COMMAND_LABEL, "Create App")
            .putExtra(TermuxPaths.EXTRA_PENDING_INTENT, pendingIntent)
        return try {
            if (context.startService(intent) == null) {
                CommandEvent.Failed(TerminalFailure.TERMUX_START_REJECTED, "RunCommandService tidak ditemukan")
            } else {
                null
            }
        } catch (e: SecurityException) {
            CommandEvent.Failed(TerminalFailure.TERMUX_PERMISSION_DENIED, e.message)
        } catch (e: Exception) {
            CommandEvent.Failed(TerminalFailure.TERMUX_START_REJECTED, e.message)
        }
    }

    companion object {
        const val DEFAULT_NO_RESPONSE_HINT_MILLIS = 15_000L
        const val DEFAULT_MAX_WAIT_MILLIS = 30 * 60_000L
        private const val MAX_COMMAND_CHARS = 100_000
        private const val RESULT_ACTION_PREFIX = "com.senzaproject.createapp.TERMUX_RESULT."
        private val executionCounter = AtomicInteger(1000)

        private fun nextExecutionId(): Int = executionCounter.incrementAndGet()
    }
}
