package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.MAX_TEXT_FILE_BYTES
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileProjectFilesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(): Fixture = Fixture(File(tmp.root, "projects"))

    @Test
    fun `daftar folder lebih dulu urut nama dan metadata disembunyikan`() = runTest {
        val f = fixture()
        val p = f.newProject()
        val dir = f.dirOf(p)
        File(dir, "zeta").mkdirs()
        File(dir, "Alpha").mkdirs()
        File(dir, "b.txt").writeText("b")
        File(dir, "A.txt").writeText("a")

        val names = f.files.list(p.id, "").ok().map { it.name }

        assertEquals(listOf("Alpha", "zeta", "A.txt", "b.txt", "README.md"), names)
        assertFalse(".createapp" in names)
    }

    @Test
    fun `daftar mengembalikan path relatif dan ukuran`() = runTest {
        val f = fixture()
        val p = f.newProject()
        File(f.dirOf(p), "src").mkdirs()
        File(f.dirOf(p), "src/Main.kt").writeText("abc")

        val node = f.files.list(p.id, "src").ok().single()

        assertEquals("src/Main.kt", node.path)
        assertEquals(3L, node.sizeBytes)
        assertFalse(node.isDirectory)
    }

    @Test
    fun `daftar folder yang tidak ada gagal NOT_FOUND`() = runTest {
        val f = fixture()
        val p = f.newProject()
        assertEquals(OperationFailure.NOT_FOUND, f.files.list(p.id, "tidak-ada").failure())
    }

    @Test
    fun `membuat file kotlin memuat package dari folder sumber`() = runTest {
        val f = fixture()
        val p = f.newProject()
        File(f.dirOf(p), "app/src/main/kotlin/com/contoh").mkdirs()

        val node = f.files.createFile(p.id, "app/src/main/kotlin/com/contoh", "Halo.kt").ok()

        assertEquals("app/src/main/kotlin/com/contoh/Halo.kt", node.path)
        assertEquals("package com.contoh\n\nclass Halo\n", f.files.readText(p.id, node.path).ok())
    }

    @Test
    fun `membuat file xml json dan gradle memakai isi awal`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.files.createFile(p.id, "", "a.xml").ok()
        f.files.createFile(p.id, "", "a.json").ok()
        f.files.createFile(p.id, "", "a.gradle.kts").ok()

        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n", f.files.readText(p.id, "a.xml").ok())
        assertEquals("{\n}\n", f.files.readText(p.id, "a.json").ok())
        assertEquals("// a.gradle.kts\n", f.files.readText(p.id, "a.gradle.kts").ok())
    }

    @Test
    fun `membuat file dengan nama sama gagal ALREADY_EXISTS dan tidak menimpa`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.files.writeText(p.id, "a.txt", "asli").ok()

        assertEquals(OperationFailure.ALREADY_EXISTS, f.files.createFile(p.id, "", "a.txt").failure())
        assertEquals("asli", f.files.readText(p.id, "a.txt").ok())
    }

    @Test
    fun `nama file tidak valid atau dicadangkan ditolak`() = runTest {
        val f = fixture()
        val p = f.newProject()
        assertEquals(OperationFailure.INVALID_NAME, f.files.createFile(p.id, "", "a/b.kt").failure())
        assertEquals(OperationFailure.INVALID_NAME, f.files.createFile(p.id, "", "").failure())
        assertEquals(OperationFailure.RESERVED_NAME, f.files.createFile(p.id, "", ".createapp").failure())
        assertEquals(OperationFailure.RESERVED_NAME, f.files.createFolder(p.id, "", ".createapp").failure())
    }

    @Test
    fun `membuat folder dan duplikatnya`() = runTest {
        val f = fixture()
        val p = f.newProject()
        val node = f.files.createFolder(p.id, "", "src").ok()

        assertTrue(node.isDirectory)
        assertTrue(File(f.dirOf(p), "src").isDirectory)
        assertEquals(OperationFailure.ALREADY_EXISTS, f.files.createFolder(p.id, "", "src").failure())
        assertEquals(OperationFailure.NOT_FOUND, f.files.createFolder(p.id, "tidak-ada", "x").failure())
    }

    @Test
    fun `ganti nama file dan folder`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.files.createFolder(p.id, "", "lama").ok()
        f.files.writeText(p.id, "lama/a.txt", "isi").ok()

        val folder = f.files.rename(p.id, "lama", "baru").ok()
        assertEquals("baru", folder.path)
        assertEquals("isi", f.files.readText(p.id, "baru/a.txt").ok())

        val file = f.files.rename(p.id, "baru/a.txt", "b.txt").ok()
        assertEquals("baru/b.txt", file.path)
        assertFalse(File(f.dirOf(p), "baru/a.txt").exists())
    }

    @Test
    fun `ganti nama ke nama yang sudah ada ditolak tanpa menimpa`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.files.writeText(p.id, "a.txt", "A").ok()
        f.files.writeText(p.id, "b.txt", "B").ok()

        assertEquals(OperationFailure.ALREADY_EXISTS, f.files.rename(p.id, "a.txt", "b.txt").failure())
        assertEquals("A", f.files.readText(p.id, "a.txt").ok())
        assertEquals("B", f.files.readText(p.id, "b.txt").ok())
    }

    @Test
    fun `ganti nama kasus tidak valid`() = runTest {
        val f = fixture()
        val p = f.newProject()
        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.rename(p.id, "", "x").failure())
        assertEquals(OperationFailure.NOT_FOUND, f.files.rename(p.id, "tidak-ada.txt", "x").failure())
        f.files.writeText(p.id, "a.txt", "A").ok()
        assertEquals(OperationFailure.INVALID_NAME, f.files.rename(p.id, "a.txt", "../x").failure())
    }

    @Test
    fun `hapus file dan folder rekursif`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.files.createFolder(p.id, "", "d").ok()
        f.files.writeText(p.id, "d/a.txt", "x").ok()
        f.files.writeText(p.id, "b.txt", "y").ok()

        f.files.delete(p.id, "d").ok()
        f.files.delete(p.id, "b.txt").ok()

        assertFalse(File(f.dirOf(p), "d").exists())
        assertFalse(File(f.dirOf(p), "b.txt").exists())
        assertEquals(OperationFailure.NOT_FOUND, f.files.delete(p.id, "b.txt").failure())
    }

    @Test
    fun `akar proyek dan metadata tidak dapat dihapus lewat explorer`() = runTest {
        val f = fixture()
        val p = f.newProject()
        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.delete(p.id, "").failure())
        assertEquals(OperationFailure.RESERVED_NAME, f.files.delete(p.id, ".createapp").failure())
        assertTrue(File(f.dirOf(p), "README.md").isFile)
        assertTrue(f.storage.metaFile(f.dirOf(p)).isFile)
    }

    @Test
    fun `tulis lalu baca UTF-8 utuh`() = runTest {
        val f = fixture()
        val p = f.newProject()
        val text = "Halo dunia ✓ 你好 é\r\nbaris dua\n"
        f.files.writeText(p.id, "u.txt", text).ok()

        assertEquals(text, f.files.readText(p.id, "u.txt").ok())
        assertEquals(text, File(f.dirOf(p), "u.txt").readText(Charsets.UTF_8))
    }

    @Test
    fun `menulis ke folder yang tidak ada gagal dan folder induk yang ada berhasil`() = runTest {
        val f = fixture()
        val p = f.newProject()
        assertEquals(OperationFailure.NOT_FOUND, f.files.writeText(p.id, "tidak-ada/a.txt", "x").failure())
        f.files.writeText(p.id, "baru.txt", "x").ok()
        assertEquals("x", f.files.readText(p.id, "baru.txt").ok())
    }

    @Test
    fun `menulis ke path yang berupa folder ditolak`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.files.createFolder(p.id, "", "d").ok()
        assertEquals(OperationFailure.ALREADY_EXISTS, f.files.writeText(p.id, "d", "x").failure())
    }

    @Test
    fun `file biner dan UTF-8 tidak valid tidak dibuka sebagai teks`() = runTest {
        val f = fixture()
        val p = f.newProject()
        File(f.dirOf(p), "bin.dat").writeBytes(byteArrayOf(0x41, 0x00, 0x42))
        File(f.dirOf(p), "bad.txt").writeBytes(byteArrayOf(0xC3.toByte(), 0x28))

        assertEquals(OperationFailure.NOT_TEXT, f.files.readText(p.id, "bin.dat").failure())
        assertEquals(OperationFailure.NOT_TEXT, f.files.readText(p.id, "bad.txt").failure())
    }

    @Test
    fun `batas ukuran file teks`() = runTest {
        val f = fixture()
        val p = f.newProject()
        val atLimit = "a".repeat(MAX_TEXT_FILE_BYTES.toInt())
        f.files.writeText(p.id, "pas.txt", atLimit).ok()
        assertEquals(MAX_TEXT_FILE_BYTES, File(f.dirOf(p), "pas.txt").length())
        assertEquals(atLimit.length, f.files.readText(p.id, "pas.txt").ok().length)

        assertEquals(OperationFailure.TOO_LARGE, f.files.writeText(p.id, "besar.txt", atLimit + "a").failure())
        assertFalse(File(f.dirOf(p), "besar.txt").exists())

        File(f.dirOf(p), "besar2.txt").writeBytes(ByteArray(MAX_TEXT_FILE_BYTES.toInt() + 1) { 'a'.code.toByte() })
        assertEquals(OperationFailure.TOO_LARGE, f.files.readText(p.id, "besar2.txt").failure())
    }

    @Test
    fun `path traversal dan metadata ditolak untuk semua operasi`() = runTest {
        val f = fixture()
        val p = f.newProject()
        File(tmp.root, "rahasia.txt").writeText("rahasia")

        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.readText(p.id, "../../rahasia.txt").failure())
        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.list(p.id, "..").failure())
        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.writeText(p.id, "../x.txt", "x").failure())
        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.createFile(p.id, "../", "x.txt").failure())
        assertEquals(OperationFailure.RESERVED_NAME, f.files.readText(p.id, ".createapp/project.properties").failure())
        assertEquals(OperationFailure.RESERVED_NAME, f.files.writeText(p.id, ".createapp/project.properties", "x").failure())
        assertEquals("rahasia", File(tmp.root, "rahasia.txt").readText())
    }

    @Test
    fun `tautan simbolik ke luar proyek tidak diikuti`() = runTest {
        val f = fixture()
        val p = f.newProject()
        val outside = File(tmp.root, "luar").also { it.mkdirs() }
        File(outside, "rahasia.txt").writeText("rahasia")
        try {
            Files.createSymbolicLink(File(f.dirOf(p), "tautan").toPath(), outside.toPath())
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        } catch (e: java.io.IOException) {
            assumeNoException(e)
        }

        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.readText(p.id, "tautan/rahasia.txt").failure())
        assertEquals(OperationFailure.OUTSIDE_PROJECT, f.files.delete(p.id, "tautan").failure())
        assertTrue(File(outside, "rahasia.txt").isFile)
    }

    @Test
    fun `menghapus tautan simbolik di dalam proyek tidak menghapus isi yang ditunjuk`() = runTest {
        val f = fixture()
        val p = f.newProject()
        File(f.dirOf(p), "asli").mkdirs()
        File(f.dirOf(p), "asli/a.txt").writeText("a")
        try {
            Files.createSymbolicLink(File(f.dirOf(p), "alias").toPath(), File(f.dirOf(p), "asli").toPath())
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        } catch (e: java.io.IOException) {
            assumeNoException(e)
        }

        f.files.delete(p.id, "alias").ok()

        assertTrue(File(f.dirOf(p), "asli/a.txt").isFile)
        assertFalse(Files.exists(File(f.dirOf(p), "alias").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun `sisa berkas sementara dari crash tidak merusak file asli`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.files.writeText(p.id, "a.txt", "lama").ok()
        // Meniru proses yang mati saat menulis: berkas sementara setengah jadi tertinggal.
        File(f.dirOf(p), "a.txt.tmp").writeText("baru-setengah")

        assertEquals("lama", f.files.readText(p.id, "a.txt").ok())

        f.files.writeText(p.id, "a.txt", "baru").ok()
        assertEquals("baru", f.files.readText(p.id, "a.txt").ok())
        assertFalse(File(f.dirOf(p), "a.txt.tmp").exists())
    }

    @Test
    fun `perubahan file memperbarui waktu ubah proyek`() = runTest {
        val f = fixture()
        val p = f.newProject()
        f.storage.metaFile(f.dirOf(p)).setLastModified(1_000L)
        assertEquals(1_000L, f.projects.observeProjects().first().single().lastModifiedMillis)

        f.files.writeText(p.id, "a.txt", "x").ok()

        assertTrue(f.projects.observeProjects().first().single().lastModifiedMillis > 1_000L)
    }

    @Test
    fun `data bertahan setelah aplikasi dimulai ulang`() = runTest {
        val first = fixture()
        val p = first.newProject()
        first.files.createFolder(p.id, "", "src").ok()
        first.files.writeText(p.id, "src/Main.kt", "fun main() {}\n").ok()

        val second = fixture()
        assertEquals(listOf("src"), second.files.list(p.id, "").ok().filter { it.isDirectory }.map { it.name })
        assertEquals("fun main() {}\n", second.files.readText(p.id, "src/Main.kt").ok())
    }
}
