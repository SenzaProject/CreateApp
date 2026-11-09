package com.senzaproject.createapp.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class Utf8ChunkDecoderTest {

    private fun decodeInChunks(text: String, chunkSize: Int): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val decoder = Utf8ChunkDecoder()
        val out = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val size = minOf(chunkSize, bytes.size - offset)
            out.append(decoder.decode(bytes.copyOfRange(offset, offset + size), size))
            offset += size
        }
        out.append(decoder.finish())
        return out.toString()
    }

    @Test
    fun `ASCII utuh`() {
        assertEquals("hello\n", decodeInChunks("hello\n", 3))
    }

    @Test
    fun `karakter multibyte yang terpotong di batas potongan tetap utuh`() {
        val text = "héllo — 日本語 😀 selesai"
        for (size in 1..7) {
            assertEquals("ukuran potongan $size", text, decodeInChunks(text, size))
        }
    }

    @Test
    fun `byte yang belum lengkap ditahan sampai byte berikutnya tiba`() {
        val bytes = "é".toByteArray(Charsets.UTF_8) // 2 byte
        val decoder = Utf8ChunkDecoder()
        assertEquals("", decoder.decode(bytes.copyOfRange(0, 1), 1))
        assertEquals("é", decoder.decode(bytes.copyOfRange(1, 2), 1))
    }

    @Test
    fun `urutan yang tidak pernah lengkap menjadi karakter pengganti saat finish`() {
        val bytes = "é".toByteArray(Charsets.UTF_8)
        val decoder = Utf8ChunkDecoder()
        assertEquals("", decoder.decode(bytes.copyOfRange(0, 1), 1))
        assertEquals("\uFFFD", decoder.finish())
        assertEquals("", decoder.finish())
    }

    @Test
    fun `panjang yang dipakai mengikuti parameter length bukan ukuran array`() {
        val buffer = ByteArray(16)
        "ab".toByteArray(Charsets.UTF_8).copyInto(buffer)
        assertEquals("ab", Utf8ChunkDecoder().decode(buffer, 2))
    }

    @Test
    fun `byte tidak valid tidak melempar pengecualian`() {
        val bytes = byteArrayOf(0xFF.toByte(), 'a'.code.toByte())
        assertEquals("\uFFFDa", Utf8ChunkDecoder().decode(bytes, 2))
    }
}
