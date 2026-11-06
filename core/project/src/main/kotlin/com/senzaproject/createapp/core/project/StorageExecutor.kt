package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.runCatchingApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Menjalankan operasi penyimpanan yang memblokir di [dispatcher] I/O dan membungkus pengecualian
 * menjadi [AppResult.Failure]. Pembatalan coroutine tetap dilempar ulang (lihat [runCatchingApp]).
 */
internal class StorageExecutor(
    private val storage: ProjectStorage,
    private val dispatcher: CoroutineDispatcher,
) {
    /** Operasi baca: tanpa kunci. */
    suspend fun <T> unlocked(block: () -> T): AppResult<T> = withContext(dispatcher) {
        runCatchingApp(block)
    }

    /** Operasi yang mengubah penyimpanan: diserialkan dengan kunci bersama. */
    suspend fun <T> locked(block: () -> T): AppResult<T> = withContext(dispatcher) {
        storage.lock.withLock { runCatchingApp(block) }
    }

    /**
     * Seperti [locked], tetapi [block] menerima fungsi `checkCancelled` yang melempar
     * CancellationException bila coroutine dibatalkan. Dipakai untuk penyalinan panjang.
     */
    suspend fun <T> lockedCancellable(block: (checkCancelled: () -> Unit) -> T): AppResult<T> =
        withContext(dispatcher) {
            val context = coroutineContext
            storage.lock.withLock { runCatchingApp { block { context.ensureActive() } } }
        }
}
