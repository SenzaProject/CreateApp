package com.senzaproject.createapp.core.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectNamesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `sanitize mengganti karakter terlarang dan memangkas`() {
        assertEquals("a_b_c", sanitizeProjectName("a/b:c", "F"))
        assertEquals("abc", sanitizeProjectName("  abc...  ", "F"))
        assertEquals("x".repeat(56), sanitizeProjectName("x".repeat(100), "F"))
    }

    @Test
    fun `sanitize memakai fallback bila hasil kosong atau dicadangkan`() {
        assertEquals("F", sanitizeProjectName(null, "F"))
        assertEquals("F", sanitizeProjectName("   ", "F"))
        assertEquals("F", sanitizeProjectName(".createapp", "F"))
        assertEquals("F", sanitizeProjectName("...", "F"))
    }

    private fun gradle(path: String, text: String): File {
        val file = File(tmp.root, path)
        file.parentFile?.mkdirs()
        file.writeText(text)
        return tmp.root
    }

    @Test
    fun `mendeteksi namespace dari build gradle kts`() {
        gradle("app/build.gradle.kts", "android {\n    namespace = \"com.contoh.app\"\n}\n")
        assertEquals("com.contoh.app", detectPackageName(tmp.root))
    }

    @Test
    fun `mendeteksi applicationId dari build gradle groovy`() {
        gradle("app/build.gradle", "defaultConfig {\n    applicationId 'id.contoh.demo'\n}\n")
        assertEquals("id.contoh.demo", detectPackageName(tmp.root))
    }

    @Test
    fun `package tidak valid atau tidak ada menghasilkan string kosong`() {
        assertEquals("", detectPackageName(tmp.root))
        gradle("app/build.gradle.kts", "namespace = \"App\"\n")
        assertEquals("", detectPackageName(tmp.root))
    }
}
