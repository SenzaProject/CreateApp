package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.METADATA_DIR_NAME
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectTemplate
import com.senzaproject.createapp.domain.project.splitRelativePath
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.util.Properties
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/** Metadata proyek yang disimpan di `.createapp/project.properties`. */
internal data class ProjectMeta(
    val name: String,
    val packageName: String,
    val favorite: Boolean,
    val template: ProjectTemplate,
)

/**
 * Penyimpanan proyek di sebuah folder [root] (di aplikasi: `filesDir/projects`). Setiap proyek
 * adalah satu subfolder; folder itu sendiri adalah sumber kebenaran, sehingga tidak ada database
 * terpisah yang bisa tidak sinkron. Metadata ditulis atomik, dan data yang rusak tidak membuat
 * proyek hilang dari daftar.
 */
class ProjectStorage(
    val root: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Dinaikkan setiap kali isi penyimpanan berubah; dipakai untuk menyegarkan daftar proyek. */
    val changes = MutableStateFlow(0)

    /** Menserialkan semua operasi yang mengubah penyimpanan. */
    val lock = Mutex()

    private val stagingCleaned = AtomicBoolean(false)

    fun notifyChanged() {
        changes.update { it + 1 }
    }

    fun ensureRoot() {
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
            throw IOException("Gagal membuat folder penyimpanan proyek: ${root.path}")
        }
    }

    /** Memastikan ruang kosong minimal [neededBytes]; bila kurang melempar LOW_STORAGE. */
    fun requireFreeSpace(neededBytes: Long) {
        ensureRoot()
        if (root.usableSpace < neededBytes) {
            throw OperationException(OperationFailure.LOW_STORAGE, "Ruang penyimpanan tidak cukup")
        }
    }

    fun isValidId(id: String): Boolean = ID_REGEX.matches(id)

    /** Folder proyek untuk [id]; melempar NOT_FOUND bila tidak ada atau OUTSIDE_PROJECT bila bukan anak langsung [root]. */
    fun projectDir(id: String): File {
        if (!isValidId(id)) throw OperationException(OperationFailure.NOT_FOUND, "Proyek tidak ditemukan: \"$id\"")
        val dir = File(root, id)
        if (!dir.isDirectory) throw OperationException(OperationFailure.NOT_FOUND, "Proyek tidak ditemukan: \"$id\"")
        if (dir.canonicalFile.parentFile != root.canonicalFile) {
            throw OperationException(OperationFailure.OUTSIDE_PROJECT, "Folder proyek berada di luar penyimpanan: \"$id\"")
        }
        return dir
    }

    /**
     * Menerjemahkan path relatif ke [File] di dalam [projectDir]. Menolak `..`, path absolut, folder
     * metadata, dan tautan simbolik yang mengarah ke luar proyek.
     */
    fun resolve(projectDir: File, relativePath: String): File {
        val parts = splitRelativePath(relativePath)
        if (parts.firstOrNull().equals(METADATA_DIR_NAME, ignoreCase = true)) {
            throw OperationException(OperationFailure.RESERVED_NAME, "Folder $METADATA_DIR_NAME dicadangkan sistem")
        }
        var target = projectDir
        for (part in parts) target = File(target, part)
        val rootPath = projectDir.canonicalFile.path
        val targetPath = target.canonicalFile.path
        if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) {
            throw OperationException(OperationFailure.OUTSIDE_PROJECT, "Path keluar dari proyek: \"$relativePath\"")
        }
        return target
    }

    fun scanProjects(): List<Project> {
        if (!root.isDirectory) return emptyList()
        cleanStagingOnce()
        return projectDirectories().map { readProject(it) }
    }

    /** Pasangan id ke nama tampilan, tanpa menghitung ukuran (murah). */
    internal fun projectNames(): List<Pair<String, String>> =
        projectDirectories().map { it.name to readMeta(it).name }

    fun readProject(dir: File): Project {
        val meta = readMeta(dir)
        val metaModified = metaFile(dir).lastModified()
        return Project(
            id = dir.name,
            name = meta.name,
            packageName = meta.packageName,
            lastModifiedMillis = if (metaModified > 0L) metaModified else dir.lastModified(),
            sizeBytes = directorySize(dir),
            isFavorite = meta.favorite,
        )
    }

    /** Memperbarui waktu "terakhir diubah" proyek (dipakai filter Terbaru). */
    fun touch(projectDir: File) {
        val meta = metaFile(projectDir)
        if (meta.isFile) meta.setLastModified(clock())
    }

    fun metaFile(dir: File): File = File(File(dir, METADATA_DIR_NAME), META_FILE_NAME)

    /** Membaca metadata; bila hilang atau rusak, kembali ke nilai bawaan agar proyek tetap tampil. */
    internal fun readMeta(dir: File): ProjectMeta {
        val fallback = ProjectMeta(
            name = dir.name,
            packageName = "",
            favorite = false,
            template = ProjectTemplate.EMPTY,
        )
        val file = metaFile(dir)
        if (!file.isFile) return fallback
        return try {
            val props = Properties()
            file.reader(Charsets.UTF_8).use { props.load(it) }
            val templateName = props.getProperty(KEY_TEMPLATE)
            ProjectMeta(
                name = props.getProperty(KEY_NAME)?.takeIf { it.isNotBlank() } ?: fallback.name,
                packageName = props.getProperty(KEY_PACKAGE).orEmpty(),
                favorite = props.getProperty(KEY_FAVORITE) == "true",
                template = ProjectTemplate.entries.firstOrNull { it.name == templateName } ?: ProjectTemplate.EMPTY,
            )
        } catch (e: IOException) {
            fallback
        } catch (e: IllegalArgumentException) {
            fallback
        }
    }

    internal fun writeMeta(dir: File, meta: ProjectMeta) {
        val props = Properties()
        props.setProperty(KEY_NAME, meta.name)
        props.setProperty(KEY_PACKAGE, meta.packageName)
        props.setProperty(KEY_FAVORITE, meta.favorite.toString())
        props.setProperty(KEY_TEMPLATE, meta.template.name)
        val writer = StringWriter()
        props.store(writer, null)
        atomicWrite(metaFile(dir), writer.toString().toByteArray(Charsets.UTF_8))
    }

    /** Id folder yang belum dipakai, diturunkan dari [base] (tanpa membedakan huruf besar/kecil). */
    fun uniqueId(base: String): String {
        val existing = root.list()?.map { it.lowercase() }?.toSet() ?: emptySet()
        if (base.lowercase() !in existing) return base
        for (index in 2..MAX_ID_ATTEMPTS) {
            val candidate = "$base-$index"
            if (candidate.lowercase() !in existing) return candidate
        }
        throw IOException("Tidak dapat menentukan nama folder yang unik untuk \"$base\"")
    }

    /** Nama tampilan yang belum dipakai proyek lain: "Nama", "Nama (2)", "Nama (3)", ... */
    internal fun uniqueDisplayName(base: String): String {
        val taken = projectNames().map { it.second.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        for (index in 2..MAX_ID_ATTEMPTS) {
            val candidate = "$base ($index)"
            if (candidate.lowercase() !in taken) return candidate
        }
        throw IOException("Tidak dapat menentukan nama proyek yang unik untuk \"$base\"")
    }

    /** Folder sementara untuk impor; tidak dianggap proyek sampai dipindahkan ke nama akhirnya. */
    fun newStagingDir(): File = File(root, STAGING_PREFIX + UUID.randomUUID().toString())

    private fun projectDirectories(): List<File> {
        val children = root.listFiles() ?: throw IOException("Tidak dapat membaca folder proyek: ${root.path}")
        return children.filter { it.isDirectory && isValidId(it.name) }
    }

    private fun directorySize(dir: File): Long {
        var total = 0L
        dir.walkTopDown()
            .onEnter { it.name != METADATA_DIR_NAME }
            .forEach { if (it.isFile) total += it.length() }
        return total
    }

    /** Menghapus sisa impor yang terhenti (lebih dari 24 jam). Dijalankan sekali per proses. */
    private fun cleanStagingOnce() {
        if (!stagingCleaned.compareAndSet(false, true)) return
        val cutoff = clock() - STAGING_MAX_AGE_MILLIS
        root.listFiles()?.forEach { entry ->
            if (entry.isDirectory && entry.name.startsWith(STAGING_PREFIX) && entry.lastModified() < cutoff) {
                entry.deleteRecursively()
            }
        }
    }

    companion object {
        const val META_FILE_NAME = "project.properties"
        internal const val STAGING_PREFIX = ".importing-"
        internal const val SPACE_MARGIN_BYTES = 1L shl 20
        private const val STAGING_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
        private const val MAX_ID_ATTEMPTS = 1000
        private const val KEY_NAME = "name"
        private const val KEY_PACKAGE = "package"
        private const val KEY_FAVORITE = "favorite"
        private const val KEY_TEMPLATE = "template"

        /** Id proyek = nama folder: diawali huruf/angka/underscore, panjang maksimal 64. */
        private val ID_REGEX = Regex("[A-Za-z0-9_][A-Za-z0-9_-]{0,63}")
    }
}
