package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.CommandRequest
import com.senzaproject.createapp.domain.terminal.CommandRunner
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildSessionLiveLogTest {

    private class Workspace : BuildWorkspace {
        override suspend fun sync(projectId: String, treeUri: String): AppResult<SyncedWorkspace> =
            AppResult.Success(SyncedWorkspace(1, 1L))

        override suspend fun verifyArtifacts(
            treeUri: String,
            projectId: String,
            reported: List<BuildArtifact>,
        ): AppResult<List<BuildArtifact>> = AppResult.Success(reported.map { it.copy(validation = ArtifactValidation.VALID) })
    }

    private class History : BuildHistoryStore {
        val records = CopyOnWriteArrayList<BuildRecord>()
        override suspend fun load(): List<BuildRecord> = records.toList()
        override suspend fun append(record: BuildRecord, logText: String) {
            records.add(record)
        }

        override suspend fun readLog(id: String): String? = null
        override suspend fun clear() = Unit
    }

    private class Runner(private val runMillis: Long, private val output: String) : CommandRunner {
        override val backend: TerminalBackend = TerminalBackend.TERMUX
        val commands = CopyOnWriteArrayList<String>()

        override fun run(request: CommandRequest): Flow<CommandEvent> {
            commands.add(request.command)
            return flow {
                if (request.command.contains(" build ")) delay(runMillis)
                emit(CommandEvent.Output(StreamKind.STDOUT, output))
                emit(CommandEvent.Finished(0))
            }
        }
    }

    private class Live(var text: String?) : BuildLiveLogSource {
        val reads = AtomicInteger(0)
        override suspend fun read(treeUri: String, projectId: String, maxBytes: Int): String? {
            reads.incrementAndGet()
            return text
        }
    }

    private val success = "@@CA@helper=1\n@@CA@phase=build\n> Task :app:assembleDebug\nBUILD SUCCESSFUL in 3s\n" +
        "@@CA@exit=0\n@@CA@phase=collect\n@@CA@artifact=APK|8|app-debug.apk\n@@CA@done=1\n"

    private val request = BuildRequest(
        "p1", "Proyek", BuildType.DEBUG,
        "content://com.android.externalstorage.documents/tree/primary%3AB", "/storage/emulated/0/B",
    )

    private fun session(scope: CoroutineScope, runner: CommandRunner, live: BuildLiveLogSource?, history: BuildHistoryStore = History()) =
        BuildSession(
            runner = runner,
            workspace = Workspace(),
            commands = BuildCommandFactory("QUJD"),
            history = history,
            scope = scope,
            termuxHome = "/home",
            liveLog = live,
            liveIntervalMillis = 20L,
            newRunToken = { "tok-1" },
        )

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `log langsung tampil selama build berjalan lalu hilang setelah selesai`() = runBlocking {
        val scope = newScope()
        try {
            val live = Live("${LiveLog.header("tok-1")}\n> Task :app:compileDebugKotlin\n")
            val s = session(scope, Runner(runMillis = 800L, output = success), live)
            assertTrue(s.start(request))
            val during = withTimeout(5_000) { s.state.first { it.liveLines.isNotEmpty() } }
            assertEquals(BuildStatus.RUNNING, during.status)
            assertEquals(":app:compileDebugKotlin", during.liveTask)
            assertEquals(1, during.liveTasksSeen)

            val done = withTimeout(10_000) { s.state.first { !it.isActive } }
            assertEquals(BuildStatus.SUCCESS, done.status)
            assertTrue(done.liveLines.isEmpty())
            assertEquals(null, done.liveTask)
            assertEquals(0, done.liveTasksSeen)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `log sisa build lama dengan token berbeda tidak pernah ditampilkan`() = runBlocking {
        val scope = newScope()
        try {
            val live = Live("# createapp-run token-lama\n> Task :app:lama\n")
            val s = session(scope, Runner(runMillis = 2_000L, output = success), live)
            s.start(request)
            // Biarkan poller membaca log lama beberapa kali, lalu ganti dengan log run ini.
            withTimeout(5_000) { while (live.reads.get() < 3) delay(10L) }
            assertTrue("log lama tidak boleh tampil", s.state.value.liveLines.isEmpty())
            live.text = "${LiveLog.header("tok-1")}\n> Task :app:baru\n"
            val first = withTimeout(5_000) { s.state.first { it.liveLines.isNotEmpty() } }
            assertEquals(listOf("> Task :app:baru"), first.liveLines)
            assertEquals(":app:baru", first.liveTask)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `file belum ada atau pembacaan melempar tidak menggagalkan build`() = runBlocking {
        val scope = newScope()
        try {
            val throwing = object : BuildLiveLogSource {
                override suspend fun read(treeUri: String, projectId: String, maxBytes: Int): String? =
                    throw IllegalStateException("SAF error")
            }
            val s = session(scope, Runner(runMillis = 300L, output = success), throwing)
            s.start(request)
            val done = withTimeout(10_000) { s.state.first { !it.isActive } }
            assertEquals(BuildStatus.SUCCESS, done.status)

            val absent = Live(null)
            val s2 = session(scope, Runner(runMillis = 200L, output = success), absent)
            s2.start(request)
            assertEquals(BuildStatus.SUCCESS, withTimeout(10_000) { s2.state.first { !it.isActive } }.status)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `perintah membawa token hanya bila log langsung dipakai`() = runBlocking {
        val scope = newScope()
        try {
            val withLive = Runner(runMillis = 0L, output = success)
            val a = session(scope, withLive, Live(null))
            a.start(request)
            withTimeout(10_000) { a.state.first { !it.isActive } }
            assertTrue(withLive.commands.any { it.contains("--run 'tok-1'") })

            val without = Runner(runMillis = 0L, output = success)
            val b = session(scope, without, null)
            b.start(request)
            withTimeout(10_000) { b.state.first { !it.isActive } }
            assertFalse(without.commands.any { it.contains("--run") })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `poller berhenti setelah build selesai`() = runBlocking {
        val scope = newScope()
        try {
            val live = Live(null)
            val s = session(scope, Runner(runMillis = 200L, output = success), live)
            s.start(request)
            withTimeout(10_000) { s.state.first { !it.isActive } }
            val readsAtEnd = live.reads.get()
            delay(200L)
            assertEquals("tidak ada pembacaan baru setelah selesai", readsAtEnd, live.reads.get())
        } finally {
            scope.cancel()
        }
    }
}
