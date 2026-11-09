package com.senzaproject.createapp.domain.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {

    @Test
    fun `perintah bawaan dikenali`() {
        assertEquals(ParsedCommand.Clear, parseCommand("clear"))
        assertEquals(ParsedCommand.Clear, parseCommand("  cls  "))
        assertEquals(ParsedCommand.Help, parseCommand("help"))
        assertEquals(ParsedCommand.History, parseCommand("history"))
    }

    @Test
    fun `cd tanpa argumen pindah ke direktori awal`() {
        assertEquals(ParsedCommand.ChangeDirectory(null), parseCommand("cd"))
        assertEquals(ParsedCommand.ChangeDirectory(null), parseCommand("  cd   "))
    }

    @Test
    fun `cd dengan satu argumen`() {
        assertEquals(ParsedCommand.ChangeDirectory("src"), parseCommand("cd src"))
        assertEquals(ParsedCommand.ChangeDirectory("my dir"), parseCommand("cd \"my dir\""))
        assertEquals(ParsedCommand.ChangeDirectory("my dir"), parseCommand("cd my\\ dir"))
        assertEquals(ParsedCommand.ChangeDirectory("~/x"), parseCommand("cd\t~/x"))
    }

    @Test
    fun `operator di dalam kutip bukan operator shell`() {
        assertEquals(ParsedCommand.ChangeDirectory("a;b"), parseCommand("cd \"a;b\""))
        assertEquals(ParsedCommand.ChangeDirectory("a&b"), parseCommand("cd 'a&b'"))
    }

    @Test
    fun `cd dengan argumen tidak valid`() {
        assertEquals(ParsedCommand.InvalidCd, parseCommand("cd my dir"))
        assertEquals(ParsedCommand.InvalidCd, parseCommand("cd 'abc"))
    }

    @Test
    fun `cd dengan operator shell diserahkan ke shell`() {
        assertEquals(ParsedCommand.External("cd src && ls"), parseCommand("cd src && ls"))
        assertEquals(ParsedCommand.External("cd /x; pwd"), parseCommand("cd /x; pwd"))
        assertEquals(ParsedCommand.External("cd x | cat"), parseCommand("cd x | cat"))
    }

    @Test
    fun `variabel tidak diekspansi oleh cd bawaan`() {
        assertEquals(ParsedCommand.ChangeDirectory("\$HOME"), parseCommand("cd \$HOME"))
    }

    @Test
    fun `nama yang hanya mirip cd bukan cd`() {
        assertEquals(ParsedCommand.External("cd.."), parseCommand("cd.."))
        assertEquals(ParsedCommand.External("cdx"), parseCommand("cdx"))
        assertEquals(ParsedCommand.External("cdrecord x"), parseCommand("cdrecord x"))
    }

    @Test
    fun `perintah lain dijalankan di shell dan dipangkas`() {
        assertEquals(ParsedCommand.External("ls -la"), parseCommand("  ls -la  "))
        assertEquals(ParsedCommand.External("history | grep x"), parseCommand("history | grep x"))
        assertEquals(ParsedCommand.External("clear screen"), parseCommand("clear screen"))
    }
}
