package com.senzaproject.createapp.domain.terminal

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRunner(
    override val backend: TerminalBackend = TerminalBackend.LOCAL_SHELL,
    private val script: (CommandRequest) -> Flow<CommandEvent>,
) : CommandRunner {
    val requests = ArrayList<CommandRequest>()

    override fun run(request: CommandRequest): Flow<CommandEvent> {
        requests.add(request)
        return script(request)
    }
}

private val EXISTING = setOf("/home", "/home/src", "/tmp", "/termux/home", "/termux/home/sub")

private fun newSession(
    runner: FakeRunner,
    termuxRunner: FakeRunner? = null,
    maxLines: Int = DEFAULT_TERMINAL_MAX_LINES,
    maxRunOutputChars: Int = DEFAULT_TERMINAL_MAX_RUN_OUTPUT_CHARS,
): TerminalSession {
    val runners = HashMap<TerminalBackend, CommandRunner>()
    runners[TerminalBackend.LOCAL_SHELL] = runner
    val homes = HashMap<TerminalBackend, String>()
    homes[TerminalBackend.LOCAL_SHELL] = "/home"
    if (termuxRunner != null) {
        runners[TerminalBackend.TERMUX] = termuxRunner
        homes[TerminalBackend.TERMUX] = "/termux/home"
    }
    return TerminalSession(
        runners = runners,
        homeDirectories = homes,
        directoryChecker = DirectoryChecker { _, path -> path in EXISTING },
        maxLines = maxLines,
        maxRunOutputChars = maxRunOutputChars,
    )
}

private fun TerminalSession.lines(): List<TerminalLine> = state.value.lines

private fun TerminalLine.statusMessage(): StatusMessage = (this as TerminalLine.Status).message

class TerminalSessionTest {

    @Test
    fun `perintah dijalankan dan output stdout stderr serta kode keluar ditampilkan`() = runTest {
        val runner = FakeRunner {
            flowOf(
                CommandEvent.Output(StreamKind.STDOUT, "hello\nwor"),
                CommandEvent.Output(StreamKind.STDOUT, "ld\n"),
                CommandEvent.Output(StreamKind.STDERR, "oops\n"),
                CommandEvent.Finished(0),
            )
        }
        val session = newSession(runner)

        session.submit("echo hello")

        val lines = session.lines()
        assertEquals(5, lines.size)
        assertEquals("echo hello", (lines[0] as TerminalLine.Command).text)
        assertEquals("/home", (lines[0] as TerminalLine.Command).workingDirectory)
        assertEquals(TerminalLine.Output(lines[1].id, "hello", false), lines[1])
        assertEquals(TerminalLine.Output(lines[2].id, "world", false), lines[2])
        assertEquals(TerminalLine.Output(lines[3].id, "oops", true), lines[3])
        assertEquals(StatusMessage.ExitCode(0), lines[4].statusMessage())
        assertFalse(session.state.value.isRunning)
    }

    @Test
    fun `runner menerima perintah dan direktori kerja`() = runTest {
        val runner = FakeRunner { flowOf(CommandEvent.Finished(0)) }
        val session = newSession(runner)

        session.submit("cd src")
        session.submit("  ls -la  ")

        assertEquals(listOf(CommandRequest("ls -la", "/home/src")), runner.requests)
    }

    @Test
    fun `kode keluar bukan nol dilaporkan apa adanya`() = runTest {
        val runner = FakeRunner { flowOf(CommandEvent.Finished(2)) }
        val session = newSession(runner)

        session.submit("false")

        assertEquals(StatusMessage.ExitCode(2), session.lines().last().statusMessage())
    }

    @Test
    fun `kegagalan runner ditampilkan setelah sisa baris dikeluarkan`() = runTest {
        val runner = FakeRunner {
            flowOf(
                CommandEvent.Output(StreamKind.STDOUT, "setengah"),
                CommandEvent.Failed(TerminalFailure.TERMUX_NOT_INSTALLED, "detail"),
            )
        }
        val session = newSession(runner)

        session.submit("x")

        val lines = session.lines()
        assertEquals("setengah", (lines[1] as TerminalLine.Output).text)
        assertEquals(
            StatusMessage.Failure(TerminalFailure.TERMUX_NOT_INSTALLED, "detail"),
            lines[2].statusMessage(),
        )
    }

