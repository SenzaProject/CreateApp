package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import com.senzaproject.createapp.domain.result.getOrNull
import com.senzaproject.createapp.domain.result.runCatchingApp
import java.io.OutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pengelola artefak untuk APK Manager: memuat daftar dari file nyata, menggabungkannya dengan
 * riwayat, memeriksa isi file (dengan cache), menghapus, dan menyalin.
 *
 * Tidak menyimpan daftar sendiri: setiap [refresh] membaca ulang folder bersama, jadi tampilan
 * selalu mengikuti keadaan file sesungguhnya (termasuk file yang dihapus/diganti di luar aplikasi).
 */
class ArtifactManager(
    private val store: ArtifactStore,
    private val history: BuildHistoryStore,
) {
    private val cacheLock = Mutex()
    private val validations = HashMap<String, ArtifactValidation>()

    /** Membaca ulang folder `out/` dan riwayat. Hasil pemeriksaan lama dipakai ulang bila file tidak berubah. */
    suspend fun refresh(treeUri: String, projectNames: Map<String, String>): AppResult<ArtifactSnapshot> {
        val listed = when (val result = store.list(treeUri)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val records = runCatchingApp { history.load() }.getOrNull() ?: emptyList()
        val known = cacheLock.withLock {
            // Buang cache untuk file yang sudah tidak ada atau sudah berubah agar tidak membengkak.
            val live = listed.mapTo(HashSet()) { it.fingerprint }
            validations.keys.retainAll(live)
            HashMap(validations)
        }
        return AppResult.Success(
            ArtifactSnapshot(
                entries = ArtifactCatalog.merge(listed, records, projectNames, known),
                history = ArtifactCatalog.history(listed, records),
            ),
        )
    }

    /** Memeriksa isi file lalu mengembalikan entri yang sama dengan [ArtifactEntry.validation] terisi. */
    suspend fun verify(treeUri: String, entry: ArtifactEntry): ArtifactEntry {
        val cached = cacheLock.withLock { validations[entry.stored.fingerprint] }
        if (cached != null) return entry.copy(validation = cached)
        val result = store.validate(treeUri, entry.stored)
        if (result == ArtifactValidation.VALID || result == ArtifactValidation.INVALID) {
            cacheLock.withLock { validations[entry.stored.fingerprint] = result }
        }
        return entry.copy(validation = result)
    }

    /** Menghapus file. Nama dan id proyek diperiksa lagi di sini sebagai lapisan pengaman kedua. */
    suspend fun delete(treeUri: String, entry: ArtifactEntry): AppResult<Unit> {
        guard(entry.stored)?.let { return AppResult.Failure(it) }
        val result = store.delete(treeUri, entry.stored)
        if (result is AppResult.Success) {
            cacheLock.withLock { validations.remove(entry.stored.fingerprint) }
        }
        return result
    }

    /**
     * Menyalin file ke [output]. Dinyatakan berhasil hanya bila jumlah byte yang tersalin sama dengan
     * ukuran yang tampil di daftar; bila berbeda, file berubah di tengah jalan dan hasilnya tidak boleh dipakai.
     */
    suspend fun copy(
        treeUri: String,
        entry: ArtifactEntry,
        output: OutputStream,
        onProgress: (Long) -> Unit = {},
    ): AppResult<Long> {
        guard(entry.stored)?.let { return AppResult.Failure(it) }
        return when (val result = store.copyTo(treeUri, entry.stored, output, onProgress)) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                if (result.value == entry.sizeBytes) {
                    result
                } else {
                    AppResult.Failure(
                        AppError.Storage("Ukuran file berubah saat disalin: ${result.value} dari ${entry.sizeBytes} byte"),
                    )
                }
        }
    }

    private fun guard(file: StoredArtifact): AppError? {
        if (!ArtifactRules.isSafeProjectId(file.projectId) || !HelperProtocol.isSafeFileName(file.name) ||
            ArtifactRules.kindOf(file.name) != file.kind
        ) {
            return AppError.Operation(
                OperationFailure.INVALID_NAME,
                "Nama artefak tidak valid: ${file.key}",
                OperationException(OperationFailure.INVALID_NAME, file.key),
            )
        }
        return null
    }
}
