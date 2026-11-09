package com.senzaproject.createapp.domain.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineAssemblerTest {

    @Test
    fun `baris lengkap langsung dikeluarkan`() {
        assertEquals(listOf("a", "b"), LineAssembler().append("a\nb\n"))
    }

    @Test
    fun `baris yang terpotong antar potongan dirakit`() {
        val assembler = LineAssembler()
        assertEquals(emptyList<String>(), assembler.append("he"))
        assertEquals(listOf("hello"), assembler.append("llo\nwor"))
        assertEquals(listOf("world"), assembler.append("ld\n"))
    }

    @Test
    fun `baris kosong dipertahankan`() {
        assertEquals(listOf("a", "", "b"), LineAssembler().append("a\n\nb\n"))
    }

    @Test
    fun `CRLF dianggap satu pemisah termasuk saat terpotong`() {
        assertEquals(listOf("a", "b"), LineAssembler().append("a\r\nb\r\n"))
        val assembler = LineAssembler()
        assertEquals(emptyList<String>(), assembler.append("a\r"))
        assertEquals(listOf("a", "b"), assembler.append("\nb\n"))
    }

    @Test
    fun `carriage return menimpa teks sebelumnya seperti bilah progres`() {
        assertEquals(listOf("30%"), LineAssembler().append("10%\r20%\r30%\n"))
    }

    @Test
    fun `sisa tanpa baris baru keluar lewat flush sekali saja`() {
        val assembler = LineAssembler()
        assertEquals(emptyList<String>(), assembler.append("abc"))
        assertEquals("abc", assembler.flush())
        assertNull(assembler.flush())
    }

    @Test
    fun `flush mengembalikan null bila sisa tidak terlihat`() {
        val assembler = LineAssembler()
        assembler.append("\r")
        assertNull(assembler.flush())
    }

    @Test
    fun `potongan kosong tidak menghasilkan apa pun`() {
        assertEquals(emptyList<String>(), LineAssembler().append(""))
    }

    @Test
    fun `urutan escape ANSI dibuang`() {
        assertEquals(listOf("red"), LineAssembler().append("\u001B[31mred\u001B[0m\n"))
        assertEquals(listOf("x"), LineAssembler().append("\u001B]0;judul\u0007x\n"))
        assertEquals(listOf("kursor"), LineAssembler().append("\u001B[2K\u001B[1Gkursor\n"))
    }

    @Test
    fun `karakter kontrol dibuang dan tab menjadi spasi`() {
        assertEquals(listOf("abc"), LineAssembler().append("a\u0000b\u0007c\n"))
        assertEquals(listOf("a    b"), LineAssembler().append("a\tb\n"))
    }

    @Test
    fun `teks Unicode tidak rusak`() {
        assertEquals(listOf("héllo — 日本語"), LineAssembler().append("héllo — 日本語\n"))
    }

    @Test
    fun `baris terlalu panjang dipotong dengan elipsis`() {
        val lines = LineAssembler(maxLineLength = 10).append("x".repeat(25) + "\n")
        assertEquals(listOf("x".repeat(10) + "…"), lines)
    }

    @Test
    fun `output tanpa baris baru dipecah paksa agar memori terbatas`() {
        val lines = LineAssembler(maxLineLength = 5).append("a".repeat(20))
        assertEquals(listOf("aaaaa"), lines)
    }
}
