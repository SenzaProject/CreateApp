package com.senzaproject.createapp.domain.terminal

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val DEFAULT_TERMINAL_MAX_LINES = 3000
const val DEFAULT_TERMINAL_MAX_RUN_OUTPUT_CHARS = 2_000_000

private class OutputLimitReached : RuntimeException()

/**
 * Sesi terminal: menyimpan scrollback, direktori kerja per backend, dan riwayat; menjalankan
 * perintah lewat [CommandRunner]; menangani perintah bawaan (`cd`, `clear`, `help`, `history`).
 *
 * Setiap perintah berjalan di proses shell baru, sehingga status shell (variabel, `cd`) tidak
 * bertahan antarperintah — kecuali direktori kerja yang dikelola sesi ini.
 *
 * TIDAK thread-safe: panggil [submit] dan fungsi lain dari satu thread/coroutine (thread utama di aplikasi).
 * [submit] baru kembali setelah perintah selesai, gagal, atau dibatalkan; membatalkan coroutine
 * pemanggilnya menghentikan perintah dan menambahkan baris "dihentikan".
 */
class TerminalSession(
    private val runners: Map<TerminalBackend, CommandRunner>,
    private val homeDirectories: Map<TerminalBackend, String>,
    private val directoryChecker: DirectoryChecker,
    initialBackend: TerminalBackend = TerminalBackend.LOCAL_SHELL,
    private val maxLines: Int = DEFAULT_TERMINAL_MAX_LINES,
    private val maxRunOutputChars: Int = DEFAULT_TERMINAL_MAX_RUN_OUTPUT_CHARS,
) {

    init {
        require(runners.containsKey(initialBackend)) { "Runner untuk $initialBackend tidak tersedia" }
        for (backend in runners.keys) {
            require(homeDirectories.containsKey(backend)) { "Direktori awal untuk $backend tidak ada" }
        }
        require(maxLines > 0) { "maxLines harus positif" }
    }

    private val directories = HashMap<TerminalBackend, String>(homeDirectories)
    private val history = CommandHistory()
    private var lastId = 0L

    private val _state = MutableStateFlow(
        TerminalState(
            backend = initialBackend,
            workingDirectory = homeDirectories.getValue(initialBackend),
            lines = emptyList(),
            isRunning = false,
        ),
    )
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    val availableBackends: List<TerminalBackend> get() = runners.keys.toList()

    val historyEntries: List<String> get() = history.entries

    fun historyPrevious(currentInput: String): String? = history.previous(currentInput)

    fun historyNext(): String? = history.next()

    /** Mengganti backend. Ditolak (false) saat ada perintah berjalan atau backend tidak tersedia. */
    fun selectBackend(backend: TerminalBackend): Boolean {
        val current = _state.value
        if (current.isRunning || !runners.containsKey(backend)) return false
        if (backend == current.backend) return true
        directories[current.backend] = current.workingDirectory
        val restored = directories.getValue(backend)
        _state.update { it.copy(backend = backend, workingDirectory = restored) }
        return true
    }

    /** Pindah direktori (mis. dari pemilih proyek). Mengembalikan false bila ditolak atau folder tidak ada. */
    fun changeDirectory(path: String): Boolean {
        val current = _state.value
        if (current.isRunning) return false
        val home = homeDirectories.getValue(current.backend)
        val target = PathResolver.resolve(current.workingDirectory, path, home)
        if (!directoryChecker.isDirectory(current.backend, target)) {
            append(listOf(status(StatusMessage.DirectoryNotFound(target))))
            return false
        }
        _state.update { it.copy(workingDirectory = target) }
        return true
    }

    /** Kembali ke direktori awal backend aktif. */
    fun goHome(): Boolean {
        val current = _state.value
        return changeDirectory(homeDirectories.getValue(current.backend))
    }

    fun clear() {
        _state.update { it.copy(lines = emptyList()) }
    }

    suspend fun submit(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty() || _state.value.isRunning) return
        history.add(input)
        val snapshot = _state.value
        append(listOf(TerminalLine.Command(nextId(), snapshot.workingDirectory, input)))
        when (val parsed = parseCommand(input)) {
            ParsedCommand.Clear -> clear()
            ParsedCommand.Help -> append(listOf(status(StatusMessage.Help)))
            ParsedCommand.History -> showHistory()
            is ParsedCommand.ChangeDirectory -> runChangeDirectory(parsed.target)
            ParsedCommand.InvalidCd -> append(listOf(status(StatusMessage.CdUsage)))
            is ParsedCommand.External -> runExternal(parsed.command)
        }
    }

    private fun showHistory() {
        // Perintah "history" sendiri sudah masuk riwayat sebelum fungsi ini dipanggil, jadi tidak pernah kosong.
        val entries = history.entries
        val width = entries.size.toString().length
        val lines = ArrayList<TerminalLine>()
        for ((index, command) in entries.withIndex()) {
            val number = (index + 1).toString().padStart(width)
            lines.add(TerminalLine.Output(nextId(), "$number  $command", isError = false))
        }
        append(lines)
    }

    private fun runChangeDirectory(target: String?) {
        val current = _state.value
        val home = homeDirectories.getValue(current.backend)
        val resolved = if (target == null) home else PathResolver.resolve(current.workingDirectory, target, home)
        if (directoryChecker.isDirectory(current.backend, resolved)) {
            _state.update { it.copy(workingDirectory = resolved) }
        } else {
            append(listOf(status(StatusMessage.DirectoryNotFound(resolved))))
        }
    }

    private suspend fun runExternal(command: String) {
        val snapshot = _state.value
        val runner = runners.getValue(snapshot.backend)
        val stdout = LineAssembler()
        val stderr = LineAssembler()
        var produced = 0
        var ended = false
        _state.update { it.copy(isRunning = true) }
        try {
            runner.run(CommandRequest(command, snapshot.workingDirectory)).collect { event ->
                when (event) {
                    is CommandEvent.Output -> {
                        produced += event.text.length
                        val isError = event.stream == StreamKind.STDERR
                        val assembler = if (isError) stderr else stdout
                        appendOutput(assembler.append(event.text), isError)
                        if (produced > maxRunOutputChars) throw OutputLimitReached()
                    }

                    is CommandEvent.Notice -> append(listOf(status(StatusMessage.Notice(event.notice))))

                    is CommandEvent.Finished -> {
                        flushAll(stdout, stderr)
                        append(listOf(status(StatusMessage.ExitCode(event.exitCode))))
                        ended = true
                    }

                    is CommandEvent.Failed -> {
                        flushAll(stdout, stderr)
                        append(listOf(status(StatusMessage.Failure(event.failure, event.detail))))
                        ended = true
                    }
                }
            }
            if (!ended) flushAll(stdout, stderr)
        } catch (e: OutputLimitReached) {
            flushAll(stdout, stderr)
            append(listOf(status(StatusMessage.OutputLimit)))
        } catch (e: CancellationException) {
            flushAll(stdout, stderr)
            append(listOf(status(StatusMessage.Cancelled(snapshot.backend))))
            throw e
        } catch (e: Exception) {
            flushAll(stdout, stderr)
            append(listOf(status(StatusMessage.Failure(TerminalFailure.IO_ERROR, e.message))))
        } finally {
            _state.update { it.copy(isRunning = false) }
        }
    }

    private fun appendOutput(lines: List<String>, isError: Boolean) {
        if (lines.isEmpty()) return
        val mapped = ArrayList<TerminalLine>(lines.size)
        for (text in lines) mapped.add(TerminalLine.Output(nextId(), text, isError))
        append(mapped)
    }

    private fun flushAll(stdout: LineAssembler, stderr: LineAssembler) {
        val rest = ArrayList<TerminalLine>()
        stdout.flush()?.let { rest.add(TerminalLine.Output(nextId(), it, isError = false)) }
        stderr.flush()?.let { rest.add(TerminalLine.Output(nextId(), it, isError = true)) }
        if (rest.isNotEmpty()) append(rest)
    }

    private fun append(lines: List<TerminalLine>) {
        _state.update { current ->
            val merged = current.lines + lines
            current.copy(lines = if (merged.size > maxLines) merged.takeLast(maxLines) else merged)
        }
    }

    private fun status(message: StatusMessage): TerminalLine = TerminalLine.Status(nextId(), message)

    private fun nextId(): Long {
        lastId += 1
        return lastId
    }
}
