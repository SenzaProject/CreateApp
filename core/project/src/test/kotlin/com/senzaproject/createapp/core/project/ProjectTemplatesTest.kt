package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.ProjectTemplate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectTemplatesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `escape XML untuk resource string`() {
        assertEquals("Tom &amp; Jerry\\'s", ProjectTemplates.xmlStringEscape("Tom & Jerry's"))
        assertEquals("&lt;b&gt;", ProjectTemplates.xmlStringEscape("<b>"))
        assertEquals("\\@abc", ProjectTemplates.xmlStringEscape("@abc"))
        assertEquals("\\?abc", ProjectTemplates.xmlStringEscape("?abc"))
        assertEquals("a@b", ProjectTemplates.xmlStringEscape("a@b"))
        assertEquals("biasa", ProjectTemplates.xmlStringEscape("biasa"))
    }

    @Test
    fun `escape dolar untuk string Gradle`() {
        assertEquals("A\\\$b", ProjectTemplates.kotlinStringEscape("A\$b"))
        assertEquals("biasa", ProjectTemplates.kotlinStringEscape("biasa"))
    }

    @Test
    fun `template kosong hanya membuat README`() {
        val dir = tmp.newFolder("kosong")
        ProjectTemplates.generate(dir, "Nama", "", ProjectTemplate.EMPTY)

        assertEquals(listOf("README.md"), dir.list()!!.toList())
        assertEquals("# Nama\n\nDibuat dengan Create App.\n", File(dir, "README.md").readText())
    }

    @Test
    fun `template Android memuat nama package dan resource yang benar`() {
        val dir = tmp.newFolder("android")
        ProjectTemplates.generate(dir, "Tom & Jerry's", "com.contoh.app", ProjectTemplate.ANDROID_KOTLIN)

        val gradle = File(dir, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("namespace = \"com.contoh.app\""))
        assertTrue(gradle.contains("applicationId = \"com.contoh.app\""))
        assertTrue(File(dir, "settings.gradle.kts").readText().contains("rootProject.name = \"Tom & Jerry's\""))
        assertTrue(File(dir, "settings.gradle.kts").readText().contains("include(\":app\")"))
        assertTrue(File(dir, "app/src/main/res/values/strings.xml").readText().contains("<string name=\"app_name\">Tom &amp; Jerry\\'s</string>"))
        assertTrue(File(dir, "app/src/main/AndroidManifest.xml").readText().contains("android:name=\".MainActivity\""))
        assertEquals(
            "package com.contoh.app",
            File(dir, "app/src/main/kotlin/com/contoh/app/MainActivity.kt").readLines().first(),
        )
    }

    @Test
    fun `nama proyek dengan dolar tidak menjadi template di settings`() {
        val dir = tmp.newFolder("dolar")
        ProjectTemplates.generate(dir, "A\$b", "com.contoh.app", ProjectTemplate.ANDROID_KOTLIN)

        assertTrue(File(dir, "settings.gradle.kts").readText().contains("rootProject.name = \"A\\\$b\""))
    }
}
