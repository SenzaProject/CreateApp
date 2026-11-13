package com.senzaproject.createapp.core.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.ArtifactValidator
import com.senzaproject.createapp.domain.builder.BuildArtifact
import com.senzaproject.createapp.domain.builder.BuildWorkspace
import com.senzaproject.createapp.domain.builder.SyncedWorkspace
import com.senzaproject.createapp.domain.builder.WorkspaceLayout
import com.senzaproject.createapp.domain.project.METADATA_DIR_NAME
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Folder bersama untuk build lewat Termux. Folder privat proyek Create App tidak bisa dibaca Termux
 * (UID berbeda), jadi proyek disalin ke folder yang dipilih pengguna lewat Storage Access Framework:
 * `src/<id proyek>` berisi sumber, `out/<id proyek>` diisi helper Termux dengan APK/AAB hasil build.
 */
class SafBuildWorkspace(
    context: Context,
    storage: ProjectStorage,
    ioDispatcher: CoroutineDispatcher,
) : BuildWorkspace {

    private val appContext: Context = context.applicationContext
    private val projectStorage = storage
    private val executor = StorageExecutor(storage, ioDispatcher)

    override suspend fun sync(projectId: String, treeUri: String): AppResult<SyncedWorkspace> =
        executor.lockedCancellable { checkCancelled ->
            val projectDir = projectStorage.projectDir(projectId)
            val tree = openTree(treeUri)
            if (!tree.canWrite()) throw SecurityException("Tidak ada izin menulis ke folder bersama")

            val sourceRoot = findOrCreateDirectory(tree, WorkspaceLayout.SOURCE_DIR)
            val existing = sourceRoot.findFile(projectId)
            if (existing != null && !existing.delete()) {
                throw IOException("Gagal menghapus salinan lama: $projectId")
            }
            val target = sourceRoot.createDirectory(projectId)
                ?: throw IOException("Gagal membuat folder tujuan: $projectId")
            try {
                val stats = TreeCopier.copyChildren(
                    source = FileTreeSource(projectDir),
                    sink = DocumentTreeSink(appContext, target),
                    limits = CopyLimits(skipDirectoryNames = SYNC_SKIPPED_DIRECTORIES),
                    checkCancelled = checkCancelled,
                )
                SyncedWorkspace(files = stats.files, bytes = stats.bytes)
            } catch (e: Throwable) {
                // Upaya terbaik: jangan tinggalkan salinan setengah jadi yang bisa ikut terbangun.
                target.delete()
                throw e
            }
        }

    override suspend fun verifyArtifacts(
        treeUri: String,
        projectId: String,
        reported: List<BuildArtifact>,
    ): AppResult<List<BuildArtifact>> = executor.unlocked {
        val tree = openTree(treeUri)
        val projectOutput = tree.findFile(WorkspaceLayout.OUTPUT_DIR)?.findFile(projectId)
        reported.map { artifact -> verifyOne(projectOutput, artifact) }
    }

    private fun verifyOne(directory: DocumentFile?, artifact: BuildArtifact): BuildArtifact {
        val file = directory?.findFile(artifact.name)
        if (file == null || !file.isFile) return artifact.copy(validation = ArtifactValidation.MISSING)
        if (file.length() != artifact.sizeBytes) return artifact.copy(validation = ArtifactValidation.INVALID)
        val input = appContext.contentResolver.openInputStream(file.uri)
            ?: return artifact.copy(validation = ArtifactValidation.MISSING)
        val result = input.use { ArtifactValidator.validate(artifact.kind, it) }
        return artifact.copy(validation = result)
    }

    private fun openTree(treeUri: String): DocumentFile {
        val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
        if (tree == null || !tree.isDirectory) {
            throw OperationException(OperationFailure.INVALID_SOURCE, "Folder bersama tidak valid")
        }
        return tree
    }

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile {
        val existing = parent.findFile(name)
        if (existing != null) {
            if (!existing.isDirectory) throw IOException("Bukan folder: $name")
            return existing
        }
        return parent.createDirectory(name) ?: throw IOException("Gagal membuat folder: $name")
    }

    private companion object {
        // "build" sengaja TIDAK dilewati: bisa saja nama paket sumber. Proyek di penyimpanan aplikasi
        // tidak berisi keluaran build karena build berjalan di Termux, bukan di folder proyek.
        val SYNC_SKIPPED_DIRECTORIES: Set<String> = setOf(METADATA_DIR_NAME, ".gradle", ".cxx")
    }
}
