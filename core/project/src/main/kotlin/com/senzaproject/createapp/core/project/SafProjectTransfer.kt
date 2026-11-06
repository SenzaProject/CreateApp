package com.senzaproject.createapp.core.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.senzaproject.createapp.domain.project.METADATA_DIR_NAME
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectTemplate
import com.senzaproject.createapp.domain.project.ProjectTransferRepository
import com.senzaproject.createapp.domain.project.slugify
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Impor/ekspor proyek lewat Storage Access Framework (SAF): pengguna memilih folder lewat
 * pemilih sistem, sehingga aplikasi tidak memerlukan izin penyimpanan luas. Impor menyalin isi
 * folder ke penyimpanan aplikasi (lewat folder staging lalu dipindahkan), sehingga proyek tetap ada
 * setelah aplikasi ditutup dan tidak bergantung pada izin SAF yang bisa dicabut.
 */
class SafProjectTransfer(
    context: Context,
    private val storage: ProjectStorage,
    ioDispatcher: CoroutineDispatcher,
) : ProjectTransferRepository {

    private val appContext: Context = context.applicationContext
    private val executor = StorageExecutor(storage, ioDispatcher)

    override suspend fun importFromTree(treeUri: String): AppResult<Project> =
        executor.lockedCancellable { checkCancelled ->
            val tree = openTree(treeUri)
            if (!tree.canRead()) throw SecurityException("Tidak ada izin membaca folder yang dipilih")
            storage.ensureRoot()
            storage.requireFreeSpace(MIN_IMPORT_FREE_BYTES)

            val displayName = storage.uniqueDisplayName(sanitizeProjectName(tree.name, FALLBACK_IMPORT_NAME))
            val finalDir = File(storage.root, storage.uniqueId(slugify(displayName)))
            val staging = storage.newStagingDir()
            try {
                if (!staging.mkdir()) throw IOException("Gagal membuat folder sementara: ${staging.path}")
                TreeCopier.copyChildren(
                    source = DocumentTreeSource(appContext, tree),
                    sink = FileTreeSink(staging),
                    limits = CopyLimits(skipDirectoryNames = IMPORT_SKIPPED_DIRECTORIES),
                    checkCancelled = checkCancelled,
                )
                storage.writeMeta(
                    staging,
                    ProjectMeta(
                        name = displayName,
                        packageName = detectPackageName(staging),
                        favorite = false,
                        template = ProjectTemplate.EMPTY,
                    ),
                )
                moveDirectory(staging, finalDir)
            } catch (e: Throwable) {
                // Jangan tinggalkan sisa impor setengah jadi.
                staging.deleteRecursively()
                throw e
            }
            storage.notifyChanged()
            storage.readProject(finalDir)
        }

    override suspend fun exportToTree(projectId: String, treeUri: String): AppResult<Int> =
        executor.lockedCancellable { checkCancelled ->
            val projectDir = storage.projectDir(projectId)
            val meta = storage.readMeta(projectDir)
            val tree = openTree(treeUri)
            if (!tree.canWrite()) throw SecurityException("Tidak ada izin menulis ke folder yang dipilih")

            val folderName = uniqueChildName(tree, sanitizeProjectName(meta.name, FALLBACK_EXPORT_NAME))
            val target = tree.createDirectory(folderName)
                ?: throw IOException("Gagal membuat folder tujuan: $folderName")
            try {
                val stats = TreeCopier.copyChildren(
                    source = FileTreeSource(projectDir),
                    sink = DocumentTreeSink(appContext, target),
                    limits = CopyLimits(skipDirectoryNames = setOf(METADATA_DIR_NAME)),
                    checkCancelled = checkCancelled,
                )
                stats.files
            } catch (e: Throwable) {
                // Upaya terbaik: bersihkan hasil ekspor yang tidak lengkap di folder tujuan.
                target.delete()
                throw e
            }
        }

    private fun openTree(treeUri: String): DocumentFile {
        val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
        if (tree == null || !tree.isDirectory) {
            throw OperationException(OperationFailure.INVALID_SOURCE, "Folder yang dipilih tidak valid")
        }
        return tree
    }

    private fun uniqueChildName(parent: DocumentFile, base: String): String {
        if (parent.findFile(base) == null) return base
        for (index in 2..MAX_NAME_ATTEMPTS) {
            val candidate = "$base ($index)"
            if (parent.findFile(candidate) == null) return candidate
        }
        throw IOException("Tidak dapat menentukan nama folder tujuan yang unik")
    }

    private fun moveDirectory(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        }
    }

    private companion object {
        const val MIN_IMPORT_FREE_BYTES = 32L * 1024 * 1024
        const val MAX_NAME_ATTEMPTS = 1000
        const val FALLBACK_IMPORT_NAME = "Proyek Impor"
        const val FALLBACK_EXPORT_NAME = "Proyek"
    }
}
