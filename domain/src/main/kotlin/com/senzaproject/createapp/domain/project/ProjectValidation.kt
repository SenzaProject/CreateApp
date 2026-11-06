package com.senzaproject.createapp.domain.project

import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure

/** Folder metadata milik Create App di dalam setiap proyek. Tidak tampil di explorer dan tidak boleh diubah pengguna. */
const val METADATA_DIR_NAME = ".createapp"

const val MAX_PROJECT_NAME_LENGTH = 64
const val MAX_ENTRY_NAME_LENGTH = 100
private const val MAX_PACKAGE_LENGTH = 128

private val FORBIDDEN_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

private val PACKAGE_REGEX = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
private val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** Kata cadangan Java/Kotlin yang tidak boleh menjadi segmen package atau nama kelas otomatis. */
private val RESERVED_WORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
    "volatile", "while", "true", "false", "null", "fun", "val", "var", "when", "object", "is",
    "in", "as", "typealias", "typeof",
)

/**
 * Memeriksa nama file/folder/proyek. [raw] harus sudah dipangkas oleh pemanggil.
 * Mengembalikan alasan kegagalan, atau null bila nama valid.
 */
fun checkEntryName(raw: String, maxLength: Int = MAX_ENTRY_NAME_LENGTH): OperationFailure? = when {
    raw.isBlank() -> OperationFailure.INVALID_NAME
    raw != raw.trim() -> OperationFailure.INVALID_NAME
    raw.length > maxLength -> OperationFailure.INVALID_NAME
    raw == "." || raw == ".." -> OperationFailure.INVALID_NAME
    raw.any { it.isISOControl() || it in FORBIDDEN_NAME_CHARS } -> OperationFailure.INVALID_NAME
    raw.endsWith('.') -> OperationFailure.INVALID_NAME
    raw.equals(METADATA_DIR_NAME, ignoreCase = true) -> OperationFailure.RESERVED_NAME
    else -> null
}

fun checkProjectName(raw: String): OperationFailure? = checkEntryName(raw, MAX_PROJECT_NAME_LENGTH)

/** Package Android: minimal dua segmen, huruf kecil, bukan kata cadangan. */
fun checkPackageName(raw: String): OperationFailure? = when {
    raw.length > MAX_PACKAGE_LENGTH -> OperationFailure.INVALID_PACKAGE
    !PACKAGE_REGEX.matches(raw) -> OperationFailure.INVALID_PACKAGE
    raw.split('.').any { it in RESERVED_WORDS } -> OperationFailure.INVALID_PACKAGE
    else -> null
}

/** Melempar [OperationException] bila [raw] bukan nama entri yang valid; mengembalikan [raw] bila valid. */
fun requireValidEntryName(raw: String): String {
    val failure = checkEntryName(raw)
    if (failure != null) throw OperationException(failure, "Nama tidak valid: \"$raw\"")
    return raw
}

/**
 * Mengubah nama proyek menjadi id folder yang aman: hanya huruf, angka, `_`, dan `-`.
 * Hasil tidak pernah kosong dan tidak diawali/diakhiri `-`.
 */
fun slugify(name: String): String {
    val builder = StringBuilder()
    var lastWasDash = false
    for (c in name.trim()) {
        val keep = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'
        if (keep) {
            builder.append(c)
            lastWasDash = false
        } else if (!lastWasDash && builder.isNotEmpty()) {
            builder.append('-')
            lastWasDash = true
        }
    }
    val slug = builder.toString().trim('-').take(48).trim('-')
    return slug.ifEmpty { "proyek" }
}

/** Apakah [text] dapat dipakai sebagai identifier Kotlin sederhana (bukan kata cadangan). */
fun isValidIdentifier(text: String): Boolean =
    IDENTIFIER_REGEX.matches(text) && text !in RESERVED_WORDS
