package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.NewProjectRequest
import com.senzaproject.createapp.domain.project.ProjectTemplate
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.senzaproject.createapp.domain.result.OperationFailure

class FileProjectRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(): Fixture = Fixture(File(tmp.root, "projects"))

    @Test
    fun `membuat proyek kosong menghasilkan folder metadata dan README`() = runTest {
        val f = fixture()
        val project = f.newProject("Aplikasi Saya")

        assertEquals("Aplikasi-Saya", project.id)
        assertEquals("Aplikasi Saya", project.name)
        assertTrue(File(f.root, "Aplikasi-Saya/README.md").isFile)
        assertTrue(f.storage.metaFile(File(f.root, "Aplikasi-Saya")).isFile)
        assertEquals("# Aplikasi Saya\n\nDibuat dengan Create App.\n", File(f.root, "Aplikasi-Saya/README.md").readText())
    }

    @Test
    fun `proyek tetap ada setelah aplikasi dimulai ulang`() = runTest {
        val first = fixture()
        val created = first.newProject("Bertahan")
        first.projects.setFavorite(created.id, true).ok()

        // Instance baru di folder yang sama = proses aplikasi baru.
        val second = fixture()
        val list = second.projects.observeProjects().first()

        assertEquals(1, list.size)
        assertEquals("Bertahan", list[0].name)
        assertEquals(created.id, list[0].id)
        assertTrue(list[0].isFavorite)
    }

    @Test
    fun `nama yang sama tanpa membedakan huruf ditolak`() = runTest {
        val f = fixture()
        f.newProject("Satu")
        val result = f.projects.createProject(NewProjectRequest("SATU", "", ProjectTemplate.EMPTY))
        assertEquals(OperationFailure.NAME_TAKEN, result.failure())
        assertEquals(1, f.projects.observeProjects().first().size)
    }

    @Test
    fun `nama berbeda dengan slug sama mendapat id unik`() = runTest {
        val f = fixture()
        val a = f.newProject("A b")
        val b = f.newProject("A-b")
        assertEquals("A-b", a.id)
        assertEquals("A-b-2", b.id)
    }

    @Test
    fun `nama tidak valid ditolak dan tidak meninggalkan folder`() = runTest {
        val f = fixture()
        for (bad in listOf("", "  ", "a/b", "a:b", ".createapp")) {
            val result = f.projects.createProject(NewProjectRequest(bad, "", ProjectTemplate.EMPTY))
            result.failure()
        }
        assertEquals(0, f.root.listFiles()?.size ?: 0)
    }

    @Test
    fun `package tidak valid untuk template Android ditolak tanpa meninggalkan folder`() = runTest {
        val f = fixture()
        val result = f.projects.createProject(NewProjectRequest("App", "app", ProjectTemplate.ANDROID_KOTLIN))
        assertEquals(OperationFailure.INVALID_PACKAGE, result.failure())
        assertEquals(0, f.root.listFiles()?.size ?: 0)
    }

    @Test
    fun `template Android membuat struktur proyek`() = runTest {
        val f = fixture()
        val project = f.newProject("Halo Android", ProjectTemplate.ANDROID_KOTLIN)
        val dir = f.dirOf(project)

        for (path in listOf(
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradle.properties",
            ".gitignore",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/res/values/strings.xml",
            "app/src/main/kotlin/com/contoh/app/MainActivity.kt",
        )) {
            assertTrue("berkas template hilang: $path", File(dir, path).isFile)
        }
        assertTrue(File(dir, "app/src/main/kotlin/com/contoh/app/MainActivity.kt").readText().startsWith("package com.contoh.app\n"))
        assertEquals("com.contoh.app", project.packageName)
    }

    @Test
    fun `ganti nama mengubah nama tampilan tetapi bukan id`() = runTest {
        val f = fixture()
        val project = f.newProject("Lama")
        val renamed = f.projects.renameProject(project.id, "  Baru  ").ok()

        assertEquals("Baru", renamed.name)
        assertEquals(project.id, renamed.id)
        assertTrue(File(f.root, project.id).isDirectory)
        assertEquals("Baru", fixture().projects.observeProjects().first()[0].name)
    }

    @Test
    fun `ganti nama ke nama proyek lain ditolak tetapi huruf besar nama sendiri boleh`() = runTest {
        val f = fixture()
        val one = f.newProject("Satu")
        f.newProject("Dua")

        assertEquals(OperationFailure.NAME_TAKEN, f.projects.renameProject(one.id, "dua").failure())
        assertEquals("SATU", f.projects.renameProject(one.id, "SATU").ok().name)
    }

    @Test
    fun `ganti nama proyek yang tidak ada gagal NOT_FOUND`() = runTest {
        val f = fixture()
        assertEquals(OperationFailure.NOT_FOUND, f.projects.renameProject("tidak-ada", "X").failure())
    }

    @Test
    fun `hapus proyek menghapus folder dan daftar`() = runTest {
        val f = fixture()
        val project = f.newProject("Hapus Saya")
        f.projects.deleteProject(project.id).ok()

        assertFalse(File(f.root, project.id).exists())
        assertTrue(f.projects.observeProjects().first().isEmpty())
        assertEquals(OperationFailure.NOT_FOUND, f.projects.deleteProject(project.id).failure())
    }

    @Test
    fun `id berbahaya ditolak`() = runTest {
        val f = fixture()
        assertEquals(OperationFailure.NOT_FOUND, f.projects.getProject("../x").failure())
        assertEquals(OperationFailure.NOT_FOUND, f.projects.deleteProject("..").failure())
    }

    @Test
    fun `metadata rusak atau hilang tidak menghilangkan proyek`() = runTest {
        val f = fixture()
        val corrupt = f.newProject("Rusak")
        val missing = f.newProject("Hilang")
        f.storage.metaFile(f.dirOf(corrupt)).writeText("name=\\uZZZZ\n")
        f.storage.metaFile(f.dirOf(missing)).delete()

        val byId = fixture().projects.observeProjects().first().associateBy { it.id }
        assertEquals(2, byId.size)
        assertEquals(corrupt.id, byId.getValue(corrupt.id).name)
        assertEquals(missing.id, byId.getValue(missing.id).name)
        assertTrue(File(f.dirOf(corrupt), "README.md").isFile)
    }

    @Test
    fun `sisa impor lama dibersihkan dan yang baru dibiarkan serta tidak tampil sebagai proyek`() = runTest {
        val f = fixture()
        f.newProject("Normal")
        val old = File(f.root, ".importing-lama").also { it.mkdirs(); File(it, "x.txt").writeText("x") }
        old.setLastModified(1_000L)
        val fresh = File(f.root, ".importing-baru").also { it.mkdirs() }

        val list = fixture().projects.observeProjects().first()

        assertEquals(listOf("Normal"), list.map { it.name })
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `ukuran proyek menghitung isi tanpa metadata`() = runTest {
        val f = fixture()
        val project = f.newProject("Ukuran")
        val readmeSize = File(f.dirOf(project), "README.md").length()
        f.files.writeText(project.id, "a.txt", "12345").ok()

        val listed = f.projects.observeProjects().first().single()
        assertEquals(readmeSize + 5, listed.sizeBytes)
    }
}
