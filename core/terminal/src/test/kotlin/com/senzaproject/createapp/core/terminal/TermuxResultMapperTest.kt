package com.senzaproject.createapp.core.terminal

import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.RunnerNotice
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxResultMapperTest {

    private fun data(
        stdout: String? = null,
        stderr: String? = null,
        exitCode: Int? = 0,
        errCode: Int? = -1,
        errMessage: String? = null,
        stdoutOriginalLength: Int? = null,
        stderrOriginalLength: Int? = null,
    ) = TermuxResultData(stdout, stderr, exitCode, errCode, errMessage, stdoutOriginalLength, stderrOriginalLength)

    @Test
    fun `sukses membawa stdout lalu kode keluar`() {
        val events = TermuxResultMapper.toEvents(data(stdout = "a\n", exitCode = 0))
        assertEquals(listOf(CommandEvent.Output(StreamKind.STDOUT, "a\n"), CommandEvent.Finished(0)), events)
    }

    @Test
    fun `stderr dipisahkan dari stdout`() {
        val events = TermuxResultMapper.toEvents(data(stdout = "o\n", stderr = "e\n", exitCode = 1))
        assertEquals(
            listOf(
                CommandEvent.Output(StreamKind.STDOUT, "o\n"),
                CommandEvent.Output(StreamKind.STDERR, "e\n"),
                CommandEvent.Finished(1),
            ),
            events,
        )
    }

    @Test
    fun `output kosong tidak menghasilkan peristiwa output`() {
        assertEquals(listOf<CommandEvent>(CommandEvent.Finished(0)), TermuxResultMapper.toEvents(data(stdout = "", stderr = null)))
    }

    @Test
    fun `err RESULT_OK minus satu, nol, atau null berarti tanpa error internal`() {
        for (code in listOf(-1, 0, null)) {
            val events = TermuxResultMapper.toEvents(data(errCode = code, errMessage = ""))
            assertEquals("err=$code", listOf<CommandEvent>(CommandEvent.Finished(0)), events)
        }
    }

    @Test
    fun `error internal Termux menjadi kegagalan dan output tetap dipertahankan`() {
        val events = TermuxResultMapper.toEvents(data(stdout = "x\n", exitCode = null, errCode = 100, errMessage = "boom"))
        assertEquals(
            listOf(
                CommandEvent.Output(StreamKind.STDOUT, "x\n"),
                CommandEvent.Failed(TerminalFailure.TERMUX_ERROR, "boom"),
            ),
            events,
        )
    }

    @Test
    fun `pesan tentang allow-external-apps dikenali`() {
        val events = TermuxResultMapper.toEvents(
            data(exitCode = null, errCode = 1, errMessage = "RunCommandService requires allow-external-apps property"),
        )
        val failed = events.single() as CommandEvent.Failed
        assertEquals(TerminalFailure.TERMUX_EXTERNAL_APPS_DISABLED, failed.failure)
    }

    @Test
    fun `kode error tanpa pesan tetap dianggap error`() {
        val failed = TermuxResultMapper.toEvents(data(exitCode = null, errCode = 7, errMessage = null)).single() as CommandEvent.Failed
        assertEquals(TerminalFailure.TERMUX_ERROR, failed.failure)
        assertEquals("err=7", failed.detail)
    }

    @Test
    fun `tanpa kode keluar dan tanpa error berarti tidak ada hasil`() {
        val events = TermuxResultMapper.toEvents(data(exitCode = null))
        assertEquals(listOf<CommandEvent>(CommandEvent.Failed(TerminalFailure.TERMUX_NO_RESULT, null)), events)
    }

    @Test
    fun `output yang dipotong Termux diberi tahu lebih dulu`() {
        val events = TermuxResultMapper.toEvents(data(stdout = "x".repeat(100), stdoutOriginalLength = 500))
        assertEquals(CommandEvent.Notice(RunnerNotice.TERMUX_OUTPUT_TRUNCATED), events.first())
        assertEquals(CommandEvent.Finished(0), events.last())
    }

    @Test
    fun `panjang asli sama dengan panjang output berarti tidak dipotong`() {
        val events = TermuxResultMapper.toEvents(data(stdout = "abc", stdoutOriginalLength = 3, stderrOriginalLength = 0))
        assertEquals(listOf(CommandEvent.Output(StreamKind.STDOUT, "abc"), CommandEvent.Finished(0)), events)
    }
}
