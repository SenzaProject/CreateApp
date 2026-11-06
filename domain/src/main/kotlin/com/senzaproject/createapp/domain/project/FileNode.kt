package com.senzaproject.createapp.domain.project

/** Satu entri (file atau folder) di dalam proyek. */
data class FileNode(
    val name: String,
    /** Path relatif terhadap folder proyek, pemisah `/`. */
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

/** Jenis file yang dapat dibuat dari File Explorer (Tahap 3: Kotlin, XML, JSON, Gradle). */
enum class NewFileType(val extension: String) {
    KOTLIN("kt"),
    XML("xml"),
    JSON("json"),
    GRADLE("gradle.kts"),
}

/** Menambahkan ekstensi [type] ke [base] bila belum ada (tanpa membedakan huruf besar/kecil). */
fun fileNameFor(base: String, type: NewFileType): String {
    val suffix = "." + type.extension
    return if (base.endsWith(suffix, ignoreCase = true)) base else base + suffix
}

/**
 * Isi awal untuk file baru berdasarkan ekstensinya. [dir] adalah folder tempat file dibuat
 * (relatif terhadap proyek) dan dipakai untuk menurunkan `package` pada file Kotlin.
 */
fun initialContentFor(name: String, dir: String): String = when {
    name.endsWith(".kt", ignoreCase = true) -> kotlinInitialContent(name.dropLast(3), dir)
    name.endsWith(".xml", ignoreCase = true) -> "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
    name.endsWith(".json", ignoreCase = true) -> "{\n}\n"
    name.endsWith(".gradle.kts", ignoreCase = true) -> "// $name\n"
    name.endsWith(".gradle", ignoreCase = true) -> "// $name\n"
    else -> ""
}

private fun kotlinInitialContent(baseName: String, dir: String): String {
    val pkg = packageFromSourceDir(dir)
    val header = if (pkg != null) "package $pkg\n\n" else ""
    val body = if (isValidIdentifier(baseName)) "class $baseName\n" else ""
    return header + body
}

/**
 * Menurunkan package dari folder sumber standar, mis. `app/src/main/kotlin/com/contoh/app` -> `com.contoh.app`.
 * Mengembalikan null bila [dir] bukan di bawah `src/<set>/kotlin|java/` atau ada segmen yang bukan identifier.
 */
fun packageFromSourceDir(dir: String): String? {
    val parts = dir.split('/').filter { it.isNotEmpty() }
    val sourceRootIndex = parts.indexOfFirst { it == "kotlin" || it == "java" }
    if (sourceRootIndex < 2 || sourceRootIndex == parts.lastIndex) return null
    if (parts[sourceRootIndex - 2] != "src") return null
    val segments = parts.drop(sourceRootIndex + 1)
    return if (segments.all { isValidIdentifier(it) }) segments.joinToString(".") else null
}
