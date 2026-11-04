package com.senzaproject.createapp.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMetricsTest {

    @Test
    fun `teks kosong berada di baris 1 kolom 1`() {
        assertEquals(CursorPosition(1, 1), cursorPositionOf("", 0))
    }

    @Test
    fun `kolom dihitung dari awal baris`() {
        assertEquals(CursorPosition(1, 3), cursorPositionOf("abc", 2))
    }

    @Test
    fun `pindah baris setelah newline`() {
        assertEquals(CursorPosition(2, 2), cursorPositionOf("a\nbc", 3))
    }

    @Test
    fun `kursor tepat setelah newline berada di kolom 1 baris berikutnya`() {
        assertEquals(CursorPosition(2, 1), cursorPositionOf("a\nbc", 2))
    }

    @Test
    fun `offset di luar rentang dipaksa masuk rentang`() {
        assertEquals(CursorPosition(1, 4), cursorPositionOf("abc", 99))
        assertEquals(CursorPosition(1, 1), cursorPositionOf("abc", -5))
    }

    @Test
    fun `jumlah baris`() {
        assertEquals(1, lineCountOf(""))
        assertEquals(1, lineCountOf("abc"))
        assertEquals(2, lineCountOf("a\n"))
        assertEquals(3, lineCountOf("a\nb\nc"))
    }
}
