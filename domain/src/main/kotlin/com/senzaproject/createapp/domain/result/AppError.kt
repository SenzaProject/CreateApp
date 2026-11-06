package com.senzaproject.createapp.domain.result

/**
 * Kategori kegagalan yang dikenal aplikasi.
 *
 * [message] ditujukan untuk log/developer. Lapisan UI memetakan kategori ke teks
 * yang dapat dilokalisasi, bukan menampilkan [message] mentah kepada pengguna.
 */
sealed interface AppError {
    val message: String
    val cause: Throwable?

    /** Gagal membaca/menulis penyimpanan (file, folder, disk penuh). */
    data class Storage(override val message: String, override val cause: Throwable? = null) : AppError

    /** Izin Android atau izin akses file ditolak. */
    data class Permission(override val message: String, override val cause: Throwable? = null) : AppError

    /** Input pengguna atau argumen tidak valid. */
    data class Validation(override val message: String, override val cause: Throwable? = null) : AppError

    /** Operasi belum/tidak didukung di perangkat atau tahap ini. */
    data class Unsupported(override val message: String, override val cause: Throwable? = null) : AppError

    /** Operasi proyek/file gagal karena alasan yang dapat dijelaskan ke pengguna (lihat [OperationFailure]). */
    data class Operation(
        val reason: OperationFailure,
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** Kesalahan lain yang tidak terklasifikasi. */
    data class Unknown(override val message: String, override val cause: Throwable? = null) : AppError
}

/** Memetakan [Throwable] ke [AppError]. Pembatalan coroutine tidak boleh dipetakan (lihat [runCatchingApp]). */
fun Throwable.toAppError(): AppError = when (this) {
    is OperationException -> AppError.Operation(failure, message ?: failure.name, this)
    is SecurityException -> AppError.Permission(message ?: "Izin ditolak", this)
    is java.io.IOException ->
        if (isNoSpaceLeft()) {
            AppError.Operation(OperationFailure.LOW_STORAGE, message ?: "Penyimpanan penuh", this)
        } else {
            AppError.Storage(message ?: "Kesalahan penyimpanan", this)
        }
    is IllegalArgumentException -> AppError.Validation(message ?: "Input tidak valid", this)
    is UnsupportedOperationException -> AppError.Unsupported(message ?: "Operasi tidak didukung", this)
    else -> AppError.Unknown(message ?: this::class.java.simpleName, this)
}

private fun Throwable.isNoSpaceLeft(): Boolean {
    val text = message ?: return false
    return text.contains("No space left", ignoreCase = true) || text.contains("ENOSPC", ignoreCase = true)
}
