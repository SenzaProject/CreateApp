package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.terminal.CommandEvent
import com.senzaproject.createapp.domain.terminal.CommandRequest
import com.senzaproject.createapp.domain.terminal.CommandRunner
import com.senzaproject.createapp.domain.terminal.StreamKind
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import com.senzaproject.createapp.domain.terminal.TerminalFailure
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildSessionTest {

    private class FakeWorkspace(
        var syncResult: AppResult<SyncedWorkspace> = AppResult.Success(SyncedWorkspace(3, 100L)),
        var validation: ArtifactValidation = ArtifactValidation.VALID,
    ) : BuildWorkspace {
        val syncCalls = AtomicInteger(0)

        override suspend fun sync(projectId: String, treeUri: String): AppResult<SyncedWorkspace> {
            syncCalls.incrementAndGet()
            return syncResult
        }

        override suspend fun verifyArtifacts(
            treeUri: String,
            projectId: String,
            reported: List<BuildArtifact>,
        ): AppResult<List<BuildArtifact>> =
            AppResult.Success(reported.map { it.copy(validation = validation) })
    }

    private class FakeHistory : BuildHistoryStore {
        val records = CopyOnWriteArrayList<BuildRecord>()
        val logs = ConcurrentHashMap<String, String>()

        override suspend fun load(): List<BuildRecord> = records.toList().reversed()

        override suspend fun append(record: BuildRecord, logText: String) {
            records.add(record)
            logs[record.id] = logText
        }

        override suspend fun readLog(id: String): String? = logs[id]

        override suspend fun clear() {
            records.clear()
            logs.clear()
        }
    }

    private class FakeRunner(private val handler: (CommandRequest) -> Flow<CommandEvent>) : CommandRunner {
        override val backend: TerminalBackend = TerminalBackend.TERMUX
        val commands = CopyOnWriteArrayList<String>()

        override fun run(request: CommandRequest): Flow<CommandEvent> {
            commands.add(request.command)
            return handler(request)
        }
    }

    private val successOutput = "@@CA@helper=1\n@@CA@phase=build\n> Task :app:assembleDebug\n" +
        "BUILD SUCCESSFUL in 3s\n@@CA@exit=0\n@@CA@phase=collect\n" +
        "@@CA@artifact=APK|8|app-debug.apk\n@@CA@done=1\n"

    private val request = BuildRequest(
        projectId = "p1",
        projectName = "Proyek Satu",
        type = BuildType.DEBUG,
        sharedTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FB",
        sharedRootPath = "/storage/emulated/0/Download/B",
    )

    private fun newSession(
        scope: CoroutineScope,
        runner: CommandRunner,
        workspace: BuildWorkspace = FakeWorkspace(),
        history: BuildHistoryStore = FakeHistory(),
        graceMillis: Long = 60_000L,
    ): BuildSession {
        val ids = AtomicInteger(0)
        return BuildSession(
            runner = runner,
            workspace = workspace,
            commands = BuildCommandFactory("QUJD"),
            history = history,
            scope = scope,
            termuxHome = "/home",
            cancelGraceMillis = graceMillis,
            newId = { "id-" + ids.incrementAndGet() },
        )
    }

    private fun outputThenFinish(text: String, exit: Int): Flow<CommandEvent> = flow {
        emit(CommandEvent.Output(StreamKind.STDOUT, text))
        emit(CommandEvent.Finished(exit))
    }

    private suspend fun BuildSession.awaitFinished(): BuildState = withTimeout(10_000L) {
        state.first { !it.isActive && it.status != BuildStatus.IDLE }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `build sukses memeriksa artefak dan mencatat riwayat`() = runBlocking {
        val scope = newScope()
        try {
            val runner = FakeRunner { outputThenFinish(successOutput, 0) }
            val workspace = FakeWorkspace()
            val history = FakeHistory()
            val session = newSession(scope, runner, workspace, history)

            assertTrue(session.start(request))
            val done = session.awaitFinished()

            assertEquals(BuildStatus.SUCCESS, done.status)
            assertNull(done.failure)
            assertEquals(1, done.artifacts.size)
            assertEquals(ArtifactValidation.VALID, done.artifacts[0].validation)
            assertEquals(1, workspace.syncCalls.get())
            assertEquals(1, runner.commands.size)
            assertTrue(runner.commands[0].contains("--name 'p1'"))
            assertTrue(runner.commands[0].contains("--task assembleDebug"))
            assertTrue(runner.commands[0].contains("--source '/storage/emulated/0/Download/B/src/p1'"))
            assertTrue(runner.commands[0].contains("--out '/storage/emulated/0/Download/B/out'"))
            assertEquals(1, history.records.size)
            assertEquals(BuildStatus.SUCCESS, history.records[0].status)
            assertEquals(listOf("app-debug.apk"), history.records[0].artifactNames)
            assertTrue(history.logs["id-1"].orEmpty().contains("BUILD SUCCESSFUL in 3s"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `artefak yang tidak valid membuat build gagal walau Gradle sukses`() = runBlocking {
        val scope = newScope()
        try {
            val runner = FakeRunner { outputThenFinish(successOutput, 0) }
            val session = newSession(scope, runner, FakeWorkspace(validation = ArtifactValidation.INVALID))
            session.start(request)
            val done = session.awaitFinished()
            assertEquals(BuildStatus.FAILED, done.status)
            assertEquals(BuildFailure.ArtifactInvalid(listOf("app-debug.apk")), done.failure)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `gagal menyalin proyek tidak menjalankan perintah Termux`() = runBlocking {
        val scope = newScope()
        try {
            val runner = FakeRunner { outputThenFinish(successOutput, 0) }
            val history = FakeHistory()
            val workspace = FakeWorkspace(syncResult = AppResult.Failure(AppError.Storage("penuh")))
            val session = newSession(scope, runner, workspace, history)
            session.start(request)
            val done = session.awaitFinished()
            assertEquals(BuildStatus.FAILED, done.status)
            assertTrue(done.failure is BuildFailure.Sync)
            assertTrue(runner.commands.isEmpty())
            assertEquals("SYNC", history.records[0].failureCode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Gradle gagal menampilkan masalah dan kode keluar`() = runBlocking {
        val scope = newScope()
        try {
            val output = "> Task :app:compileDebugKotlin FAILED\n" +
                "e: file:///a/Main.kt:3:5 Unresolved reference 'foo'\n" +
                "BUILD FAILED in 2s\n@@CA@exit=1\n@@CA@done=1\n"
            val history = FakeHistory()
            val session = newSession(scope, FakeRunner { outputThenFinish(output, 1) }, history = history)
            session.start(request)
            val done = session.awaitFinished()
            assertEquals(BuildStatus.FAILED, done.status)
            assertEquals(BuildFailure.GradleExit(1), done.failure)
            assertEquals(1, done.issues.size)
            assertEquals("Unresolved reference 'foo'", done.issues[0].message)
            assertTrue(done.artifacts.isEmpty())
            assertEquals("GRADLE_EXIT", history.records[0].failureCode)
            assertEquals("1", history.records[0].failureDetail)
            assertEquals(1, history.records[0].errorCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `kegagalan runner menjadi kegagalan build`() = runBlocking {
        val scope = newScope()
        try {
            val runner = FakeRunner {
                flow { emit(CommandEvent.Failed(TerminalFailure.TERMUX_EXTERNAL_APPS_DISABLED, "x")) }
            }
            val session = newSession(scope, runner)
            session.start(request)
            val done = session.awaitFinished()
            assertEquals(BuildStatus.FAILED, done.status)
            assertEquals(
                BuildFailure.Runner(TerminalFailure.TERMUX_EXTERNAL_APPS_DISABLED, "x"),
                done.failure,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `build kedua ditolak selama build pertama berjalan`() = runBlocking {
        val scope = newScope()
        try {
            val gate = CompletableDeferred<Unit>()
            val runner = FakeRunner {
                flow {
                    gate.await()
                    emit(CommandEvent.Output(StreamKind.STDOUT, successOutput))
                    emit(CommandEvent.Finished(0))
                }
            }
            val session = newSession(scope, runner)
            assertTrue(session.start(request))
            assertFalse(session.start(request))
            gate.complete(Unit)
            assertEquals(BuildStatus.SUCCESS, session.awaitFinished().status)
            // Setelah selesai, build baru boleh dimulai lagi.
            assertTrue(session.start(request))
            assertEquals(BuildStatus.SUCCESS, session.awaitFinished().status)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `pembatalan mengirim perintah cancel lalu menunggu hasil akhir`() = runBlocking {
        val scope = newScope()
        try {
            val cancelSeen = CompletableDeferred<Unit>()
            val runner = FakeRunner { req ->
                if (req.command.endsWith(" cancel")) {
                    flow {
                        cancelSeen.complete(Unit)
                        emit(CommandEvent.Output(StreamKind.STDOUT, "@@CA@cancel=killed\n"))
                        emit(CommandEvent.Finished(0))
                    }
                } else {
                    flow {
                        emit(CommandEvent.Output(StreamKind.STDOUT, "@@CA@phase=build\n> Task :app:x\n"))
                        cancelSeen.await()
                        emit(CommandEvent.Output(StreamKind.STDOUT, "@@CA@cancelled=1\n@@CA@done=1\n"))
                        emit(CommandEvent.Finished(143))
                    }
                }
            }
            val history = FakeHistory()
            val session = newSession(scope, runner, history = history)
            session.start(request)
            withTimeout(10_000L) { session.state.first { it.phase == BuildPhase.BUILD } }

            session.cancel()
            val done = session.awaitFinished()

            assertEquals(BuildStatus.CANCELLED, done.status)
            assertFalse(done.mayStillRun)
            assertTrue(runner.commands.any { it.endsWith(" cancel") })
            assertEquals(BuildStatus.CANCELLED, history.records[0].status)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Termux yang tidak membalas setelah dibatalkan ditandai mungkin masih berjalan`() = runBlocking {
        val scope = newScope()
        try {
            val runner = FakeRunner { req ->
                if (req.command.endsWith(" cancel")) {
                    flow { emit(CommandEvent.Finished(0)) }
                } else {
                    flow {
                        emit(CommandEvent.Output(StreamKind.STDOUT, "@@CA@phase=build\n"))
                        awaitCancellation()
                    }
                }
            }
            val session = newSession(scope, runner, graceMillis = 300L)
            session.start(request)
            withTimeout(10_000L) { session.state.first { it.phase == BuildPhase.BUILD } }

            session.cancel()
            val done = session.awaitFinished()

            assertEquals(BuildStatus.CANCELLED, done.status)
            assertTrue(done.mayStillRun)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `reset mengembalikan ke keadaan awal hanya bila build sudah selesai`() = runBlocking {
        val scope = newScope()
        try {
            val session = newSession(scope, FakeRunner { outputThenFinish(successOutput, 0) })
            session.start(request)
            session.awaitFinished()
            session.reset()
            assertEquals(BuildStatus.IDLE, session.state.value.status)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancel tanpa build berjalan tidak berbuat apa pun`() = runBlocking {
        val scope = newScope()
        try {
            val runner = FakeRunner { outputThenFinish("", 0) }
            val session = newSession(scope, runner)
            session.cancel()
            assertEquals(BuildStatus.IDLE, session.state.value.status)
            assertTrue(runner.commands.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `pemeriksaan lingkungan membaca butir dari keluaran helper`() = runBlocking {
        val scope = newScope()
        try {
            val output = "@@CA@helper=1\n@@CA@check=arch|OK|aarch64\n@@CA@check=java|MISSING|pkg install openjdk-17\n" +
                "@@CA@check=aapt2|WARN|arm\n@@CA@done=1\n"
            val runner = FakeRunner { outputThenFinish(output, 0) }
            val session = newSession(scope, runner)
            val result = session.checkToolchain("/storage/emulated/0/Download/B")
            assertTrue(result is ToolchainResult.Report)
            val report = (result as ToolchainResult.Report).report
            assertEquals(3, report.items.size)
            assertFalse(report.isReady)
            assertTrue(report.hasWarnings)
            assertTrue(runner.commands[0].endsWith("check --source '/storage/emulated/0/Download/B'"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `pemeriksaan lingkungan tanpa butir dianggap gagal`() = runBlocking {
        val scope = newScope()
        try {
            val session = newSession(scope, FakeRunner { outputThenFinish("tidak ada penanda\n", 0) })
            val result = session.checkToolchain(null)
            assertTrue(result is ToolchainResult.Failed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `pembersihan folder kerja`() = runBlocking {
        val scope = newScope()
        try {
            assertEquals(
                CleanResult.Done,
                newSession(scope, FakeRunner { outputThenFinish("@@CA@clean=ok\n", 0) }).cleanTermuxWork(),
            )
            assertEquals(
                CleanResult.Busy,
                newSession(scope, FakeRunner { outputThenFinish("@@CA@error=ALREADY_RUNNING\n", 3) }).cleanTermuxWork(),
            )
            val failed = newSession(
                scope,
                FakeRunner { flow { emit(CommandEvent.Failed(TerminalFailure.TERMUX_ERROR, null)) } },
            ).cleanTermuxWork()
            assertTrue(failed is CleanResult.Failed)
        } finally {
            scope.cancel()
        }
    }
}
