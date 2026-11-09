package com.senzaproject.createapp.domain.terminal

/**
 * Normalisasi path secara tekstual (tanpa menyentuh sistem file), dipakai oleh `cd`.
 * Mendukung `~`, `~/x`, path absolut, relatif, `.`, `..`, dan garis miring ganda.
 * Tautan simbolik tidak diselesaikan.
 */
object PathResolver {

    fun resolve(base: String, target: String, home: String): String {
        val raw = when {
            target == "~" -> home
            target.startsWith("~/") -> home.trimEnd('/') + "/" + target.substring(2)
            target.startsWith("/") -> target
            else -> base.trimEnd('/') + "/" + target
        }
        return normalize(raw)
    }

    fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(part)
            }
        }
        return "/" + parts.joinToString("/")
    }
}
