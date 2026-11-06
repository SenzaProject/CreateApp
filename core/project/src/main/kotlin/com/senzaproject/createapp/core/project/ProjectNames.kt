package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.MAX_PROJECT_NAME_LENGTH
import com.senzaproject.createapp.domain.project.METADATA_DIR_NAME
import com.senzaproject.createapp.domain.project.checkPackageName
import java.io.File
import java.io.IOException

private val FORBIDDEN = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

/**
 * Mengubah nama dari luar (mis. nama folder SAF) menjadi nama proyek yang valid:
 * karakter terlarang diganti `_`, dipangkas, dan dipotong. Memakai [fallback] bila hasilnya kosong.
 * Disisakan 8 karakter agar akhiran " (n)" tetap muat.
 */
internal fun sanitizeProjectName(raw: String?, fallback: String): String {
    val replaced = (raw ?: "").map { if (it.isISOControl() || it in FORBIDDEN) '_' else it }.joinToString("")
    val cleaned = replaced.trim().trimEnd('.', ' ').take(MAX_PROJECT_NAME_LENGTH - 8).trim()
    return if (cleaned.isEmpty() || cleaned == "." || cleaned.equals(METADATA_DIR_NAME, ignoreCase = true)) {
        fallback
    } else {
        cleaned
    }
}

private val PACKAGE_DECLARATION = Regex("""(?:namespace|applicationId)\s*=?\s*["']([A-Za-z0-9_.]+)["']""")
private const val PACKAGE_SCAN_BYTES = 64 * 1024

/**
 * Mencari package proyek Android dari `app/build.gradle.kts` atau `app/build.gradle`
 * (`namespace` atau `applicationId`). Mengembalikan string kosong bila tidak ditemukan atau tidak valid.
 */
internal fun detectPackageName(projectDir: File): String {
    for (candidate in listOf("app/build.gradle.kts", "app/build.gradle")) {
        val file = File(projectDir, candidate)
        if (!file.isFile) continue
        val text = try {
            file.inputStream().use { stream ->
                val buffer = ByteArray(PACKAGE_SCAN_BYTES)
                val read = stream.read(buffer)
                if (read > 0) String(buffer, 0, read, Charsets.UTF_8) else ""
            }
        } catch (e: IOException) {
            continue
        }
        val found = PACKAGE_DECLARATION.find(text)?.groupValues?.get(1)
        if (found != null && checkPackageName(found) == null) return found
    }
    return ""
}
