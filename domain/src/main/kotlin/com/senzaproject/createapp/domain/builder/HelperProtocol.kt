package com.senzaproject.createapp.domain.builder

/** Tingkat hasil satu butir pemeriksaan lingkungan. */
enum class CheckLevel { OK, WARN, MISSING }

/** Pesan terstruktur dari helper Termux (baris berawalan `@@CA@`). */
sealed interface HelperMessage {
    data class Version(val version: Int) : HelperMessage
    data class Phase(val phase: BuildPhase) : HelperMessage
    data class LogFile(val path: String) : HelperMessage

    /** Apakah helper menulis log Gradle ke file bersama selama build (`--live`). */
    data class Live(val enabled: Boolean) : HelperMessage
    data class Exit(val code: Int) : HelperMessage
    data class Artifact(val kind: ArtifactKind, val sizeBytes: Long, val name: String) : HelperMessage
    data class Error(val error: HelperError) : HelperMessage
    data class Check(val id: String, val level: CheckLevel, val detail: String) : HelperMessage
    data class CancelResult(val killed: Boolean) : HelperMessage
    data object Cancelled : HelperMessage
    data object CleanOk : HelperMessage
    data object Done : HelperMessage
}

/**
 * Mengurai baris keluaran helper. Baris yang bukan penanda (log Gradle biasa) menghasilkan null.
 *
 * Penanda tidak dipercaya begitu saja: keberhasilan build tetap diperiksa lewat kode keluar proses
 * dan pemeriksaan file artefak oleh aplikasi, karena proyek pengguna bisa saja mencetak teks apa pun.
 */
object HelperProtocol {
    const val PREFIX = "@@CA@"
    const val VERSION = 2

    fun parse(line: String): HelperMessage? {
        if (!line.startsWith(PREFIX)) return null
        val body = line.substring(PREFIX.length)
        val equals = body.indexOf('=')
        if (equals <= 0) return null
        val key = body.substring(0, equals)
        val value = body.substring(equals + 1)
        return when (key) {
            "helper" -> value.toIntOrNull()?.let { HelperMessage.Version(it) }
            "phase" -> parsePhase(value)?.let { HelperMessage.Phase(it) }
            "log" -> if (value.isEmpty()) null else HelperMessage.LogFile(value)
            "live" -> when (value) {
                "1" -> HelperMessage.Live(enabled = true)
                "0" -> HelperMessage.Live(enabled = false)
                else -> null
            }
            "exit" -> value.toIntOrNull()?.let { HelperMessage.Exit(it) }
            "artifact" -> parseArtifact(value)
            "error" -> HelperMessage.Error(HelperError.fromCode(value))
            "check" -> parseCheck(value)
            "cancel" -> when (value) {
                "killed" -> HelperMessage.CancelResult(killed = true)
                "none" -> HelperMessage.CancelResult(killed = false)
                else -> null
            }
            "cancelled" -> if (value == "1") HelperMessage.Cancelled else null
            "clean" -> if (value == "ok") HelperMessage.CleanOk else null
            "done" -> if (value == "1") HelperMessage.Done else null
            else -> null
        }
    }

    private fun parsePhase(value: String): BuildPhase? = when (value) {
        "prepare" -> BuildPhase.PREPARE
        "wrapper" -> BuildPhase.WRAPPER
        "build" -> BuildPhase.BUILD
        "collect" -> BuildPhase.COLLECT
        else -> null
    }

    /** Format: `KIND|UKURAN|NAMA`; NAMA boleh berisi `|` karena hanya dua pemisah pertama yang dipakai. */
    private fun parseArtifact(value: String): HelperMessage? {
        val first = value.indexOf('|')
        if (first <= 0) return null
        val second = value.indexOf('|', first + 1)
        if (second <= first + 1) return null
        val kind = when (value.substring(0, first)) {
            "APK" -> ArtifactKind.APK
            "AAB" -> ArtifactKind.AAB
            else -> return null
        }
        val size = value.substring(first + 1, second).toLongOrNull() ?: return null
        val name = value.substring(second + 1)
        if (size < 0 || !isSafeFileName(name)) return null
        return HelperMessage.Artifact(kind, size, name)
    }

    /** Format: `ID|LEVEL|DETAIL`. */
    private fun parseCheck(value: String): HelperMessage? {
        val first = value.indexOf('|')
        if (first <= 0) return null
        val second = value.indexOf('|', first + 1)
        if (second <= first + 1) return null
        val level = when (value.substring(first + 1, second)) {
            "OK" -> CheckLevel.OK
            "WARN" -> CheckLevel.WARN
            "MISSING" -> CheckLevel.MISSING
            else -> return null
        }
        return HelperMessage.Check(value.substring(0, first), level, value.substring(second + 1))
    }

    /** Nama file artefak harus berupa nama biasa: tanpa path, tanpa `..`, tanpa karakter kontrol. */
    fun isSafeFileName(name: String): Boolean {
        if (name.isEmpty() || name == "." || name == "..") return false
        return name.none { it == '/' || it == '\\' || it.code < 32 }
    }
}
