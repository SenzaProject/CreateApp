package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TreeCopierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dir(name: String): File = File(tmp.root, name).also { it.mkdirs() }

    private fun write(root: File, path: String, content: String) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun assertFailsWith(reason: OperationFailure, block: () -> Unit) {
        try {
            block()
            fail("seharusnya gagal dengan $reason")
        } catch (e: OperationException) {
            assertEquals(reason, e.failure)
        }
    }

    @Test
    fun `menyalin isi dan melewati folder keluaran build dan metadata`() {
        val src = dir("src")
        write(src, "a.txt", "aaa")
        write(src, "sub/b.txt", "bb")
        write(src, "sub/dalam/c.txt", "c")
        write(src, "build/out.bin", "junk")
        write(src, ".gradle/cache", "junk")
        write(src, ".createapp/project.properties", "junk")
        val dst = dir("dst")

        val stats = TreeCopier.copyChildren(
            FileTreeSource(src),
            FileTreeSink(dst),
            CopyLimits(skipDirectoryNames = IMPORT_SKIPPED_DIRECTORIES),
        )

        assertEquals(3, stats.files)
        assertEquals(6L, stats.bytes)
        assertEquals("aaa", File(dst, "a.txt").readText())
        assertEquals("bb", File(dst, "sub/b.txt").readText())
        assertEquals("c", File(dst, "sub/dalam/c.txt").readText())
        assertFalse(File(dst, "build").exists())
        assertFalse(File(dst, ".gradle").exists())
        assertFalse(File(dst, ".createapp").exists())
    }

    @Test
    fun `folder kosong ikut disalin`() {
        val src = dir("src")
        File(src, "kosong").mkdirs()
        val dst = dir("dst")

        TreeCopier.copyChildren(FileTreeSource(src), FileTreeSink(dst))

        assertTrue(File(dst, "kosong").isDirectory)
    }

    @Test
    fun `melebihi batas jumlah file ditolak`() {
        val src = dir("src")
        write(src, "a.txt", "a")
        write(src, "b.txt", "b")
        val dst = dir("dst")

        assertFailsWith(OperationFailure.TOO_LARGE) {
            TreeCopier.copyChildren(FileTreeSource(src), FileTreeSink(dst), CopyLimits(maxFiles = 1))
        }
    }

    @Test
    fun `melebihi batas ukuran total ditolak`() {
        val src = dir("src")
        write(src, "a.txt", "1234567890")
        val dst = dir("dst")

        assertFailsWith(OperationFailure.TOO_LARGE) {
            TreeCopier.copyChildren(FileTreeSource(src), FileTreeSink(dst), CopyLimits(maxBytes = 5))
        }
    }

    @Test
    fun `melebihi batas kedalaman ditolak`() {
        val src = dir("src")
        write(src, "a/b/c/d.txt", "x")
        val dst = dir("dst")

        assertFailsWith(OperationFailure.TOO_LARGE) {
            TreeCopier.copyChildren(FileTreeSource(src), FileTreeSink(dst), CopyLimits(maxDepth = 1))
        }
    }

    @Test
    fun `pembatalan menghentikan penyalinan dan dilempar apa adanya`() {
        val src = dir("src")
        write(src, "a.txt", "a")
        write(src, "b.txt", "b")
        val dst = dir("dst")
        var calls = 0

        try {
            TreeCopier.copyChildren(FileTreeSource(src), FileTreeSink(dst), CopyLimits()) {
                calls++
                if (calls >= 2) throw CancellationException("dibatalkan")
            }
            fail("seharusnya dibatalkan")
        } catch (e: CancellationException) {
            assertEquals("dibatalkan", e.message)
        }
        assertFalse(File(dst, "b.txt").exists())
    }

    private class FakeEntry(
        override val name: String,
        override val isDirectory: Boolean = false,
        private val kids: List<TreeSource> = emptyList(),
    ) : TreeSource {
        override fun children(): List<TreeSource> = kids
        override fun openInput(): InputStream = ByteArrayInputStream("x".toByteArray())
    }

    @Test
    fun `nama entri berbahaya dari penyedia dokumen ditolak`() {
        for (bad in listOf("..", ".", "", "a/b", "a\\b", "..\\x", "a\u0000b")) {
            val dst = dir("dst-" + bad.hashCode())
            val root = FakeEntry("root", isDirectory = true, kids = listOf(FakeEntry(bad)))

            assertFailsWith(OperationFailure.INVALID_SOURCE) {
                TreeCopier.copyChildren(root, FileTreeSink(dst))
            }
            assertEquals("tidak boleh ada berkas tertulis untuk nama: $bad", 0, dst.listFiles()?.size ?: 0)
        }
    }
}
