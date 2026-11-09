package com.senzaproject.createapp.domain.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellWordsTest {

    @Test
    fun `spasi memisahkan kata dan spasi ganda diabaikan`() {
        assertEquals(listOf("a", "b", "c"), ShellWords.split("a b   c"))
        assertEquals(listOf("a", "b"), ShellWords.split("  a\tb  "))
    }

    @Test
    fun `teks kosong menghasilkan daftar kosong`() {
        assertEquals(emptyList<String>(), ShellWords.split(""))
        assertEquals(emptyList<String>(), ShellWords.split("   "))
    }

    @Test
    fun `kutip tunggal bersifat harfiah`() {
        assertEquals(listOf("a b", "c"), ShellWords.split("'a b' c"))
        assertEquals(listOf("""a\b"""), ShellWords.split("""'a\b'"""))
    }

    @Test
    fun `kutip ganda mengizinkan escape kutip dan backslash`() {
        assertEquals(listOf("a b"), ShellWords.split("\"a b\""))
        assertEquals(listOf("say \"hi\""), ShellWords.split("\"say \\\"hi\\\"\""))
        assertEquals(listOf("a\\b"), ShellWords.split("\"a\\\\b\""))
    }

    @Test
    fun `backslash di luar kutip meloloskan karakter berikutnya`() {
        assertEquals(listOf("a b"), ShellWords.split("a\\ b"))
    }

    @Test
    fun `kutip kosong menghasilkan kata kosong`() {
        assertEquals(listOf(""), ShellWords.split("\"\""))
        assertEquals(listOf("", "x"), ShellWords.split("'' x"))
    }

    @Test
    fun `kutip yang berdampingan digabung menjadi satu kata`() {
        assertEquals(listOf("ab cd"), ShellWords.split("a\"b c\"d"))
    }

    @Test
    fun `kutip tidak tertutup menghasilkan null`() {
        assertNull(ShellWords.split("'abc"))
        assertNull(ShellWords.split("\"abc"))
        assertNull(ShellWords.split("a 'b c"))
    }
}
