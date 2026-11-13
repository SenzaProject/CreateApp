package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLogTest {

    private val token = "run-123"
    private val header = LiveLog.header(token)

    @Test
    fun `header memuat token`() {
        assertEquals("# createapp-run run-123", header)
    }

    @Test
    fun `token valid dan tidak valid`() {
        assertTrue(LiveLog.isValidToken("abc-123_X"))
        assertTrue(LiveLog.isValidToken("0b9e5f3c-2c1a-4d6f-8f0e-1a2b3c4d5e6f"))
        assertFalse(LiveLog.isValidToken(""))
        assertFalse(LiveLog.isValidToken("a b"))
        assertFalse(LiveLog.isValidToken("a'b"))
        assertFalse(LiveLog.isValidToken("a;b"))
        assertFalse(LiveLog.isValidToken("a\nb"))
        assertFalse(LiveLog.isValidToken("a".repeat(65)))
        assertFalse(LiveLog.isValidToken("é"))
    }

    @Test
    fun `log lama atau tanpa header ditolak`() {
        assertNull(LiveLog.parse("# createapp-run lain\n> Task :app:x\n", token))
        assertNull(LiveLog.parse("> Task :app:x\n", token))
        assertNull(LiveLog.parse("", token))
        assertNull(LiveLog.parse(header, token)) // header tanpa baris baru: belum lengkap
    }

    @Test
    fun `hanya header berarti build sudah mulai tanpa keluaran`() {
        val view = LiveLog.parse("$header\n", token)
        assertNotNull(view)
        assertTrue(view!!.lines.isEmpty())
        assertNull(view.currentTask)
        assertEquals(0, view.tasksSeen)
    }

    @Test
    fun `task terakhir dan jumlah task terbaca`() {
        val text = "$header\n> Configure project :\n> Task :app:preBuild UP-TO-DATE\n> Task :app:compileDebugKotlin\n"
        val view = LiveLog.parse(text, token)!!
        assertEquals(":app:compileDebugKotlin", view.currentTask)
        assertEquals(2, view.tasksSeen)
        assertEquals(3, view.lines.size)
        assertEquals("> Configure project :", view.lines.first())
    }

    @Test
    fun `baris terakhir yang belum selesai ditulis dibuang`() {
        val text = "$header\n> Task :app:a\n> Task :app:compileDeb"
        val view = LiveLog.parse(text, token)!!
        assertEquals(":app:a", view.currentTask)
        assertEquals(1, view.tasksSeen)
        assertEquals(listOf("> Task :app:a"), view.lines)
    }

    @Test
    fun `akhir baris CRLF dibersihkan`() {
        val view = LiveLog.parse("$header\r\n> Task :app:a\r\nhalo\r\n", token)!!
        assertEquals(listOf("> Task :app:a", "halo"), view.lines)
    }

    @Test
    fun `baris dibatasi dan jumlah yang dibuang dilaporkan sementara task tetap dihitung dari semua`() {
        val body = (1..10).joinToString("") { "> Task :t$it\n" }
        val view = LiveLog.parse("$header\n$body", token, maxLines = 4)!!
        assertEquals(4, view.lines.size)
        assertEquals("> Task :t10", view.lines.last())
        assertEquals(6, view.droppedLines)
        assertEquals(10, view.tasksSeen)
        assertEquals(":t10", view.currentTask)
    }

    @Test
    fun `error Gradle tidak membuat penguraian gagal`() {
        val text = "$header\ne: file:///a/Main.kt:3:5 Unresolved reference: x\n\nFAILURE: Build failed\n"
        val view = LiveLog.parse(text, token)!!
        assertEquals(3, view.lines.size)
    }

    @Test
    fun `perintah build membawa token bila diberikan dan tidak bila tidak`() {
        val factory = BuildCommandFactory("QUJD")
        val plain = factory.build("/s/src/p1", "p1", BuildType.DEBUG, "/s/out")
        assertTrue(plain.endsWith("--out '/s/out'"))
        assertFalse(plain.contains("--run"))
        val withToken = factory.build("/s/src/p1", "p1", BuildType.DEBUG, "/s/out", runToken = token)
        assertTrue(withToken.endsWith("--out '/s/out' --run 'run-123'"))
    }

    @Test
    fun `token tidak valid ditolak saat membuat perintah`() {
        val factory = BuildCommandFactory("QUJD")
        assertThrows(IllegalArgumentException::class.java) {
            factory.build("/s/src/p1", "p1", BuildType.DEBUG, "/s/out", runToken = "x'; rm -rf /")
        }
    }
}

class LiveLogReadTest {

    private val header = "# createapp-run tok"

    private fun read(text: String, maxBytes: Int, shortSkips: Boolean = false): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val base = java.io.ByteArrayInputStream(bytes)
        val stream = if (shortSkips) {
            object : java.io.FilterInputStream(base) {
                override fun skip(n: Long): Long = 0L // meniru stream yang menolak skip
            }
        } else {
            base
        }
        return LiveLog.readHeadAndTail(stream, bytes.size.toLong(), maxBytes)
    }

    @Test
    fun `file kecil dibaca utuh`() {
        val text = "$header\nbaris1\nbaris2\n"
        assertEquals(text, read(text, 1024))
    }

    @Test
    fun `file besar menyisakan header dan ekor pada batas baris`() {
        val body = (1..2000).joinToString("") { "baris-nomor-$it\n" }
        val text = "$header\n$body"
        val result = read(text, 1024)
        assertTrue(result.startsWith("$header\n"))
        assertTrue(result.endsWith("baris-nomor-2000\n"))
        assertTrue(result.length < 1024 + header.length + 40)
        // Tidak ada baris terpotong: tiap baris setelah header berbentuk utuh.
        val lines = result.removeSuffix("\n").split('\n').drop(1)
        assertTrue(lines.all { it.matches(Regex("baris-nomor-\\d+")) })
    }

    @Test
    fun `hasil file besar tetap lolos penguraian dengan token yang sama`() {
        val body = (1..3000).joinToString("") { "> Task :t$it\n" }
        val view = LiveLog.parse(read("$header\n$body", 2048), "tok")
        assertNotNull(view)
        assertEquals(":t3000", view!!.currentTask)
    }

    @Test
    fun `stream yang menolak skip tetap terbaca benar`() {
        val body = (1..500).joinToString("") { "baris-$it\n" }
        val result = read("$header\n$body", 1024, shortSkips = true)
        assertTrue(result.startsWith("$header\n"))
        assertTrue(result.endsWith("baris-500\n"))
    }

    @Test
    fun `file besar tanpa satu pun baris baru di ekor tidak melempar`() {
        val text = "$header\n" + "x".repeat(5000)
        val result = read(text, 1024)
        assertEquals("$header\n", result)
    }
}
