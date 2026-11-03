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

    /** Kesalahan lain yang tidak terklasifikasi. */
    data class Unknown(override val message: String, override val cause: Throwable? = null) : AppError
}

/** Memetakan [Throwable] ke [AppError]. Pembatalan coroutine tidak boleh dipetakan (lihat [runCatchingApp]). */
fun Throwable.toAppError(): AppError = when (this) {
    is SecurityException -> AppError.Permission(message ?: "Izin ditolak", this)
    is java.io.IOException -> AppError.Storage(message ?: "Kesalahan penyimpanan", this)
    is IllegalArgumentException -> AppError.Validation(message ?: "Input tidak valid", this)
    is UnsupportedOperationException -> AppError.Unsupported(message ?: "Operasi tidak didukung", this)
    else -> AppError.Unknown(message ?: this::class.java.simpleName, this)
}
