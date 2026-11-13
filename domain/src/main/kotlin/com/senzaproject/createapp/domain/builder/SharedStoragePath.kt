package com.senzaproject.createapp.domain.builder

import java.io.ByteArrayOutputStream

/**
 * Mengubah URI folder pilihan pengguna (Storage Access Framework) menjadi path yang dilihat Termux.
 *
 * Hanya penyimpanan internal utama ("primary") yang didukung, karena Termux mengaksesnya lewat
 * `/storage/emulated/0` setelah `termux-setup-storage`. Volume lain (kartu SD) ditolak (null).
 */
object SharedStoragePath {

    private const val TREE_PREFIX = "content://com.android.externalstorage.documents/tree/"
    private const val PRIMARY_VOLUME = "primary"
    private const val PRIMARY_ROOT = "/storage/emulated/0"

    /** Path absolut untuk [treeUri], atau null bila URI bukan folder di penyimpanan internal utama. */
    fun fromTreeUri(treeUri: String): String? {
        if (!treeUri.startsWith(TREE_PREFIX)) return null
        var encoded = treeUri.substring(TREE_PREFIX.length)
        val documentIndex = encoded.indexOf("/document/")
        if (documentIndex >= 0) encoded = encoded.substring(0, documentIndex)

        val documentId = percentDecode(encoded) ?: return null
        val colon = documentId.indexOf(':')
        if (colon < 0) return null
        if (documentId.substring(0, colon) != PRIMARY_VOLUME) return null

        val relative = documentId.substring(colon + 1).trim('/')
        if (relative.isEmpty()) return PRIMARY_ROOT
        val parts = relative.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (relative.contains('\u0000')) return null
        return "$PRIMARY_ROOT/$relative"
    }

    /**
     * Kebalikan [fromTreeUri]: id dokumen `primary:<relatif>` untuk path di penyimpanan internal utama,
     * dipakai membuka folder di aplikasi File. Null bila path bukan di penyimpanan utama atau tidak aman.
     */
    fun documentIdForPath(path: String): String? {
        if (path == PRIMARY_ROOT) return "$PRIMARY_VOLUME:"
        if (!path.startsWith("$PRIMARY_ROOT/")) return null
        val relative = path.substring(PRIMARY_ROOT.length + 1).removeSuffix("/")
        if (relative.isEmpty() || relative.contains('\u0000')) return null
        if (relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return "$PRIMARY_VOLUME:$relative"
    }

    /** Dekode persen untuk UTF-8; null bila tidak valid. Tanda `+` TIDAK diubah menjadi spasi. */
    private fun percentDecode(text: String): String? {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '%') {
                if (index + 2 >= text.length) return null
                val high = Character.digit(text[index + 1], 16)
                val low = Character.digit(text[index + 2], 16)
                if (high < 0 || low < 0) return null
                bytes.write(high * 16 + low)
                index += 3
            } else {
                if (ch.code >= 128) return null
                bytes.write(ch.code)
                index += 1
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}

/** Tata letak folder bersama: `src/<id proyek>` untuk sumber, `out/<id proyek>` untuk hasil build. */
object WorkspaceLayout {
    const val SOURCE_DIR = "src"
    const val OUTPUT_DIR = "out"

    /** Log langsung yang ditulis helper selama build: `live/<id proyek>.log`. */
    const val LIVE_DIR = "live"
    const val LIVE_SUFFIX = ".log"

    fun sourcePath(rootPath: String, projectId: String): String = "$rootPath/$SOURCE_DIR/$projectId"

    fun outputRootPath(rootPath: String): String = "$rootPath/$OUTPUT_DIR"

    fun outputPath(rootPath: String, projectId: String): String = "$rootPath/$OUTPUT_DIR/$projectId"
}
