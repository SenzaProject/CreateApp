package com.senzaproject.createapp.domain.result

import kotlin.coroutines.cancellation.CancellationException

/** Hasil operasi: sukses membawa nilai, gagal membawa [AppError]. */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

/**
 * Menjalankan [block] dan membungkus pengecualian menjadi [AppResult.Failure].
 * [CancellationException] selalu dilempar ulang agar pembatalan coroutine tetap bekerja.
 */
inline fun <T> runCatchingApp(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.toAppError())
    }
