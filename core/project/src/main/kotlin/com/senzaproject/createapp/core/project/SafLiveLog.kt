package com.senzaproject.createapp.core.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.senzaproject.createapp.domain.builder.LiveLogSource
import com.senzaproject.createapp.domain.builder.WorkspaceLayout
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Membaca `logs/<id proyek>.log` di folder bersama selagi helper Termux menulisnya. Hanya membaca
 * bagian yang belum pernah dibaca (berdasarkan posisi byte), dan hanya mengembalikan baris yang sudah
 * lengkap (pemotongan di byte `\n` aman untuk UTF-8 karena byte itu tidak muncul di dalam karakter
 * multibyte). Sisa tanpa baris baru ditahan sampai baris itu selesai atau sampai `flush`.
 *
 * Setiap kegagalan (file belum ada, izin, penyedia dokumen bermasalah) menghasilkan null, tidak pernah
 * pengecualian; pemanggil tetap punya ekor log dari stdout Termux sebagai cadangan.
 *
 * Tidak thread-safe; dipanggil berurutan oleh satu penarik.
 */
internal class SafLiveLog(
    private val context: Context,
    private val treeUri: String,
    private val projectId: String,
    private val ioDispatcher: CoroutineDispatcher,
) : LiveLogSource {

    private var file: DocumentFile? = null
    private var offset = 0L
    private var pending = ByteArray(0)

    override suspend fun readNew(flush: Boolean): String? = withContext(ioDispatcher) {
        try {
            readBlocking(flush)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun readBlocking(flush: Boolean): String? {
        val document = locate() ?: return null
        val length = document.length()
        if (length < offset) {
            // File dikosongkan ulang (build baru): mulai dari awal.
            offset = 0L
            pending = ByteArray(0)
        }

        val chunk = if (length > offset) (readChunk(document, length) ?: return null) else ByteArray(0)
        offset += chunk.size

        val all = if (pending.isEmpty()) chunk else pending + chunk
        val end = if (flush) all.size else lastNewline(all) + 1
        pending = all.copyOfRange(end, all.size)
        return if (end == 0) "" else String(all, 0, end, Charsets.UTF_8)
    }

    private fun readChunk(document: DocumentFile, length: Long): ByteArray? {
        val toRead = minOf(length - offset, MAX_CHUNK_BYTES.toLong()).toInt()
        val input = context.contentResolver.openInputStream(document.uri) ?: return null
        return input.use { stream ->
            if (!skipFully(stream, offset)) return@use null
            val buffer = ByteArray(toRead)
            var filled = 0
            while (filled < toRead) {
                val count = stream.read(buffer, filled, toRead - filled)
                if (count < 0) break
                filled += count
            }
            if (filled == toRead) buffer else buffer.copyOf(filled)
        }
    }

    private fun locate(): DocumentFile? {
        file?.let { return it }
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        val found = tree.findFile(WorkspaceLayout.LOG_DIR)?.findFile(WorkspaceLayout.liveLogName(projectId))
        if (found != null && found.isFile) file = found
        return file
    }

    private fun lastNewline(bytes: ByteArray): Int {
        var index = bytes.size - 1
        while (index >= 0) {
            if (bytes[index] == NEWLINE) return index
            index--
        }
        return -1
    }

    private fun skipFully(stream: InputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                if (stream.read() < 0) return false
                remaining -= 1L
            }
        }
        return true
    }

    private companion object {
        const val MAX_CHUNK_BYTES = 256 * 1024
        const val NEWLINE: Byte = 0x0A
    }
}
