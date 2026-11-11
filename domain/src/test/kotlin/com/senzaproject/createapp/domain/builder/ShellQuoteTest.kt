package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShellQuoteTest {

    @Test
    fun `teks biasa dibungkus kutip tunggal`() {
        assertEquals("'abc'", ShellQuote.quote("abc"))
    }

    @Test
    fun `kutip tunggal di dalam teks di-escape`() {
        assertEquals("'it'\\''s'", ShellQuote.quote("it's"))
    }

    @Test
    fun `spasi dan karakter khusus shell tetap harfiah`() {
        assertEquals("'a b \$(rm -rf /) `x` ; &'", ShellQuote.quote("a b \$(rm -rf /) `x` ; &"))
    }

    @Test
    fun `teks kosong menjadi dua kutip`() {
        assertEquals("''", ShellQuote.quote(""))
    }

    @Test
    fun `karakter NUL ditolak`() {
        assertThrows(IllegalArgumentException::class.java) { ShellQuote.quote("a\u0000b") }
    }
}
