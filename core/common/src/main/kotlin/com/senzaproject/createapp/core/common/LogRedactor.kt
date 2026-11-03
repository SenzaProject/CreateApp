package com.senzaproject.createapp.core.common

/**
 * Menyamarkan data sensitif sebelum ditulis ke log atau laporan crash.
 * Ini lapisan pengaman tambahan; kode tetap tidak boleh mengirim rahasia ke logger.
 */
object LogRedactor {

    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""gh[pousr]_[A-Za-z0-9]{20,}""") to "[REDACTED_TOKEN]",
        Regex("""github_pat_[A-Za-z0-9_]{20,}""") to "[REDACTED_TOKEN]",
        Regex(
            """(?i)\b(storePassword|keyPassword|password|passwd|pwd|token|secret)\b(\s*[=:]\s*)\S+""",
        ) to "\$1\$2***",
    )

    fun redact(text: String): String =
        rules.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
}
