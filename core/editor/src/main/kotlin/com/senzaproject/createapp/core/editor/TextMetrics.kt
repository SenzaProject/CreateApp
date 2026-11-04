package com.senzaproject.createapp.core.editor

/** Posisi kursor, berbasis 1 (baris pertama = 1, kolom pertama = 1). */
data class CursorPosition(val line: Int, val column: Int)

/** Menghitung baris dan kolom dari [offset] karakter. [offset] di luar rentang dipaksa masuk rentang. */
fun cursorPositionOf(text: String, offset: Int): CursorPosition {
    val safeOffset = offset.coerceIn(0, text.length)
    var line = 1
    var lineStart = 0
    for (index in 0 until safeOffset) {
        if (text[index] == '\n') {
            line++
            lineStart = index + 1
        }
    }
    return CursorPosition(line = line, column = safeOffset - lineStart + 1)
}

/** Jumlah baris; teks kosong tetap dihitung 1 baris, dan "\n" di akhir menambah baris kosong. */
fun lineCountOf(text: String): Int = text.count { it == '\n' } + 1