    @Test
    fun `pengecualian dari runner menjadi kegagalan IO`() = runTest {
        val runner = FakeRunner { flow { throw IllegalStateException("boom") } }
        val session = newSession(runner)

        session.submit("x")

        assertEquals(
            StatusMessage.Failure(TerminalFailure.IO_ERROR, "boom"),
            session.lines().last().statusMessage(),
        )
        assertFalse(session.state.value.isRunning)
    }

    @Test
    fun `cd pindah direktori tanpa memanggil runner`() = runTest {
        val runner = FakeRunner { flowOf(CommandEvent.Finished(0)) }
        val session = newSession(runner)

        session.submit("cd src")
        assertEquals("/home/src", session.state.value.workingDirectory)
        session.submit("cd ..")
        assertEquals("/home", session.state.value.workingDirectory)
        session.submit("cd src")
        session.submit("cd")
        assertEquals("/home", session.state.value.workingDirectory)
        session.submit("cd /tmp")
        assertEquals("/tmp", session.state.value.workingDirectory)
        session.submit("cd ~/src")
        assertEquals("/home/src", session.state.value.workingDirectory)
        assertTrue(runner.requests.isEmpty())
    }

    @Test
    fun `cd ke folder yang tidak ada menampilkan error dan tidak mengubah direktori`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })

        session.submit("cd nope")

        assertEquals("/home", session.state.value.workingDirectory)
        assertEquals(StatusMessage.DirectoryNotFound("/home/nope"), session.lines().last().statusMessage())
    }

    @Test
    fun `cd dengan argumen tidak valid menampilkan petunjuk`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })

        session.submit("cd a b")

        assertEquals(StatusMessage.CdUsage, session.lines().last().statusMessage())
        assertEquals("/home", session.state.value.workingDirectory)
    }

    @Test
    fun `cd dengan operator shell dijalankan oleh shell dan tidak mengubah direktori sesi`() = runTest {
        val runner = FakeRunner { flowOf(CommandEvent.Finished(0)) }
        val session = newSession(runner)

        session.submit("cd src && ls")

        assertEquals(listOf(CommandRequest("cd src && ls", "/home")), runner.requests)
        assertEquals("/home", session.state.value.workingDirectory)
    }

    @Test
    fun `clear menghapus scrollback`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })
        session.submit("ls")
        assertTrue(session.lines().isNotEmpty())

        session.submit("clear")

        assertTrue(session.lines().isEmpty())
    }

    @Test
    fun `help menampilkan bantuan`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })

        session.submit("help")

        assertEquals(StatusMessage.Help, session.lines().last().statusMessage())
    }

    @Test
    fun `history menampilkan riwayat bernomor`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })
        session.submit("ls")
        session.submit("pwd")
        session.clear()

        session.submit("history")

        val texts = session.lines().filterIsInstance<TerminalLine.Output>().map { it.text }
        assertEquals(listOf("1  ls", "2  pwd", "3  history"), texts)
    }

    @Test
    fun `masukan kosong diabaikan`() = runTest {
        val runner = FakeRunner { flowOf(CommandEvent.Finished(0)) }
        val session = newSession(runner)

        session.submit("   ")

        assertTrue(session.lines().isEmpty())
        assertTrue(runner.requests.isEmpty())
        assertTrue(session.historyEntries.isEmpty())
    }

    @Test
    fun `membatalkan coroutine menghentikan perintah dan menambahkan baris dihentikan`() = runTest {
        val runner = FakeRunner {
            flow {
                emit(CommandEvent.Output(StreamKind.STDOUT, "mulai\nsetengah"))
                awaitCancellation()
            }
        }
        val session = newSession(runner)

        val job = launch { session.submit("sleep 100") }
        runCurrent()
        assertTrue(session.state.value.isRunning)
        assertEquals("mulai", (session.lines()[1] as TerminalLine.Output).text)

        job.cancelAndJoin()

        assertFalse(session.state.value.isRunning)
        val lines = session.lines()
        assertEquals("setengah", (lines[2] as TerminalLine.Output).text)
        assertEquals(StatusMessage.Cancelled(TerminalBackend.LOCAL_SHELL), lines.last().statusMessage())
    }

    @Test
    fun `pembatalan mencatat backend yang dipakai`() = runTest {
        val termux = FakeRunner(TerminalBackend.TERMUX) { flow { awaitCancellation() } }
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) }, termux)
        session.selectBackend(TerminalBackend.TERMUX)

        val job = launch { session.submit("sleep 100") }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(StatusMessage.Cancelled(TerminalBackend.TERMUX), session.lines().last().statusMessage())
    }

    @Test
    fun `masukan baru ditolak selama perintah berjalan`() = runTest {
        val runner = FakeRunner { flow { awaitCancellation() } }
        val session = newSession(runner)
        val job = launch { session.submit("sleep 100") }
        runCurrent()

        session.submit("ls")

        assertEquals(1, runner.requests.size)
        assertFalse(session.selectBackend(TerminalBackend.LOCAL_SHELL))
        assertFalse(session.changeDirectory("/tmp"))
        job.cancelAndJoin()
    }

    @Test
    fun `output melebihi batas menghentikan perintah`() = runTest {
        var emitted = 0
        val runner = FakeRunner {
            flow {
                repeat(10) {
                    emitted++
                    emit(CommandEvent.Output(StreamKind.STDOUT, "x".repeat(30) + "\n"))
                }
                emit(CommandEvent.Finished(0))
            }
        }
        val session = newSession(runner, maxRunOutputChars = 50)

        session.submit("yes")

        assertEquals(StatusMessage.OutputLimit, session.lines().last().statusMessage())
        assertEquals(2, emitted)
        assertFalse(session.state.value.isRunning)
        assertTrue(session.lines().none { it is TerminalLine.Status && it.message is StatusMessage.ExitCode })
    }

    @Test
    fun `scrollback dibatasi dan baris terbaru dipertahankan`() = runTest {
        val runner = FakeRunner {
            flowOf(
                CommandEvent.Output(StreamKind.STDOUT, (1..10).joinToString("") { "baris$it\n" }),
                CommandEvent.Finished(0),
            )
        }
        val session = newSession(runner, maxLines = 5)

        session.submit("seq")

        val lines = session.lines()
        assertEquals(5, lines.size)
        assertEquals("baris10", (lines[3] as TerminalLine.Output).text)
        assertEquals(StatusMessage.ExitCode(0), lines[4].statusMessage())
    }

    @Test
    fun `id baris unik dan naik`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Output(StreamKind.STDOUT, "a\nb\n"), CommandEvent.Finished(0)) })
        session.submit("x")
        session.submit("y")

        val ids = session.lines().map { it.id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `direktori kerja disimpan per backend`() = runTest {
        val local = FakeRunner { flowOf(CommandEvent.Finished(0)) }
        val termux = FakeRunner(TerminalBackend.TERMUX) { flowOf(CommandEvent.Finished(0)) }
        val session = newSession(local, termux)

        session.submit("cd src")
        assertTrue(session.selectBackend(TerminalBackend.TERMUX))
        assertEquals(TerminalBackend.TERMUX, session.state.value.backend)
        assertEquals("/termux/home", session.state.value.workingDirectory)

        session.submit("cd sub")
        assertEquals("/termux/home/sub", session.state.value.workingDirectory)
        session.submit("ls")
        assertEquals(listOf(CommandRequest("ls", "/termux/home/sub")), termux.requests)

        assertTrue(session.selectBackend(TerminalBackend.LOCAL_SHELL))
        assertEquals("/home/src", session.state.value.workingDirectory)
    }

    @Test
    fun `backend yang tidak tersedia ditolak`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })

        assertFalse(session.selectBackend(TerminalBackend.TERMUX))
        assertEquals(TerminalBackend.LOCAL_SHELL, session.state.value.backend)
    }

    @Test
    fun `changeDirectory publik memvalidasi folder`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })

        assertTrue(session.changeDirectory("/tmp"))
        assertEquals("/tmp", session.state.value.workingDirectory)

        assertFalse(session.changeDirectory("/tidak/ada"))
        assertEquals("/tmp", session.state.value.workingDirectory)
        assertEquals(StatusMessage.DirectoryNotFound("/tidak/ada"), session.lines().last().statusMessage())

        assertTrue(session.goHome())
        assertEquals("/home", session.state.value.workingDirectory)
    }

    @Test
    fun `navigasi riwayat lewat sesi`() = runTest {
        val session = newSession(FakeRunner { flowOf(CommandEvent.Finished(0)) })
        session.submit("ls")
        session.submit("pwd")

        assertEquals("pwd", session.historyPrevious("draf"))
        assertEquals("ls", session.historyPrevious("draf"))
        assertEquals("pwd", session.historyNext())
        assertEquals("draf", session.historyNext())
    }
}
