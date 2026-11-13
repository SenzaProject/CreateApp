package com.senzaproject.createapp.core.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.senzaproject.createapp.domain.builder.ArtifactRules
import com.senzaproject.createapp.domain.builder.ArtifactStore
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.ArtifactValidator
import com.senzaproject.createapp.domain.builder.BuildLiveLogSource
import com.senzaproject.createapp.domain.builder.HelperProtocol
import com.senzaproject.createapp.domain.builder.LiveLog
import com.senzaproject.createapp.domain.builder.StoredArtifact
import com.senzaproject.createapp.domain.builder.WorkspaceLayout
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import com.senzaproject.createapp.domain.result.runCatchingApp
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Membaca, memeriksa, menghapus, dan menyalin APK/AAB di `out/<id proyek>/` pada folder bersama
 * (Storage Access Framework). Tidak pernah mengasumsikan path file: semua lewat [DocumentFile].
 */
class SafArtifactStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : ArtifactStore {

    private val appContext: Context = context.applicationContext

    override suspend fun list(treeUri: String): AppResult<List<StoredArtifact>> = withContext(ioDispatcher) {
        runCatchingApp {
            val tree = openTree(treeUri)
            val outDir = tree.findFile(WorkspaceLayout.OUTPUT_DIR)
            if (outDir == null || !outDir.isDirectory) {
                emptyList()
            } else {
                val found = ArrayList<StoredArtifact>()
                for (projectDir in outDir.listFiles()) {
                    val projectId = projectDir.name ?: continue
                    if (!projectDir.isDirectory || !ArtifactRules.isSafeProjectId(projectId)) continue
                    for (file in projectDir.listFiles()) {
                        val name = file.name ?: continue
                        if (!file.isFile || !HelperProtocol.isSafeFileName(name)) continue
                        val kind = ArtifactRules.kindOf(name) ?: continue
                        found.add(
                            StoredArtifact(
                                projectId = projectId,
                                name = name,
                                kind = kind,
                                sizeBytes = file.length(),
                                modifiedAtMillis = file.lastModified(),
                            ),
                        )
                    }
                }
                found
            }
        }
    }

    override suspend fun validate(treeUri: String, artifact: StoredArtifact): ArtifactValidation =
        withContext(ioDispatcher) {
            try {
                val file = locate(treeUri, artifact) ?: return@withContext ArtifactValidation.MISSING
                // Ukuran berbeda dari saat didaftar berarti file diganti (mis. build baru): jangan dinilai VALID.
                if (file.length() != artifact.sizeBytes) return@withContext ArtifactValidation.INVALID
                val input = appContext.contentResolver.openInputStream(file.uri)
                    ?: return@withContext ArtifactValidation.MISSING
                input.use { ArtifactValidator.validate(artifact.kind, it) }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                ArtifactValidation.MISSING
            }
        }

    override suspend fun delete(treeUri: String, artifact: StoredArtifact): AppResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingApp {
                val tree = openTree(treeUri)
                val projectDir = tree.findFile(WorkspaceLayout.OUTPUT_DIR)?.findFile(artifact.projectId)
                val file = projectDir?.findFile(artifact.name)
                if (projectDir == null || file == null || !file.isFile) {
                    throw OperationException(OperationFailure.NOT_FOUND, "Artefak tidak ditemukan: ${artifact.key}")
                }
                if (!file.delete()) throw IOException("Gagal menghapus: ${artifact.key}")
                // Folder proyek yang sudah kosong ikut dibersihkan (upaya terbaik; kegagalan diabaikan).
                if (projectDir.listFiles().isEmpty()) projectDir.delete()
                Unit
            }
        }

    override suspend fun copyTo(
        treeUri: String,
        artifact: StoredArtifact,
        output: OutputStream,
        onProgress: (Long) -> Unit,
    ): AppResult<Long> = withContext(ioDispatcher) {
        runCatchingApp {
            val file = locate(treeUri, artifact)
                ?: throw OperationException(OperationFailure.NOT_FOUND, "Artefak tidak ditemukan: ${artifact.key}")
            val input = appContext.contentResolver.openInputStream(file.uri)
                ?: throw IOException("Tidak dapat membuka file: ${artifact.key}")
            input.use { stream -> copyStream(stream, output, onProgress) }
        }
    }

    private suspend fun copyStream(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit): Long {
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            onProgress(total)
        }
        output.flush()
        return total
    }

    private fun locate(treeUri: String, artifact: StoredArtifact): DocumentFile? {
        if (!ArtifactRules.isSafeProjectId(artifact.projectId) || !HelperProtocol.isSafeFileName(artifact.name)) return null
        val file = openTree(treeUri).findFile(WorkspaceLayout.OUTPUT_DIR)?.findFile(artifact.projectId)?.findFile(artifact.name)
        return if (file != null && file.isFile) file else null
    }

    private fun openTree(treeUri: String): DocumentFile {
        val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
        if (tree == null || !tree.isDirectory) {
            throw OperationException(OperationFailure.INVALID_SOURCE, "Folder bersama tidak valid")
        }
        return tree
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
    }
}

/**
 * Membaca `live/<id proyek>.log` yang ditulis helper Termux selama build (lihat [BuildLiveLogSource]).
 * Hanya membaca; tidak pernah menulis atau menghapus di folder bersama.
 */
class SafBuildLiveLog(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : BuildLiveLogSource {

    private val appContext: Context = context.applicationContext

    override suspend fun read(treeUri: String, projectId: String, maxBytes: Int): String? = withContext(ioDispatcher) {
        if (!ArtifactRules.isSafeProjectId(projectId) || maxBytes < 1024) return@withContext null
        val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
        if (tree == null || !tree.isDirectory) return@withContext null
        val file = tree.findFile(WorkspaceLayout.LIVE_DIR)?.findFile(projectId + WorkspaceLayout.LIVE_SUFFIX)
        if (file == null || !file.isFile) return@withContext null
        val length = file.length()
        val input = appContext.contentResolver.openInputStream(file.uri) ?: return@withContext null
        input.use { stream -> LiveLog.readHeadAndTail(stream, length, maxBytes) }
    }

}
