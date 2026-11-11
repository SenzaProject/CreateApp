package com.senzaproject.createapp.domain.builder

enum class GradleOutcome { SUCCESS, FAILED }

const val DEFAULT_MAX_BUILD_ISSUES = 200

/**
 * Membaca log Gradle (mode `--console=plain`) baris demi baris: task yang berjalan, error/warning
 * beserta lokasinya, dan hasil akhir (`BUILD SUCCESSFUL/FAILED`).
 *
 * Penguraian bersifat "upaya terbaik" dan hanya MENAMPILKAN informasi dari log; status sukses/gagal
 * ditentukan oleh kode keluar proses (lihat [BuildRunTracker]), bukan oleh parser ini.
 */
class GradleLogParser(private val maxIssues: Int = DEFAULT_MAX_BUILD_ISSUES) {

    var currentTask: String? = null
        private set

    var taskCount: Int = 0
        private set

    var outcome: GradleOutcome? = null
        private set

    var duration: String? = null
        private set

    var issuesTruncated: Boolean = false
        private set

    private val issueList = ArrayList<BuildIssue>()
    private val seenKeys = HashSet<String>()

    /** 0 = biasa, 1 = menunggu baris pesan setelah "What went wrong", 2 = menunggu baris sebab "> ...". */
    private var whatWentWrong = 0

    val issues: List<BuildIssue> get() = issueList

    val errorCount: Int get() = issueList.count { it.severity == IssueSeverity.ERROR }

    val warningCount: Int get() = issueList.count { it.severity == IssueSeverity.WARNING }

    fun accept(line: String) {
        when (whatWentWrong) {
            1 -> {
                if (line.isBlank()) return
                whatWentWrong = 2
                addIssue(BuildIssue(IssueSeverity.ERROR, line.trim()))
                return
            }

            2 -> {
                whatWentWrong = 0
                val trimmed = line.trim()
                if (trimmed.startsWith("> ")) {
                    appendCauseToLastIssue(trimmed)
                    return
                }
            }
        }

        if (line.startsWith("> Task ")) {
            val rest = line.substring("> Task ".length).trim()
            val path = rest.substringBefore(' ')
            if (path.isNotEmpty()) {
                currentTask = path
                taskCount++
            }
            return
        }

        if (line == "* What went wrong:") {
            whatWentWrong = 1
            return
        }

        BUILD_RESULT.matchEntire(line)?.let { match ->
            outcome = if (match.groupValues[1] == "SUCCESSFUL") GradleOutcome.SUCCESS else GradleOutcome.FAILED
            duration = match.groupValues[2].trim()
            return
        }

        parseKotlin(line)?.let {
            addIssue(it)
            return
        }

        FILE_DIAGNOSTIC.matchEntire(line)?.let { match ->
            val severity = if (match.groupValues[4] == "error") IssueSeverity.ERROR else IssueSeverity.WARNING
            addIssue(
                BuildIssue(
                    severity = severity,
                    message = match.groupValues[5].trim(),
                    file = match.groupValues[1].trim(),
                    line = match.groupValues[2].toIntOrNull(),
                    column = match.groupValues[3].toIntOrNull(),
                ),
            )
        }
    }

    private fun parseKotlin(line: String): BuildIssue? {
        val severity = when {
            line.startsWith("e: ") -> IssueSeverity.ERROR
            line.startsWith("w: ") -> IssueSeverity.WARNING
            else -> return null
        }
        val body = line.substring(3).trim()
        KOTLIN_LOCATION.matchEntire(body)?.let { match ->
            return BuildIssue(
                severity = severity,
                message = match.groupValues[4].trim(),
                file = match.groupValues[1].removePrefix("file://"),
                line = match.groupValues[2].toIntOrNull(),
                column = match.groupValues[3].toIntOrNull(),
            )
        }
        KOTLIN_LOCATION_OLD.matchEntire(body)?.let { match ->
            return BuildIssue(
                severity = severity,
                message = match.groupValues[4].trim(),
                file = match.groupValues[1].removePrefix("file://"),
                line = match.groupValues[2].toIntOrNull(),
                column = match.groupValues[3].toIntOrNull(),
            )
        }
        if (body.isEmpty()) return null
        return BuildIssue(severity, body)
    }

    private fun addIssue(issue: BuildIssue) {
        val key = "${issue.severity}|${issue.file}|${issue.line}|${issue.column}|${issue.message}"
        if (!seenKeys.add(key)) return
        if (issueList.size >= maxIssues) {
            issuesTruncated = true
            return
        }
        issueList.add(issue)
    }

    private fun appendCauseToLastIssue(cause: String) {
        val lastIndex = issueList.size - 1
        if (lastIndex < 0) return
        val last = issueList[lastIndex]
        if (last.file != null) return
        issueList[lastIndex] = last.copy(message = last.message + " " + cause)
    }

    private companion object {
        val BUILD_RESULT = Regex("""BUILD (SUCCESSFUL|FAILED) in (.+)""")

        // Kotlin 2.x: "e: file:///path/File.kt:12:5 Pesan"
        val KOTLIN_LOCATION = Regex("""(.+?):(\d+):(\d+):?\s+(.*)""")

        // Kotlin lama: "e: /path/File.kt: (12, 5): Pesan"
        val KOTLIN_LOCATION_OLD = Regex("""(.+?): \((\d+), (\d+)\): (.*)""")

        // Java, AAPT2, dan alat lain: "/path/File.java:12: error: Pesan" atau "ERROR: /path/a.xml:12: AAPT: error: Pesan"
        val FILE_DIAGNOSTIC = Regex("""(?:ERROR: |WARNING: )?(\S.*?):(\d+)(?::(\d+))?: (?:AAPT: )?(error|warning): (.*)""")
    }
}
