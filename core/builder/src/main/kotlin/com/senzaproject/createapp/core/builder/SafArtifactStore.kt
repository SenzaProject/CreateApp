package com.senzaproject.createapp.core.builder

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.senzaproject.createapp.domain.apk.ArtifactDetails
import com.senzaproject.createapp.domain.apk.ArtifactNames
import com.senzaproject.createapp.domain.apk.ArtifactStore
import com.senzaproject.createapp.domain.apk.ManagedArtifact
import com.senzaproject.createapp.domain.apk.StagedArtifact
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.ArtifactValidator
import com.senzaproject.createapp.domain.builder.HelperProtocol
import com.senzaproject.createapp.domain.builder.WorkspaceLayout
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import com.senzaproject.createapp.domain.result.runCatchingApp
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ArtifactStore] di atas folder bersama (Storage Access Framework). Satu-satunya sumber kebenaran adalah
 * isi folder `out/<id proyek>/`: daftar dibuat dari isi folder pada saat dipanggil.
 *
 * Pemasangan dan pembagian butuh file yang bisa diberikan lewat FileProvider, sedangkan file di folder
 * bersama tidak bisa. Maka file disalin dulu ke [cacheRoot] (cache privat, `<id proyek>/<nama>`),
 * dengan pemeriksaan ukuran, lalu dibagikan dari sana. Salinan yang tidak lagi punya file asal dibersihkan
 * setiap [list].
 *
 * @param authority authority FileProvider aplikasi (`<applicationId>.fileprovider`).
 */
