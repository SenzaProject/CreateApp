package com.senzaproject.createapp.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNodeTest {

    @Test
    fun `fileNameFor menambahkan ekstensi bila belum ada`() {
        assertEquals("Main.kt", fileNameFor("Main", NewFileType.KOTLIN))
        assertEquals("Main.kt", fileNameFor("Main.kt", NewFileType.KOTLIN))
        assertEquals("Main.KT", fileNameFor("Main.KT", NewFileType.KOTLIN))
        assertEquals("layout.xml", fileNameFor("layout", NewFileType.XML))
        assertEquals("data.json", fileNameFor("data", NewFileType.JSON))
        assertEquals("build.gradle.kts", fileNameFor("build.gradle.kts", NewFileType.GRADLE))
        assertEquals("app.gradle.kts", fileNameFor("app", NewFileType.GRADLE))
    }

    @Test
    fun `package diturunkan dari folder sumber standar`() {
        assertEquals("com.contoh.app", packageFromSourceDir("app/src/main/kotlin/com/contoh/app"))
        assertEquals("com.contoh", packageFromSourceDir("app/src/main/java/com/contoh"))
        assertEquals("com.contoh", packageFromSourceDir("src/main/kotlin/com/contoh"))
    }

    @Test
    fun `package null di luar folder sumber atau akar sumber`() {
        assertNull(packageFromSourceDir(""))
        assertNull(packageFromSourceDir("app/src/main/kotlin"))
        assertNull(packageFromSourceDir("docs/kotlin/com/contoh"))
        assertNull(packageFromSourceDir("app/src/main/kotlin/com/class"))
    }

    @Test
    fun `isi awal file kotlin memuat package dan kelas`() {
        assertEquals(
            "package com.contoh\n\nclass Halo\n",
            initialContentFor("Halo.kt", "app/src/main/kotlin/com/contoh"),
        )
    }

    @Test
    fun `isi awal file kotlin tanpa package dan dengan nama bukan identifier`() {
        assertEquals("class Halo\n", initialContentFor("Halo.kt", ""))
        assertEquals("", initialContentFor("my-file.kt", ""))
    }

    @Test
    fun `isi awal xml json gradle dan lainnya`() {
        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n", initialContentFor("a.xml", ""))
        assertEquals("{\n}\n", initialContentFor("a.json", ""))
        assertEquals("// app.gradle.kts\n", initialContentFor("app.gradle.kts", ""))
        assertEquals("", initialContentFor("catatan.txt", ""))
    }
}
