package com.senzaproject.createapp.apk

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.senzaproject.createapp.domain.builder.ArtifactEntry
import com.senzaproject.createapp.domain.builder.ArtifactManager
import com.senzaproject.createapp.domain.builder.ArtifactRules
import com.senzaproject.createapp.domain.builder.HelperProtocol
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Salinan sementara artefak di cache aplikasi yang bisa diberikan ke aplikasi lain lewat FileProvider. */
class StagedArtifact(val file: File, val uri: Uri, val mimeType: String)

/**
 * Menyiapkan file untuk dipasang/dibagikan dan menyalinnya ke tujuan ekspor.
 *
 * Mengapa disalin ke cache dulu: file hasil build ada di folder bersama (Storage Access Framework) dan
 * pemasang paket maupun aplikasi lain tidak dijamin bisa membuka URI-nya. Salinan di `cache/apk-share/`
 * diberikan lewat FileProvider milik aplikasi sendiri, dan hanya satu salinan disimpan pada satu waktu.
 */
class ApkStager(
    context: Context,
    private val manager: ArtifactManager,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val appContext: Context = context.applicationContext
    private val directory = File(appContext.cacheDir, DIR_NAME)
    private val mutex = Mutex()
    private var stagedKey: String? = null

    /** Menyalin [entry] ke cache (atau memakai salinan yang sama bila file belum berubah). */
    suspend fun stage(
        treeUri: String,
        entry: ArtifactEntry,
        onProgress: (Long) -> Unit,
    ): AppResult<StagedArtifact> = mutex.withLock {
        withContext(ioDispatcher) {
            if (!HelperProtocol.isSafeFileName(entry.name)) {
                return@withContext AppResult.Failure(AppError.Validation("Nama artefak tidak valid"))
            }
            val target = File(directory, entry.name)
            val mime = ArtifactRules.mimeType(entry.kind)
            val fingerprint = entry.stored.fingerprint

            if (stagedKey == fingerprint && target.isFile && target.length() == entry.sizeBytes) {
                onProgress(entry.sizeBytes)
                return@withContext AppResult.Success(StagedArtifact(target, uriFor(target), mime))
            }

            clearDirectory()
            if (!directory.isDirectory && !directory.mkdirs()) {
                return@withContext AppResult.Failure(AppError.Storage("Gagal membuat folder sementara"))
            }
            if (directory.usableSpace < entry.sizeBytes + SPACE_MARGIN) {
                return@withContext AppResult.Failure(
                    AppError.Operation(OperationFailure.LOW_STORAGE, "Ruang penyimpanan tidak cukup untuk menyalin ${entry.name}"),
                )
            }

            var completed = false
            try {
                val result = FileOutputStream(target).use { out -> manager.copy(treeUri, entry, out, onProgress) }
                when (result) {
                    is AppResult.Failure -> result
                    is AppResult.Success -> {
                        completed = true
                        stagedKey = fingerprint
                        AppResult.Success(StagedArtifact(target, uriFor(target), mime))
                    }
                }
            } catch (e: java.io.IOException) {
                AppResult.Failure(
                    if (e.message?.contains("No space left", ignoreCase = true) == true) {
                        AppError.Operation(OperationFailure.LOW_STORAGE, e.message ?: "Penyimpanan penuh", e)
                    } else {
                        AppError.Storage(e.message ?: "Gagal menyalin file", e)
                    },
                )
            } finally {
                // Pembatalan atau kegagalan tidak boleh meninggalkan salinan setengah jadi.
                if (!completed) {
                    target.delete()
                    stagedKey = null
                }
            }
        }
    }

    /** Menyalin [entry] ke dokumen tujuan yang dipilih pengguna (Storage Access Framework). */
    suspend fun exportTo(
        treeUri: String,
        entry: ArtifactEntry,
        destination: Uri,
        onProgress: (Long) -> Unit,
    ): AppResult<Long> = withContext(ioDispatcher) {
        val output = try {
            appContext.contentResolver.openOutputStream(destination, "w")
        } catch (e: Exception) {
            null
        } ?: return@withContext AppResult.Failure(AppError.Storage("Tidak dapat menulis ke tujuan ekspor"))

        var result: AppResult<Long>? = null
        try {
            result = output.use { manager.copy(treeUri, entry, it, onProgress) }
            result
        } catch (e: java.io.IOException) {
            AppResult.Failure(AppError.Storage(e.message ?: "Gagal menulis ke tujuan ekspor", e))
        } finally {
            if (result !is AppResult.Success) {
                // Jangan tinggalkan file ekspor yang rusak atau kosong (upaya terbaik).
                try {
                    DocumentsContract.deleteDocument(appContext.contentResolver, destination)
                } catch (e: Exception) {
                    // diabaikan
                }
            }
        }
    }

    /** Menghapus salinan sementara (mis. saat aplikasi dibuka). */
    suspend fun clear() {
        mutex.withLock {
            withContext(ioDispatcher) { clearDirectory() }
        }
    }

    private fun clearDirectory() {
        stagedKey = null
        directory.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(appContext, appContext.packageName + AUTHORITY_SUFFIX, file)

    companion object {
        const val DIR_NAME = "apk-share"
        const val AUTHORITY_SUFFIX = ".fileprovider"
        private const val SPACE_MARGIN = 8L * 1024 * 1024
    }
}
