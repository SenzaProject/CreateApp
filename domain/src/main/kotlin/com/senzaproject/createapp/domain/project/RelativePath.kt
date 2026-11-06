package com.senzaproject.createapp.domain.project

import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure

/**
 * Path relatif di dalam proyek memakai pemisah `/`. String kosong berarti folder akar proyek.
 * Path tidak boleh absolut, memuat `.` atau `..`, segmen kosong, backslash, atau karakter NUL.
 */
fun splitRelativePath(path: String): List<String> {
    if (path.isEmpty()) return emptyList()
    if (path.startsWith("/") || path.contains('\\') || path.contains('\u0000')) {
        throw OperationException(OperationFailure.OUTSIDE_PROJECT, "Path tidak valid: \"$path\"")
    }
    val parts = path.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
        throw OperationException(OperationFailure.OUTSIDE_PROJECT, "Path tidak valid: \"$path\"")
    }
    return parts
}

/** Folder induk dari [path]; string kosong bila [path] berada langsung di akar. */
fun parentOf(path: String): String = path.substringBeforeLast('/', "")

/** Nama terakhir dari [path]. */
fun nameOf(path: String): String = path.substringAfterLast('/')

/** Menggabungkan folder [dir] (boleh kosong) dengan [name]. */
fun joinPath(dir: String, name: String): String = if (dir.isEmpty()) name else "$dir/$name"
