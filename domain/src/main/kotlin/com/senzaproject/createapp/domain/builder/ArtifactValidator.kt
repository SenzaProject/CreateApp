package com.senzaproject.createapp.domain.builder

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Memeriksa bahwa file artefak benar-benar berformat APK/AAB: ZIP yang utuh (catatan akhir ZIP ada dan
 * konsisten) dan berisi manifest yang diwajibkan. Ini pemeriksaan struktur, BUKAN verifikasi tanda
 * tangan (Tahap 8).
 *
 * Keutuhan perlu diperiksa terpisah karena [ZipInputStream] menganggap data yang terpotong di batas
 * entri sebagai akhir ZIP biasa: APK terpotong yang manifestnya ada di awal file akan lolos tanpa
 * pemeriksaan catatan akhir.
 */
object ArtifactValidator {

    private const val MAX_ENTRIES = 200_000
    private const val EOCD_MIN_SIZE = 22
    private const val MAX_COMMENT = 65_535
    private const val DRAIN_BUFFER = 64 * 1024
    private const val APK_MANIFEST = "AndroidManifest.xml"
    private const val AAB_MANIFEST = "base/manifest/AndroidManifest.xml"
    private const val AAB_CONFIG = "BundleConfig.pb"

    /** [input] ditutup oleh pemanggil. Mengembalikan VALID atau INVALID. */
    fun validate(kind: ArtifactKind, input: InputStream): ArtifactValidation {
        val required: Set<String> = when (kind) {
            ArtifactKind.APK -> setOf(APK_MANIFEST)
            ArtifactKind.AAB -> setOf(AAB_MANIFEST, AAB_CONFIG)
        }
        val found = HashSet<String>()
        val recorder = TailRecorder(input, EOCD_MIN_SIZE + MAX_COMMENT)
        try {
            val zip = ZipInputStream(recorder)
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > MAX_ENTRIES) return ArtifactValidation.INVALID
                if (entry.name in required) found.add(entry.name)
                zip.closeEntry()
            }
            // Baca sisa file agar bagian ekornya (catatan akhir ZIP) terekam.
            val buffer = ByteArray(DRAIN_BUFFER)
            while (recorder.read(buffer, 0, buffer.size) >= 0) {
                // dibuang
            }
        } catch (e: ZipException) {
            return ArtifactValidation.INVALID
        } catch (e: IOException) {
            return ArtifactValidation.INVALID
        }
        if (!found.containsAll(required)) return ArtifactValidation.INVALID
        return if (hasValidEnd(recorder.tail(), recorder.total)) ArtifactValidation.VALID else ArtifactValidation.INVALID
    }

    /**
     * Mencari End-of-Central-Directory dari belakang. Catatan itu harus berakhir tepat di ujung file
     * (sesuai panjang komentar) dan menunjuk direktori pusat yang berada sebelum catatan itu sendiri.
     */
    private fun hasValidEnd(tail: ByteArray, total: Long): Boolean {
        var index = tail.size - EOCD_MIN_SIZE
        while (index >= 0) {
            if (tail[index] == 0x50.toByte() &&
                tail[index + 1] == 0x4B.toByte() &&
                tail[index + 2] == 0x05.toByte() &&
                tail[index + 3] == 0x06.toByte()
            ) {
                val commentLength = u16(tail, index + 20)
                if (index + EOCD_MIN_SIZE + commentLength == tail.size) {
                    val directorySize = u32(tail, index + 12)
                    val directoryOffset = u32(tail, index + 16)
                    // 0xFFFFFFFF menandakan ZIP64; nilai sebenarnya ada di catatan lain, tidak diperiksa di sini.
                    if (directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL) return true
                    val eocdOffset = total - tail.size + index
                    return directoryOffset + directorySize <= eocdOffset
                }
            }
            index--
        }
        return false
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)

    /** Meneruskan bacaan sambil mengingat jumlah byte dan [capacity] byte terakhir. */
    private class TailRecorder(private val source: InputStream, private val capacity: Int) : InputStream() {
        private val ring = ByteArray(capacity)

        var total: Long = 0L
            private set

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = source.read(buffer, offset, length)
            if (count > 0) record(buffer, offset, count)
            return count
        }

        override fun available(): Int = source.available()

        private fun record(buffer: ByteArray, offset: Int, length: Int) {
            for (i in 0 until length) {
                ring[(total % capacity).toInt()] = buffer[offset + i]
                total++
            }
        }

        fun tail(): ByteArray {
            val size = if (total < capacity) total.toInt() else capacity
            val start = total - size
            val out = ByteArray(size)
            for (i in 0 until size) out[i] = ring[((start + i) % capacity).toInt()]
            return out
        }
    }
}
