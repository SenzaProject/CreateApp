package com.senzaproject.createapp.domain.terminal

/**
 * Merakit potongan teks output (yang bisa terpotong di tengah baris) menjadi baris utuh untuk
 * ditampilkan. Satu instance per aliran (stdout/stderr).
 *
 * Perilaku:
 * - Baris selesai pada `\n`; `\r\n` diperlakukan sebagai satu pemisah.
 * - `\r` di tengah baris (bilah progres) menimpa teks sebelumnya: hanya teks setelah `\r` terakhir ditampilkan.
 * - Urutan escape ANSI (warna, kursor, judul jendela) dibuang; karakter kontrol lain dibuang; tab menjadi 4 spasi.
 * - Baris lebih panjang dari [maxLineLength] dipotong dengan "…"; output tanpa baris baru dipecah paksa
 *   agar memori tidak membengkak.
 */
class LineAssembler(private val maxLineLength: Int = DEFAULT_MAX_LINE_LENGTH) {

    init {
        require(maxLineLength > 0) { "maxLineLength harus positif" }
    }

    private val pending = StringBuilder()

    /** Memasukkan [chunk]; mengembalikan baris-baris yang sudah lengkap (bisa kosong). */
    fun append(chunk: String): List<String> {
        if (chunk.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        var start = 0
        while (true) {
            val newline = chunk.indexOf('\n', start)
            if (newline < 0) break
            pending.append(chunk, start, newline)
            lines.add(takePending())
            start = newline + 1
        }
        pending.append(chunk, start, chunk.length)
        while (pending.length >= maxLineLength * FORCED_SPLIT_FACTOR) {
            val forced = pending.substring(0, maxLineLength)
            pending.delete(0, maxLineLength)
            lines.add(sanitize(forced))
        }
        return lines
    }

    /** Mengeluarkan sisa teks tanpa baris baru di akhir; null bila tidak ada isi yang terlihat. */
    fun flush(): String? {
        if (pending.isEmpty()) return null
        val line = takePending()
        return if (line.isEmpty()) null else line
    }

    private fun takePending(): String {
        val raw = pending.toString()
        pending.setLength(0)
        return sanitize(raw)
    }

    private fun sanitize(raw: String): String {
        var text = raw.trimEnd('\r')
        val lastCarriageReturn = text.lastIndexOf('\r')
        if (lastCarriageReturn >= 0) text = text.substring(lastCarriageReturn + 1)
        text = ANSI_SEQUENCE.replace(text, "")
        val out = StringBuilder(text.length)
        for (ch in text) {
            if (ch == '\t') {
                out.append("    ")
            } else if (ch >= ' ' && ch != '\u007F') {
                out.append(ch)
            }
        }
        val cleaned = out.toString()
        return if (cleaned.length > maxLineLength) cleaned.substring(0, maxLineLength) + "…" else cleaned
    }

    private companion object {
        const val FORCED_SPLIT_FACTOR = 4

        // CSI (ESC [ ... final), OSC (ESC ] ... BEL), dan escape dua karakter (ESC + @..-_).
        val ANSI_SEQUENCE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007]*\u0007|\u001B[@-_]")
    }
}

const val DEFAULT_MAX_LINE_LENGTH = 4000
