package com.senzaproject.createapp.domain.terminal

/**
 * Riwayat perintah di memori (tidak ditulis ke disk, agar token atau password yang mungkin diketik
 * tidak tersimpan). Perintah berurutan yang sama tidak digandakan; kapasitas dibatasi.
 */
class CommandHistory(private val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity harus positif" }
    }

    private val items = ArrayList<String>()
    private var cursor = -1
    private var draft = ""

    val entries: List<String> get() = items.toList()

    fun add(command: String) {
        val text = command.trim()
        if (text.isNotEmpty() && items.lastOrNull() != text) {
            items.add(text)
            while (items.size > capacity) items.removeAt(0)
        }
        resetNavigation()
    }

    /**
     * Naik ke perintah yang lebih lama. Teks yang sedang diketik ([currentInput]) disimpan sebagai
     * draf pada panggilan pertama dan dikembalikan oleh [next] setelah melewati perintah terbaru.
     * Mengembalikan null bila riwayat kosong.
     */
    fun previous(currentInput: String): String? {
        if (items.isEmpty()) return null
        if (cursor == -1) {
            draft = currentInput
            cursor = items.size - 1
        } else if (cursor > 0) {
            cursor -= 1
        }
        return items[cursor]
    }

    /** Turun ke perintah yang lebih baru; setelah yang terbaru mengembalikan draf. Null bila tidak sedang menavigasi. */
    fun next(): String? {
        if (cursor == -1) return null
        if (cursor < items.size - 1) {
            cursor += 1
            return items[cursor]
        }
        val restored = draft
        resetNavigation()
        return restored
    }

    fun resetNavigation() {
        cursor = -1
        draft = ""
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
