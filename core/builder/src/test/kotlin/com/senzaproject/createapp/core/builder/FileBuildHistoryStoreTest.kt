package com.senzaproject.createapp.core.builder

import com.senzaproject.createapp.domain.builder.BuildRecord
import com.senzaproject.createapp.domain.builder.BuildStatus
import com.senzaproject.createapp.domain.builder.BuildType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileBuildHistoryStoreTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File.createTempFile("history", "").also {
            it.delete()
            it.mkdirs()
        }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store(maxRecords: Int = 30, maxLogChars: Int = 1_000_000) =
        FileBuildHistoryStore(directory, Dispatchers.IO, maxRecords, maxLogChars)

    private fun record(id: String, started: Long = 1L) = BuildRecord(
        id = id,
        projectId = "p1",
        projectName = "Proyek",
        type = BuildType.DEBUG,
        status = BuildStatus.SUCCESS,
        startedAtMillis = started,
        durationMillis = 10L,
        tasksSeen = 1,
        errorCount = 0,
        warningCount = 0,
        artifactNames = listOf("app-debug.apk"),
        failureCode = null,
        failureDetail = null,
    )

    @Test
    fun `riwayat kosong di awal`() = runBlocking {
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun `catatan terbaru tampil lebih dulu dan log bisa dibaca`() = runBlocking {
        val s = store()
        s.append(record("a", 1L), "log a")
        s.append(record("b", 2L), "log b")
        assertEquals(listOf("b", "a"), s.load().map { it.id })
        assertEquals("log a", s.readLog("a"))
        assertEquals("log b", s.readLog("b"))
        assertNull(s.readLog("tidak-ada"))
    }

    @Test
    fun `riwayat bertahan setelah toko dibuat ulang`() = runBlocking {
        store().append(record("a"), "log a")
        assertEquals(listOf("a"), store().load().map { it.id })
    }

    @Test
    fun `catatan terlama dibuang bersama lognya`() = runBlocking {
        val s = store(maxRecords = 2)
        s.append(record("a", 1L), "log a")
        s.append(record("b", 2L), "log b")
        s.append(record("c", 3L), "log c")
        assertEquals(listOf("c", "b"), s.load().map { it.id })
        assertNull(s.readLog("a"))
        assertEquals("log c", s.readLog("c"))
    }

    @Test
    fun `id yang sama diganti bukan digandakan`() = runBlocking {
        val s = store()
        s.append(record("a"), "lama")
        s.append(record("a"), "baru")
        assertEquals(1, s.load().size)
        assertEquals("baru", s.readLog("a"))
    }

    @Test
    fun `id tidak aman ditolak dan tidak bisa membaca file lain`() = runBlocking {
        val s = store()
        File(directory, "rahasia.log").writeText("rahasia")
        assertNull(s.readLog("../rahasia"))
        var rejected = false
        try {
            s.append(record("../x"), "x")
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `log disamarkan dan dipotong`() = runBlocking {
        val s = store(maxLogChars = 50)
        s.append(record("a"), "kata ".repeat(60) + "\nakhir")
        val text = s.readLog("a").orEmpty()
        assertTrue(text.endsWith("akhir"))
        assertTrue(text.length < 100)
    }

    @Test
    fun `baris indeks yang rusak dilewati`() = runBlocking {
        val s = store()
        s.append(record("a"), "log")
        File(directory, "history.tsv").appendText("baris rusak\n")
        assertEquals(listOf("a"), s.load().map { it.id })
    }

    @Test
    fun `clear menghapus semuanya`() = runBlocking {
        val s = store()
        s.append(record("a"), "log")
        s.clear()
        assertTrue(s.load().isEmpty())
        assertNull(s.readLog("a"))
        assertFalse(File(directory, "logs/a.log").exists())
    }
}
