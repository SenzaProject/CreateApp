package com.senzaproject.createapp.domain.builder

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Sumber log langsung. Termux hanya mengirim keluaran perintah saat perintah SELESAI, jadi selama
 * Gradle berjalan aplikasi tidak melihat apa-apa. Karena itu helper juga menyalin log Gradle ke
 * `live/<id proyek>.log` di folder bersama setiap beberapa detik, dan aplikasi membacanya lewat
 * Storage Access Framework (implementasi: `SafBuildLiveLog` di `:core:project`).
 */
interface BuildLiveLogSource {
    /**
     * Isi log langsung untuk [projectId], atau null bila belum ada / tidak terbaca. Baris pertama selalu
     * header run; bila file lebih besar dari [maxBytes], sisanya hanya bagian paling akhir.
     */
    suspend fun read(treeUri: String, projectId: String, maxBytes: Int): String?
}

/** Tampilan log langsung yang sudah diurai. */
data class LiveLogView(
    val lines: List<String>,
    val currentTask: String?,
    val tasksSeen: Int,
    val droppedLines: Int,
)

const val DEFAULT_LIVE_LOG_INTERVAL_MILLIS = 2_000L
const val DEFAULT_LIVE_LOG_MAX_BYTES = 512 * 1024
const val DEFAULT_LIVE_LOG_MAX_LINES = 300

object LiveLog {
    private const val HEADER_PREFIX = "# createapp-run "

    private const val HEADER_LIMIT = 256

    /**
     * Membaca isi file log: bila [length] <= [maxBytes] seluruhnya; bila lebih besar, baris pertama (header)
     * ditambah [maxBytes] byte terakhir yang dipotong di batas baris, supaya header run tetap ada.
     * [stream] harus dimulai dari awal file.
     */
    fun readHeadAndTail(stream: InputStream, length: Long, maxBytes: Int): String {
        if (length <= maxBytes) return String(stream.readBytes(), Charsets.UTF_8)

        val head = ByteArrayOutputStream()
        var read = 0
        while (read < HEADER_LIMIT) {
            val b = stream.read()
            if (b < 0) break
            read++
            if (b == '\n'.code) break
            head.write(b)
        }
        val skipTo = length - maxBytes
        var skipped = read.toLong()
        while (skipped < skipTo) {
            val n = stream.skip(skipTo - skipped)
            if (n <= 0L) {
                if (stream.read() < 0) break
                skipped++
            } else {
                skipped += n
            }
        }
        val tail = stream.readBytes()
        // Buang potongan baris pertama dari bagian ekor karena kemungkinan terpotong di tengah.
        var start = 0
        while (start < tail.size && tail[start] != '\n'.code.toByte()) start++
        start = if (start < tail.size) start + 1 else tail.size
        return String(head.toByteArray(), Charsets.UTF_8) + "\n" + String(tail, start, tail.size - start, Charsets.UTF_8)
    }

    /** Header yang ditulis helper sebagai baris pertama; berisi token unik tiap build. */
    fun header(token: String): String = HEADER_PREFIX + token

    /** Token run harus sederhana agar aman dipakai sebagai argumen helper dan baris header. */
    fun isValidToken(token: String): Boolean =
        token.isNotEmpty() && token.length <= 64 && token.all { (it.isLetterOrDigit() && it.code < 128) || it == '-' || it == '_' }

    /**
     * Mengurai [text] menjadi tampilan. Mengembalikan null bila baris pertama bukan header untuk
     * [token] — artinya file itu sisa build sebelumnya atau build ini belum menulis apa pun.
     *
     * Baris terakhir yang belum diakhiri baris baru dibuang: bisa saja sedang ditulis (terpotong),
     * dan menampilkannya akan membuat nama task tampak salah sesaat.
     */
    fun parse(text: String, token: String, maxLines: Int = DEFAULT_LIVE_LOG_MAX_LINES): LiveLogView? {
        if (maxLines < 1) return null
        val firstBreak = text.indexOf('\n')
        if (firstBreak < 0) return null
        if (text.substring(0, firstBreak).trimEnd('\r') != header(token)) return null

        val body = text.substring(firstBreak + 1)
        val complete = body.substring(0, body.lastIndexOf('\n') + 1)
        val all: List<String> =
            if (complete.isEmpty()) emptyList() else complete.removeSuffix("\n").split('\n').map { it.trimEnd('\r') }
        val parser = GradleLogParser()
        for (line in all) parser.accept(line)
        val dropped = (all.size - maxLines).coerceAtLeast(0)
        val shown = if (dropped > 0) all.subList(dropped, all.size).toList() else all
        return LiveLogView(
            lines = shown,
            currentTask = parser.currentTask,
            tasksSeen = parser.taskCount,
            droppedLines = dropped,
        )
    }
}
