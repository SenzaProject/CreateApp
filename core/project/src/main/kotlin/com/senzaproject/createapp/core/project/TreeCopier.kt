package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.METADATA_DIR_NAME
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Sumber penyalinan: folder atau file (di penyimpanan lokal atau lewat SAF). */
interface TreeSource {
    val name: String
    val isDirectory: Boolean
    fun children(): List<TreeSource>
    fun openInput(): InputStream
}

/** Tujuan penyalinan: sebuah folder yang dapat dibuatkan anak folder dan file. */
interface TreeSink {
    fun createDirectory(name: String): TreeSink
    fun openOutput(name: String): OutputStream
}

data class CopyStats(val files: Int, val bytes: Long)

/** Batas pengamanan penyalinan agar impor/ekspor tidak menghabiskan penyimpanan atau berputar tanpa akhir. */
data class CopyLimits(
    val maxFiles: Int = 20_000,
    val maxBytes: Long = 512L * 1024 * 1024,
    val maxDepth: Int = 64,
    /** Folder yang dilewati (cocok persis dengan nama). */
    val skipDirectoryNames: Set<String> = emptySet(),
)

/** Folder keluaran build yang tidak ikut diimpor dari folder luar. */
val IMPORT_SKIPPED_DIRECTORIES: Set<String> = setOf("build", ".gradle", ".cxx", METADATA_DIR_NAME)

/** Menyalin isi folder dengan pemeriksaan nama, batas ukuran, dan pembatalan. */
object TreeCopier {

    /**
     * Menyalin seluruh anak [source] ke [sink].
     *
     * @param checkCancelled dipanggil berkala; boleh melempar CancellationException untuk menghentikan penyalinan.
     * @throws OperationException TOO_LARGE bila melebihi [limits]; INVALID_SOURCE bila ada nama entri yang tidak aman.
     */
    fun copyChildren(
        source: TreeSource,
        sink: TreeSink,
        limits: CopyLimits = CopyLimits(),
        checkCancelled: () -> Unit = {},
    ): CopyStats {
        val run = Run(limits, checkCancelled)
        run.copyChildren(source, sink, depth = 0)
        return CopyStats(files = run.files, bytes = run.bytes)
    }

    private class Run(
        private val limits: CopyLimits,
        private val checkCancelled: () -> Unit,
    ) {
        var files = 0
        var bytes = 0L

        fun copyChildren(source: TreeSource, sink: TreeSink, depth: Int) {
            if (depth > limits.maxDepth) {
                throw OperationException(OperationFailure.TOO_LARGE, "Struktur folder terlalu dalam")
            }
            for (child in source.children()) {
                checkCancelled()
                val name = child.name
                requireSafeName(name)
                if (child.isDirectory) {
                    if (name in limits.skipDirectoryNames) continue
                    copyChildren(child, sink.createDirectory(name), depth + 1)
                } else {
                    copyFile(child, sink, name)
                }
            }
        }

        private fun copyFile(child: TreeSource, sink: TreeSink, name: String) {
            if (files >= limits.maxFiles) {
                throw OperationException(OperationFailure.TOO_LARGE, "Jumlah file melebihi batas ${limits.maxFiles}")
            }
            val buffer = ByteArray(BUFFER_SIZE)
            child.openInput().use { input ->
                sink.openOutput(name).use { output ->
                    while (true) {
                        checkCancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytes += read
                        if (bytes > limits.maxBytes) {
                            throw OperationException(OperationFailure.TOO_LARGE, "Ukuran total melebihi batas")
                        }
                    }
                }
            }
            files++
        }

        private fun requireSafeName(name: String) {
            val unsafe = name.isEmpty() || name == "." || name == ".." ||
                name.any { it == '/' || it == '\\' || it == '\u0000' }
            if (unsafe) {
                throw OperationException(OperationFailure.INVALID_SOURCE, "Nama entri tidak aman: \"$name\"")
            }
        }
    }

    private const val BUFFER_SIZE = 64 * 1024
}

/** [TreeSource] untuk file/folder lokal. */
class FileTreeSource(private val file: File) : TreeSource {
    override val name: String get() = file.name
    override val isDirectory: Boolean get() = file.isDirectory

    override fun children(): List<TreeSource> {
        val list = file.listFiles() ?: throw IOException("Tidak dapat membaca folder: ${file.path}")
        return list.sortedBy { it.name }.map { FileTreeSource(it) }
    }

    override fun openInput(): InputStream = file.inputStream()
}

/** [TreeSink] untuk folder lokal. */
class FileTreeSink(private val dir: File) : TreeSink {
    override fun createDirectory(name: String): TreeSink {
        val child = File(dir, name)
        if (!child.isDirectory && !child.mkdir()) throw IOException("Gagal membuat folder: ${child.path}")
        return FileTreeSink(child)
    }

    override fun openOutput(name: String): OutputStream = FileOutputStream(File(dir, name))
}
