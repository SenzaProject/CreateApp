package com.senzaproject.createapp.core.project

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** [TreeSource] di atas folder/file hasil Storage Access Framework. */
internal class DocumentTreeSource(
    private val context: Context,
    private val document: DocumentFile,
) : TreeSource {
    override val name: String get() = document.name ?: ""
    override val isDirectory: Boolean get() = document.isDirectory

    override fun children(): List<TreeSource> =
        document.listFiles().map { DocumentTreeSource(context, it) }

    override fun openInput(): InputStream =
        context.contentResolver.openInputStream(document.uri)
            ?: throw IOException("Tidak dapat membuka file untuk dibaca: ${document.name}")
}

/** [TreeSink] di atas folder hasil Storage Access Framework. */
internal class DocumentTreeSink(
    private val context: Context,
    private val directory: DocumentFile,
) : TreeSink {

    override fun createDirectory(name: String): TreeSink {
        val existing = directory.findFile(name)
        val child = if (existing != null && existing.isDirectory) existing else directory.createDirectory(name)
        return DocumentTreeSink(
            context,
            child ?: throw IOException("Gagal membuat folder tujuan: $name"),
        )
    }

    override fun openOutput(name: String): OutputStream {
        val file = directory.createFile(GENERIC_MIME_TYPE, name)
            ?: throw IOException("Gagal membuat file tujuan: $name")
        return context.contentResolver.openOutputStream(file.uri, "wt")
            ?: throw IOException("Tidak dapat membuka file untuk ditulis: $name")
    }

    private companion object {
        // MIME generik agar penyedia dokumen tidak menambahkan ekstensi sendiri.
        const val GENERIC_MIME_TYPE = "application/octet-stream"
    }
}
