package com.senzaproject.createapp.core.terminal

/**
 * Mendekode aliran byte UTF-8 yang dibaca per potongan. Karakter multibyte yang terpotong di batas
 * potongan ditahan sampai byte berikutnya tiba, sehingga tidak berubah menjadi karakter rusak.
 */
internal class Utf8ChunkDecoder {

    private var carry = ByteArray(0)

    fun decode(bytes: ByteArray, length: Int): String {
        val all = if (carry.isEmpty()) bytes.copyOf(length) else carry + bytes.copyOf(length)
        val complete = completeLength(all)
        carry = all.copyOfRange(complete, all.size)
        return String(all, 0, complete, Charsets.UTF_8)
    }

    /** Mengosongkan sisa byte (urutan yang tidak pernah lengkap menjadi karakter pengganti). */
    fun finish(): String {
        if (carry.isEmpty()) return ""
        val text = String(carry, Charsets.UTF_8)
        carry = ByteArray(0)
        return text
    }

    /** Panjang awalan yang tidak berakhir di tengah karakter multibyte. */
    private fun completeLength(bytes: ByteArray): Int {
        var index = bytes.size - 1
        var scanned = 0
        while (index >= 0 && scanned < MAX_TRAILING_BYTES) {
            val value = bytes[index].toInt() and 0xFF
            if ((value and 0xC0) != 0x80) {
                val needed = when {
                    value >= 0xF0 -> 4
                    value >= 0xE0 -> 3
                    value >= 0xC0 -> 2
                    else -> 1
                }
                return if (bytes.size - index < needed) index else bytes.size
            }
            index--
            scanned++
        }
        return bytes.size
    }

    private companion object {
        const val MAX_TRAILING_BYTES = 3
    }
}
