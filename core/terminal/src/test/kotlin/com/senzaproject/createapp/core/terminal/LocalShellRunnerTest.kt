package com.senzaproject.createapp.core.terminal

import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.CommandRequest
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Menjalankan proses sungguhan memakai /bin/sh di JVM (CI Linux). Ini menguji logika polling, pemisahan
 * stdout/stderr, kode keluar, dan pembatalan — bukan perilaku shell Android (itu butuh perangkat).
 */
class LocalShellRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun requireUnixShell() {
        assumeTrue("butuh /bin/sh", File("/bin/sh").canExecute())
    }

    private fun runner(
        shell: String = "/bin/sh",
        onStarted: (Process) -> Unit = {},
    ) = LocalShellRunner(
        homeDirectory = tmp.root.path,
        killDescendants = true,
        shellPath = shell,
        onProcessStarted = onStarted,
    )

    private fun runCommand(command: String, directory: File = tmp.root): List<CommandEvent> = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) { runner().run(CommandRequest(command, directory.path)).toList() }
    }

    private fun List<CommandEvent>.text(stream: StreamKind): String =
        filterIsInstance<CommandEvent.Output>().filter { it.stream == stream }.joinToString("") { it.text }

    private fun List<CommandEvent>.exitCode(): Int = (last() as CommandEvent.Finished).exitCode

    @Test
    fun `stdout ditangkap dan kode keluar nol`() {
        val events = runCommand("echo hello")
        assertEquals("hello\n", events.text(StreamKind.STDOUT))
        assertEquals(0, events.exitCode())
    }

    @Test
    fun `stderr dipisahkan dari stdout`() {
        val events = runCommand("echo out; echo err 1>&2")
        assertEquals("out\n", events.text(StreamKind.STDOUT))
        assertEquals("err\n", events.text(StreamKind.STDERR))
    }

    @Test
    fun `kode keluar non-nol dilaporkan`() {
        assertEquals(3, runCommand("exit 3").exitCode())
    }

    @Test
    fun `perintah yang tidak ada memberi 127 dan pesan di stderr`() {
        val events = runCommand("perintah_tidak_ada_xyz")
        assertEquals(127, events.exitCode())
        assertTrue(events.text(StreamKind.STDERR).isNotBlank())
    }

    @Test
    fun `kesalahan sintaks memberi kode non-nol`() {
        val events = runCommand("if")
        assertTrue(events.exitCode() != 0)
        assertTrue(events.text(StreamKind.STDERR).isNotBlank())
    }

    @Test
    fun `direktori kerja dipakai`() {
        val dir = tmp.newFolder("proyek")
        val events = runCommand("pwd", dir)
        assertEquals(dir.canonicalPath, File(events.text(StreamKind.STDOUT).trim()).canonicalPath)
    }

    @Test
    fun `HOME diatur ke direktori awal`() {
        assertEquals(tmp.root.path + "\n", runCommand("echo \$HOME").text(StreamKind.STDOUT))
    }

    @Test
    fun `stdin ditutup sehingga cat tanpa argumen tidak menggantung`() {
        assertEquals(0, runCommand("cat").exitCode())
    }

    @Test
    fun `output tanpa baris baru tetap diterima`() {
        assertEquals("abc", runCommand("printf abc").text(StreamKind.STDOUT))
    }

    @Test
    fun `UTF-8 multibyte utuh`() {
        assertEquals("h\u00e9llo\n", runCommand("printf 'h\\303\\251llo\\n'").text(StreamKind.STDOUT))
    }

    @Test
    fun `output besar diterima lengkap`() {
        val events = runCommand("seq 1 50000")
        val lines = events.text(StreamKind.STDOUT).lines().filter { it.isNotEmpty() }
        assertEquals(50000, lines.size)
        assertEquals("1", lines.first())
        assertEquals("50000", lines.last())
        assertEquals(0, events.exitCode())
    }

    @Test
    fun `hanya satu peristiwa akhir dan berada di urutan terakhir`() {
        val events = runCommand("echo a; echo b 1>&2; exit 4")
        assertEquals(1, events.count { it is CommandEvent.Finished || it is CommandEvent.Failed })
        assertTrue(events.last() is CommandEvent.Finished)
    }

    @Test
    fun `direktori yang tidak ada ditolak tanpa menjalankan proses`() {
        val events = runCommand("echo x", File(tmp.root, "tidak-ada"))
        val failed = events.single() as CommandEvent.Failed
        assertEquals(TerminalFailure.INVALID_DIRECTORY, failed.failure)
    }

    @Test
    fun `shell yang tidak ada menghasilkan kegagalan start`() = runBlocking {
        val events = withTimeout(TEST_TIMEOUT_MILLIS) {
            runner(shell = "/tidak/ada/sh").run(CommandRequest("echo x", tmp.root.path)).toList()
        }
        assertEquals(TerminalFailure.START_FAILED, (events.single() as CommandEvent.Failed).failure)
    }

    @Test
    fun `membatalkan koleksi mematikan proses dan turunannya`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            var started: Process? = null
            val seenStarted = CompletableDeferred<Unit>()
            val job = launch {
                runner(onStarted = { started = it })
                    .run(CommandRequest("echo started; sleep 60; echo terlambat", tmp.root.path))
                    .collect { event ->
                        if (event is CommandEvent.Output && event.text.contains("started")) seenStarted.complete(Unit)
                    }
            }
            seenStarted.await()
            val process = checkNotNull(started)

            // Tunggu sampai `sleep` benar-benar menjadi proses turunan sebelum membatalkan.
            val descendants = ArrayList<Any>()
            waitUntil(5_000) {
                descendants.clear()
                descendantHandles(process).forEach { descendants.add(it) }
                descendants.isNotEmpty()
            }
            assertTrue("sleep harus menjadi proses turunan", descendants.isNotEmpty())

            job.cancelAndJoin()

            assertTrue("proses shell harus mati", waitUntil(5_000) { !process.isAlive })
            assertTrue("proses turunan harus mati", waitUntil(5_000) { descendants.none { handleAlive(it) } })
        }
    }

    private suspend fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            delay(25)
        }
        return condition()
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 60_000L
    }
}
