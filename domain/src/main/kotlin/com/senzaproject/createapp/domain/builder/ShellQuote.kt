package com.senzaproject.createapp.domain.builder

/** Membungkus teks agar aman dipakai sebagai satu argumen di shell POSIX (sh/dash/bash). */
object ShellQuote {

    /**
     * Mengembalikan [value] dalam tanda kutip tunggal. Tanda kutip tunggal di dalam teks diubah menjadi
     * `'\''`. Karakter NUL tidak bisa dibawa lewat argumen proses, jadi ditolak.
     */
    fun quote(value: String): String {
        require(!value.contains('\u0000')) { "Argumen shell tidak boleh berisi karakter NUL" }
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
