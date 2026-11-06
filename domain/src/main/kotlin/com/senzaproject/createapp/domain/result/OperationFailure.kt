package com.senzaproject.createapp.domain.result

/**
 * Alasan kegagalan operasi proyek/file yang dapat dijelaskan ke pengguna.
 * Lapisan UI memetakan setiap alasan ke teks yang dapat dilokalisasi.
 */
enum class OperationFailure {
    /** Nama kosong, terlalu panjang, berspasi di tepi, atau mengandung karakter terlarang. */
    INVALID_NAME,

    /** Nama dicadangkan sistem (mis. folder metadata proyek). */
    RESERVED_NAME,

    /** Package name tidak sesuai aturan Android/Kotlin. */
    INVALID_PACKAGE,

    /** Sudah ada proyek lain dengan nama yang sama. */
    NAME_TAKEN,

    /** Proyek, file, atau folder tidak ditemukan. */
    NOT_FOUND,

    /** File atau folder dengan nama itu sudah ada di lokasi tujuan. */
    ALREADY_EXISTS,

    /** Path mencoba keluar dari folder proyek. */
    OUTSIDE_PROJECT,

    /** File atau kumpulan file melebihi batas yang didukung. */
    TOO_LARGE,

    /** File bukan teks UTF-8 yang valid (mis. biner). */
    NOT_TEXT,

    /** Ruang penyimpanan tidak cukup. */
    LOW_STORAGE,

    /** Sumber impor/ekspor tidak valid (bukan folder, tidak bisa dibaca/ditulis). */
    INVALID_SOURCE,
}

/** Pengecualian domain yang membawa [failure]; dipetakan ke `AppError.Operation` oleh [toAppError]. */
class OperationException(
    val failure: OperationFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
