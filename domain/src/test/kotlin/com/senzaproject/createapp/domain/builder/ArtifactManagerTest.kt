package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactManagerTest {

    private class FakeStore(val files: MutableList<StoredArtifact>) : ArtifactStore {
        var listResult: AppResult<List<StoredArtifact>>? = null
        var validateCalls = 0
        var validateResult = ArtifactValidation.VALID
        val deleted = ArrayList<String>()
        var deleteFails = false
        var copyBytes: Long? = null
        var copyFails = false

        override suspend fun list(treeUri: String): AppResult<List<StoredArtifact>> =
            listResult ?: AppResult.Success(files.toList())

        override suspend fun validate(treeUri: String, artifact: StoredArtifact): ArtifactValidation {
            validateCalls++
            return validateResult
        }

        override suspend fun delete(treeUri: String, artifact: StoredArtifact): AppResult<Unit> {
            if (deleteFails) return AppResult.Failure(AppError.Storage("gagal"))
            deleted.add(artifact.key)
            files.removeAll { it.key == artifact.key }
            return AppResult.Success(Unit)
        }

        override suspend fun copyTo(
            treeUri: String,
            artifact: StoredArtifact,
            output: OutputStream,
            onProgress: (Long) -> Unit,
        ): AppResult<Long> {
            if (copyFails) return AppResult.Failure(AppError.Storage("disk penuh"))
            val bytes = copyBytes ?: artifact.sizeBytes
            output.write(ByteArray(bytes.toInt()) { 7 })
            onProgress(bytes)
            return AppResult.Success(bytes)
        }
    }

    private class FakeHistory(var records: List<BuildRecord> = emptyList(), var failLoad: Boolean = false) : BuildHistoryStore {
        override suspend fun load(): List<BuildRecord> {
            if (failLoad) throw IllegalStateException("riwayat rusak")
            return records
        }

        override suspend fun append(record: BuildRecord, logText: String) = Unit
        override suspend fun readLog(id: String): String? = null
        override suspend fun clear() = Unit
    }

    private fun file(name: String = "app-debug.apk", size: Long = 10L, modified: Long = 100L, project: String = "p1") =
        StoredArtifact(project, name, ArtifactRules.kindOf(name)!!, size, modified)

    private fun record(id: String, started: Long, names: List<String> = listOf("app-debug.apk")) = BuildRecord(
        id, "p1", "Proyek", BuildType.DEBUG, BuildStatus.SUCCESS, started, 10L, 1, 0, 0, names, null, null,
    )

    private fun snapshot(result: AppResult<ArtifactSnapshot>): ArtifactSnapshot = (result as AppResult.Success).value

    private val tree = "content://tree"

    @Test
    fun `refresh membaca file nyata dan menggabungkan riwayat`() = runBlocking {
        val store = FakeStore(mutableListOf(file()))
        val manager = ArtifactManager(store, FakeHistory(listOf(record("r1", 50L))))
        val snap = snapshot(manager.refresh(tree, mapOf("p1" to "Proyek Satu")))
        assertEquals(1, snap.entries.size)
        assertEquals("Proyek Satu", snap.entries[0].projectName)
        assertEquals("r1", snap.entries[0].build?.recordId)
        assertEquals(ArtifactValidation.NOT_CHECKED, snap.entries[0].validation)
        assertEquals(1, snap.history.size)
        assertTrue(snap.history[0].artifacts.single().available)
    }

    @Test
    fun `refresh gagal bila folder tidak terbaca`() = runBlocking {
        val store = FakeStore(mutableListOf())
        store.listResult = AppResult.Failure(AppError.Permission("ditolak"))
        val result = ArtifactManager(store, FakeHistory()).refresh(tree, emptyMap())
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Permission)
    }

    @Test
    fun `riwayat yang rusak tidak membuat daftar gagal`() = runBlocking {
        val store = FakeStore(mutableListOf(file()))
        val snap = snapshot(ArtifactManager(store, FakeHistory(failLoad = true)).refresh(tree, emptyMap()))
        assertEquals(1, snap.entries.size)
        assertTrue(snap.history.isEmpty())
    }

    @Test
    fun `folder kosong menghasilkan daftar kosong, bukan data palsu`() = runBlocking {
        val snap = snapshot(ArtifactManager(FakeStore(mutableListOf()), FakeHistory(listOf(record("r1", 1L)))).refresh(tree, emptyMap()))
        assertTrue(snap.entries.isEmpty())
        assertEquals(0, snap.summary.count)
        assertFalse(snap.history.single().artifacts.single().available)
    }

    @Test
    fun `verify memeriksa isi sekali lalu memakai cache selama file tidak berubah`() = runBlocking {
        val store = FakeStore(mutableListOf(file()))
        val manager = ArtifactManager(store, FakeHistory())
        val entry = snapshot(manager.refresh(tree, emptyMap())).entries.single()
        val first = manager.verify(tree, entry)
        val second = manager.verify(tree, entry)
        assertEquals(ArtifactValidation.VALID, first.validation)
        assertEquals(ArtifactValidation.VALID, second.validation)
        assertEquals(1, store.validateCalls)
        // Setelah refresh, hasil sudah ikut terbawa di entri.
        assertEquals(ArtifactValidation.VALID, snapshot(manager.refresh(tree, emptyMap())).entries.single().validation)
    }

    @Test
    fun `file yang berubah diperiksa ulang`() = runBlocking {
        val store = FakeStore(mutableListOf(file(size = 10L, modified = 100L)))
        val manager = ArtifactManager(store, FakeHistory())
        manager.verify(tree, snapshot(manager.refresh(tree, emptyMap())).entries.single())
        store.files[0] = file(size = 11L, modified = 200L)
        val entry = snapshot(manager.refresh(tree, emptyMap())).entries.single()
        assertEquals(ArtifactValidation.NOT_CHECKED, entry.validation)
        manager.verify(tree, entry)
        assertEquals(2, store.validateCalls)
    }

    @Test
    fun `hasil INVALID juga disimpan dan hasil MISSING tidak`() = runBlocking {
        val store = FakeStore(mutableListOf(file()))
        val manager = ArtifactManager(store, FakeHistory())
        val entry = snapshot(manager.refresh(tree, emptyMap())).entries.single()
        store.validateResult = ArtifactValidation.INVALID
        assertEquals(ArtifactValidation.INVALID, manager.verify(tree, entry).validation)
        manager.verify(tree, entry)
        assertEquals(1, store.validateCalls)

        val store2 = FakeStore(mutableListOf(file()))
        store2.validateResult = ArtifactValidation.MISSING
        val manager2 = ArtifactManager(store2, FakeHistory())
        val entry2 = snapshot(manager2.refresh(tree, emptyMap())).entries.single()
        manager2.verify(tree, entry2)
        manager2.verify(tree, entry2)
        assertEquals(2, store2.validateCalls)
    }

    @Test
    fun `delete menghapus file dan daftar berikutnya tidak memuatnya`() = runBlocking {
        val store = FakeStore(mutableListOf(file(), file(name = "b.apk")))
        val manager = ArtifactManager(store, FakeHistory())
        val entry = snapshot(manager.refresh(tree, emptyMap())).entries.first { it.name == "app-debug.apk" }
        assertTrue(manager.delete(tree, entry) is AppResult.Success)
        assertEquals(listOf("p1/app-debug.apk"), store.deleted)
        assertEquals(listOf("b.apk"), snapshot(manager.refresh(tree, emptyMap())).entries.map { it.name })
    }

    @Test
    fun `delete yang gagal dilaporkan dan file tetap ada`() = runBlocking {
        val store = FakeStore(mutableListOf(file()))
        store.deleteFails = true
        val manager = ArtifactManager(store, FakeHistory())
        val entry = snapshot(manager.refresh(tree, emptyMap())).entries.single()
        assertTrue(manager.delete(tree, entry) is AppResult.Failure)
        assertEquals(1, snapshot(manager.refresh(tree, emptyMap())).entries.size)
    }

    @Test
    fun `nama atau id berbahaya ditolak sebelum menyentuh penyimpanan`() = runBlocking {
        val store = FakeStore(mutableListOf())
        val manager = ArtifactManager(store, FakeHistory())
        val bad = listOf(
            StoredArtifact("p1", "../x.apk", ArtifactKind.APK, 1L, 0L),
            StoredArtifact("p1", "a/b.apk", ArtifactKind.APK, 1L, 0L),
            StoredArtifact("../p", "x.apk", ArtifactKind.APK, 1L, 0L),
            StoredArtifact("p1", "x.apk", ArtifactKind.AAB, 1L, 0L),
            StoredArtifact("p1", "x.txt", ArtifactKind.APK, 1L, 0L),
        )
        for (artifact in bad) {
            val entry = ArtifactEntry(artifact, null, null, ArtifactValidation.VALID)
            assertTrue("delete ${artifact.key}", manager.delete(tree, entry) is AppResult.Failure)
            assertTrue("copy ${artifact.key}", manager.copy(tree, entry, ByteArrayOutputStream()) is AppResult.Failure)
        }
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `copy berhasil hanya bila jumlah byte sama dengan ukuran di daftar`() = runBlocking {
        val store = FakeStore(mutableListOf(file(size = 10L)))
        val manager = ArtifactManager(store, FakeHistory())
        val entry = snapshot(manager.refresh(tree, emptyMap())).entries.single()
        val out = ByteArrayOutputStream()
        var progress = 0L
        val ok = manager.copy(tree, entry, out) { progress = it }
        assertEquals(10L, (ok as AppResult.Success).value)
        assertEquals(10, out.size())
        assertEquals(10L, progress)

        store.copyBytes = 9L
        val short = manager.copy(tree, entry, ByteArrayOutputStream())
        assertTrue(short is AppResult.Failure)
    }

    @Test
    fun `copy yang gagal di penyimpanan diteruskan apa adanya`() = runBlocking {
        val store = FakeStore(mutableListOf(file()))
        store.copyFails = true
        val manager = ArtifactManager(store, FakeHistory())
        val entry = snapshot(manager.refresh(tree, emptyMap())).entries.single()
        val result = manager.copy(tree, entry, ByteArrayOutputStream())
        assertTrue((result as AppResult.Failure).error is AppError.Storage)
    }
}
