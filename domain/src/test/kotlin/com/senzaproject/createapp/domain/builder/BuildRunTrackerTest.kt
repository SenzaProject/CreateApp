package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.terminal.RunnerNotice
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildRunTrackerTest {

    private val successOutput = """
        @@CA@helper=1
        @@CA@phase=prepare
        @@CA@phase=wrapper
        @@CA@phase=build
        @@CA@log=/data/data/com.termux/files/home/.createapp/logs/p1.log
        > Task :app:preBuild UP-TO-DATE
        > Task :app:assembleDebug
        BUILD SUCCESSFUL in 3s
        @@CA@exit=0
        @@CA@phase=collect
        @@CA@artifact=APK|8|app-debug.apk
        @@CA@done=1
    """.trimIndent() + "\n"

    private fun tracker(output: String): BuildRunTracker {
        val t = BuildRunTracker()
        t.onOutput(StreamKind.STDOUT, output)
        t.finishOutput()
        return t
    }

    @Test
    fun `build sukses memisahkan penanda dari log dan mengumpulkan artefak`() {
        val t = tracker(successOutput)
        val resolution = t.resolve(RunEnd.Finished(0), cancelRequested = false)
        assertEquals(BuildStatus.SUCCESS, resolution.status)
        assertNull(resolution.failure)
        assertEquals(listOf(BuildArtifact(ArtifactKind.APK, "app-debug.apk", 8L)), t.artifacts)
        assertEquals(2, t.tasksSeen)
        assertEquals(":app:assembleDebug", t.currentTask)
        assertEquals("3s", t.gradleDuration)
        assertFalse(t.logLines.any { it.startsWith("@@CA@") })
        assertTrue(t.logLines.contains("BUILD SUCCESSFUL in 3s"))
        assertEquals(BuildPhase.COLLECT, t.phase)
    }

    @Test
    fun `gradle gagal menghasilkan GradleExit walau proses helper keluar nol`() {
        val t = tracker("> Task :app:x FAILED\nBUILD FAILED in 2s\n@@CA@exit=1\n@@CA@done=1\n")
        val resolution = t.resolve(RunEnd.Finished(1), cancelRequested = false)
        assertEquals(BuildStatus.FAILED, resolution.status)
        assertEquals(BuildFailure.GradleExit(1), resolution.failure)

        val t2 = tracker("BUILD FAILED in 2s\n@@CA@exit=1\n@@CA@done=1\n")
        assertEquals(BuildFailure.GradleExit(1), t2.resolve(RunEnd.Finished(0), false).failure)
    }

    @Test
    fun `error helper diteruskan`() {
        val t = tracker("@@CA@error=NO_JAVA\n@@CA@done=1\n")
        val resolution = t.resolve(RunEnd.Finished(4), cancelRequested = false)
        assertEquals(BuildStatus.FAILED, resolution.status)
        assertEquals(BuildFailure.Helper(HelperError.NO_JAVA), resolution.failure)
    }

    @Test
    fun `pembatalan menang atas hasil lain`() {
        val t = tracker("@@CA@cancelled=1\n@@CA@done=1\n")
        assertEquals(BuildStatus.CANCELLED, t.resolve(RunEnd.Finished(143), false).status)
        val t2 = tracker(successOutput)
        assertEquals(BuildStatus.CANCELLED, t2.resolve(RunEnd.Finished(0), cancelRequested = true).status)
    }

    @Test
    fun `berhenti menunggu saat dibatalkan ditandai mungkin masih berjalan`() {
        val resolution = BuildRunTracker().resolve(RunEnd.Stopped, cancelRequested = true)
        assertEquals(BuildStatus.CANCELLED, resolution.status)
        assertTrue(resolution.mayStillRun)
        assertFalse(tracker("@@CA@cancelled=1\n").resolve(RunEnd.Finished(143), true).mayStillRun)
    }

    @Test
    fun `berhenti menunggu tanpa permintaan batal dianggap gagal waktu habis`() {
        val resolution = BuildRunTracker().resolve(RunEnd.Stopped, cancelRequested = false)
        assertEquals(BuildStatus.FAILED, resolution.status)
        assertEquals(BuildFailure.Runner(TerminalFailure.TIMEOUT, null), resolution.failure)
    }

    @Test
    fun `kegagalan runner diteruskan`() {
        val resolution = BuildRunTracker().resolve(
            RunEnd.Failed(TerminalFailure.TERMUX_EXTERNAL_APPS_DISABLED, "detail"),
            cancelRequested = false,
        )
        assertEquals(BuildStatus.FAILED, resolution.status)
        assertEquals(
            BuildFailure.Runner(TerminalFailure.TERMUX_EXTERNAL_APPS_DISABLED, "detail"),
            resolution.failure,
        )
    }

    @Test
    fun `sukses tanpa penanda done dianggap tidak lengkap`() {
        val t = tracker("BUILD SUCCESSFUL in 3s\n@@CA@exit=0\n@@CA@artifact=APK|8|app-debug.apk\n")
        val resolution = t.resolve(RunEnd.Finished(0), cancelRequested = false)
        assertEquals(BuildStatus.FAILED, resolution.status)
        assertEquals(BuildFailure.IncompleteOutput, resolution.failure)
    }

    @Test
    fun `sukses tanpa penanda exit dianggap tidak lengkap`() {
        val t = tracker("@@CA@artifact=APK|8|app-debug.apk\n@@CA@done=1\n")
        assertEquals(BuildFailure.IncompleteOutput, t.resolve(RunEnd.Finished(0), false).failure)
    }

    @Test
    fun `sukses tanpa artefak dianggap gagal`() {
        val t = tracker("@@CA@exit=0\n@@CA@done=1\n")
        val resolution = t.resolve(RunEnd.Finished(0), cancelRequested = false)
        assertEquals(BuildStatus.FAILED, resolution.status)
        assertEquals(BuildFailure.Helper(HelperError.NO_ARTIFACT), resolution.failure)
    }

    @Test
    fun `penanda palsu dari log proyek tidak bisa mengubah kode keluar proses`() {
        val t = tracker("@@CA@exit=0\n@@CA@artifact=APK|8|x.apk\n@@CA@done=1\n")
        val resolution = t.resolve(RunEnd.Finished(1), cancelRequested = false)
        assertEquals(BuildStatus.FAILED, resolution.status)
    }

    @Test
    fun `output terpotong di awal tetap sukses bila penanda akhir utuh`() {
        val t = tracker("BUILD SUCCESSFUL in 3s\n@@CA@exit=0\n@@CA@phase=collect\n@@CA@artifact=APK|8|app-debug.apk\n@@CA@done=1\n")
        assertEquals(BuildStatus.SUCCESS, t.resolve(RunEnd.Finished(0), false).status)
    }

    @Test
    fun `potongan teks yang terputus di tengah baris dirakit`() {
        val t = BuildRunTracker()
        t.onOutput(StreamKind.STDOUT, "> Task :app:com")
        t.onOutput(StreamKind.STDOUT, "pileDebugKotlin\n@@CA@exi")
        t.onOutput(StreamKind.STDOUT, "t=0\n@@CA@done=1")
        t.finishOutput()
        assertEquals(":app:compileDebugKotlin", t.currentTask)
        assertEquals(0, t.gradleExitCode)
        assertTrue(t.helperDone)
    }

    @Test
    fun `log dibatasi dan jumlah baris yang dibuang dihitung`() {
        val t = BuildRunTracker(maxLogLines = 5)
        val text = (1..12).joinToString("") { "baris $it\n" }
        t.onOutput(StreamKind.STDOUT, text)
        t.finishOutput()
        assertEquals(5, t.logLines.size)
        assertEquals("baris 12", t.logLines.last())
        assertEquals(7, t.droppedLogLines)
        assertTrue(t.logText().startsWith("… 7 baris awal dibuang"))
    }

    @Test
    fun `catatan runner dicatat`() {
        val t = BuildRunTracker()
        t.onNotice(RunnerNotice.TERMUX_NO_RESPONSE_YET)
        t.onNotice(RunnerNotice.TERMUX_OUTPUT_TRUNCATED)
        assertTrue(t.noResponseYet)
        assertTrue(t.outputTruncated)
    }

    @Test
    fun `stderr juga dibaca`() {
        val t = BuildRunTracker()
        t.onOutput(StreamKind.STDERR, "e: file:///a/B.kt:1:2 Boom\n")
        t.finishOutput()
        assertEquals(1, t.errorCount)
    }
}
