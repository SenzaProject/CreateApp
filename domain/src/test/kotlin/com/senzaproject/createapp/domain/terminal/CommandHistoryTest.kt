package com.senzaproject.createapp.domain.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandHistoryTest {

    @Test
    fun `perintah dipangkas dan yang kosong diabaikan`() {
        val history = CommandHistory()
        history.add("  ls  ")
        history.add("   ")
        history.add("")
        assertEquals(listOf("ls"), history.entries)
    }

    @Test
    fun `perintah berurutan yang sama tidak digandakan tetapi yang tidak berurutan boleh`() {
        val history = CommandHistory()
        history.add("a")
        history.add("a")
        history.add("b")
        history.add("a")
        assertEquals(listOf("a", "b", "a"), history.entries)
    }

    @Test
    fun `kapasitas dibatasi dan yang terlama dibuang`() {
        val history = CommandHistory(capacity = 3)
        for (c in listOf("1", "2", "3", "4", "5")) history.add(c)
        assertEquals(listOf("3", "4", "5"), history.entries)
    }

    @Test
    fun `navigasi naik turun mengembalikan draf`() {
        val history = CommandHistory()
        history.add("a")
        history.add("b")
        history.add("c")

        assertEquals("c", history.previous("draf"))
        assertEquals("b", history.previous("diabaikan"))
        assertEquals("a", history.previous("diabaikan"))
        assertEquals("a", history.previous("diabaikan"))
        assertEquals("b", history.next())
        assertEquals("c", history.next())
        assertEquals("draf", history.next())
        assertNull(history.next())
    }

    @Test
    fun `riwayat kosong tidak bisa dinavigasi`() {
        val history = CommandHistory()
        assertNull(history.previous("x"))
        assertNull(history.next())
    }

    @Test
    fun `menambah perintah mengatur ulang navigasi`() {
        val history = CommandHistory()
        history.add("a")
        history.add("b")
        assertEquals("b", history.previous(""))
        history.add("c")
        assertNull(history.next())
        assertEquals("c", history.previous(""))
    }

    @Test
    fun `entries adalah salinan`() {
        val history = CommandHistory()
        history.add("a")
        val snapshot = history.entries
        history.add("b")
        assertEquals(listOf("a"), snapshot)
    }
}
