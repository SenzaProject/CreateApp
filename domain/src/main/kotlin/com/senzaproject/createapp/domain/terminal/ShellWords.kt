package com.senzaproject.createapp.domain.terminal

/**
 * Memecah teks menjadi kata ala shell: spasi memisahkan kata; tanda kutip tunggal bersifat harfiah;
 * tanda kutip ganda mengizinkan `\"` dan `\\`; backslash di luar kutip meloloskan karakter berikutnya.
 * Tidak ada ekspansi variabel atau wildcard.
 *
 * @return daftar kata, atau null bila ada tanda kutip yang tidak ditutup.
 */
object ShellWords {

    fun split(text: String): List<String>? {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inToken = false
        var quote: Char? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote == '\'') {
                if (c == '\'') quote = null else current.append(c)
            } else if (quote == '"') {
                if (c == '"') {
                    quote = null
                } else if (c == '\\' && i + 1 < text.length && (text[i + 1] == '"' || text[i + 1] == '\\')) {
                    current.append(text[i + 1])
                    i++
                } else {
                    current.append(c)
                }
            } else if (c == '\'' || c == '"') {
                quote = c
                inToken = true
            } else if (c == '\\') {
                inToken = true
                if (i + 1 < text.length) {
                    current.append(text[i + 1])
                    i++
                } else {
                    current.append(c)
                }
            } else if (c.isWhitespace()) {
                if (inToken) {
                    tokens.add(current.toString())
                    current.setLength(0)
                    inToken = false
                }
            } else {
                current.append(c)
                inToken = true
            }
            i++
        }
        if (quote != null) return null
        if (inToken) tokens.add(current.toString())
        return tokens
    }
}