class SafArtifactStore(
    context: Context,
    private val cacheRoot: File,
    private val authority: String,
    private val ioDispatcher: CoroutineDispatcher,
) : ArtifactStore {

    private val appContext: Context = context.applicationContext
    private val mutex = Mutex()

    override suspend fun list(treeUri: String): AppResult<List<ManagedArtifact>> = io {
        val tree = openTree(treeUri)
        val found = ArrayList<ManagedArtifact>()
        val outputRoot = tree.findFile(WorkspaceLayout.OUTPUT_DIR)
        if (outputRoot != null && outputRoot.isDirectory) {
            for (projectDir in outputRoot.listFiles()) {
                val projectId = projectDir.name ?: continue
                if (!projectDir.isDirectory || !HelperProtocol.isSafeFileName(projectId)) continue
                for (file in projectDir.listFiles()) {
                    val name = file.name ?: continue
                    val kind = ArtifactNames.kindOf(name) ?: continue
                    if (!file.isFile || !HelperProtocol.isSafeFileName(name)) continue
                    found.add(
                        ManagedArtifact(
                            projectId = projectId,
                            name = name,
                            kind = kind,
                            sizeBytes = file.length(),
                            modifiedMillis = file.lastModified(),
                            uri = file.uri.toString(),
                        ),
                    )
                }
            }
        }
        pruneStaging(found)
        found
    }

    override suspend fun inspect(treeUri: String, artifact: ManagedArtifact): AppResult<ArtifactDetails> = io {
        val staged = stageBlocking(treeUri, artifact)
        val validation = FileInputStream(staged.file).use { ArtifactValidator.validate(artifact.kind, it) }
        val metadata = if (validation == ArtifactValidation.VALID && artifact.kind == com.senzaproject.createapp.domain.builder.ArtifactKind.APK) {
            ApkMetadataReader.read(appContext, staged.file)
        } else {
            null
        }
        ArtifactDetails(validation = validation, sha256 = staged.sha256, apk = metadata)
    }

    override suspend fun stage(treeUri: String, artifact: ManagedArtifact): AppResult<StagedArtifact> = io {
        val staged = stageBlocking(treeUri, artifact)
        StagedArtifact(
            contentUri = FileProvider.getUriForFile(appContext, authority, staged.file).toString(),
            sizeBytes = staged.file.length(),
            sha256 = staged.sha256,
        )
    }

    override suspend fun export(
        treeUri: String,
        artifact: ManagedArtifact,
        destinationUri: String,
    ): AppResult<Long> = io {
        val source = findArtifactFile(treeUri, artifact)
        val expected = source.length()
        if (expected != artifact.sizeBytes) {
            throw IOException("Ukuran file berubah sejak daftar dimuat; muat ulang daftar")
        }
        val input = appContext.contentResolver.openInputStream(source.uri)
            ?: throw IOException("Tidak dapat membuka file untuk dibaca: ${artifact.name}")
        val output = appContext.contentResolver.openOutputStream(Uri.parse(destinationUri), "wt")
            ?: throw IOException("Tidak dapat membuka tujuan ekspor")
        val copied = input.use { source ->
            output.use { target ->
                val total = copyStream(source, target)
                target.flush()
                total
            }
        }
        if (copied != expected) throw IOException("Ekspor tidak lengkap: $copied dari $expected byte")
        copied
    }

    override suspend fun delete(treeUri: String, artifact: ManagedArtifact): AppResult<Unit> = io {
        val tree = openTree(treeUri)
        val projectDir = tree.findFile(WorkspaceLayout.OUTPUT_DIR)?.findFile(artifact.projectId)
        val file = projectDir?.findFile(artifact.name)
        if (projectDir == null || file == null || !file.isFile) {
            throw OperationException(OperationFailure.NOT_FOUND, "File tidak ditemukan: ${artifact.key}")
        }
        if (!file.delete()) throw IOException("Gagal menghapus: ${artifact.name}")

        // Salinan cache dan folder proyek yang kosong dibersihkan sebisanya; kegagalan di sini tidak penting.
        stagedFile(artifact).delete()
        if (projectDir.listFiles().isEmpty()) projectDir.delete()
    }

    // --- Pembantu ---

    private suspend fun <T> io(block: () -> T): AppResult<T> = withContext(ioDispatcher) {
        mutex.withLock { runCatchingApp(block) }
    }

    private class Staged(val file: File, val sha256: String)

    private fun stageBlocking(treeUri: String, artifact: ManagedArtifact): Staged {
        val target = stagedFile(artifact)
        val source = findArtifactFile(treeUri, artifact)
        val sourceSize = source.length()
        if (sourceSize != artifact.sizeBytes) {
            throw IOException("Ukuran file berubah sejak daftar dimuat; muat ulang daftar")
        }
        val modified = source.lastModified()

        // Pakai ulang salinan yang sama persis (ukuran dan waktu ubah sama).
        if (modified > 0L && target.isFile && target.length() == sourceSize && target.lastModified() == modified) {
            return Staged(target, sha256Of(target))
        }

        val directory = target.parentFile ?: throw IOException("Folder cache tidak valid")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Gagal membuat folder cache")
        }
        val temp = File(directory, artifact.name + PART_SUFFIX)
        val digest = MessageDigest.getInstance("SHA-256")
        val input = appContext.contentResolver.openInputStream(source.uri)
            ?: throw IOException("Tidak dapat membuka file untuk dibaca: ${artifact.name}")
        val copied = try {
            input.use { stream ->
                temp.outputStream().use { out -> copyStream(stream, out, digest) }
            }
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
        if (copied != sourceSize) {
            temp.delete()
            throw IOException("Salinan tidak lengkap: $copied dari $sourceSize byte")
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (modified > 0L) target.setLastModified(modified)
        return Staged(target, hex(digest.digest()))
    }

    private fun findArtifactFile(treeUri: String, artifact: ManagedArtifact): DocumentFile {
        requireSafe(artifact)
        val tree = openTree(treeUri)
        val file = tree.findFile(WorkspaceLayout.OUTPUT_DIR)?.findFile(artifact.projectId)?.findFile(artifact.name)
        if (file == null || !file.isFile) {
            throw OperationException(OperationFailure.NOT_FOUND, "File tidak ditemukan: ${artifact.key}")
        }
        return file
    }

    private fun stagedFile(artifact: ManagedArtifact): File {
        requireSafe(artifact)
        return File(File(cacheRoot, artifact.projectId), artifact.name)
    }

    private fun requireSafe(artifact: ManagedArtifact) {
        require(HelperProtocol.isSafeFileName(artifact.projectId) && HelperProtocol.isSafeFileName(artifact.name)) {
            "Nama artefak tidak valid"
        }
    }

    private fun openTree(treeUri: String): DocumentFile {
        val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
        if (tree == null || !tree.isDirectory) {
            throw OperationException(OperationFailure.INVALID_SOURCE, "Folder bersama tidak valid")
        }
        return tree
    }

    /** Membuang salinan cache yang file aslinya sudah tidak ada, termasuk sisa `.part`. */
    private fun pruneStaging(existing: List<ManagedArtifact>) {
        val keep = existing.map { it.key }.toHashSet()
        val projectDirs = cacheRoot.listFiles() ?: return
        for (projectDir in projectDirs) {
            if (!projectDir.isDirectory) continue
            for (file in projectDir.listFiles() ?: emptyArray()) {
                if (file.name.endsWith(PART_SUFFIX) || !keep.contains(projectDir.name + "/" + file.name)) file.delete()
            }
            if ((projectDir.listFiles() ?: emptyArray()).isEmpty()) projectDir.delete()
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { copyStream(it, NullOutputStream, digest) }
        return hex(digest.digest())
    }

    private fun copyStream(input: InputStream, output: OutputStream, digest: MessageDigest? = null): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            digest?.update(buffer, 0, count)
            total += count
        }
        return total
    }

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    private object NullOutputStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private companion object {
        const val PART_SUFFIX = ".part"
        const val BUFFER_BYTES = 64 * 1024
        const val HEX = "0123456789abcdef"
    }
}
