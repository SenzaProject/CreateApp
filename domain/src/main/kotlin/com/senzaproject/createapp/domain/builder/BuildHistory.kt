package com.senzaproject.createapp.domain.builder

/** Penyimpanan riwayat build beserta log tiap build. Implementasi file ada di `:core:builder`. */
interface BuildHistoryStore {
    /** Riwayat terbaru lebih dulu. */
    suspend fun load(): List<BuildRecord>

    /** Menambah catatan dan menyimpan [logText]. Catatan paling lama dibuang bila melebihi batas. */
    suspend fun append(record: BuildRecord, logText: String)

    /** Log tersimpan untuk [id], atau null bila tidak ada. */
    suspend fun readLog(id: String): String?

    suspend fun clear()
}

/** Pengaturan build yang disimpan: folder bersama pilihan pengguna. */
interface BuildSettingsStore {
    var sharedTreeUri: String?
}

/**
 * Format teks satu baris per catatan: kolom dipisah tab, dengan `\`, tab, dan baris baru di-escape.
 * Sengaja tanpa JSON agar tidak bergantung pada library dan bisa diuji di JVM murni.
 */
object BuildRecordCodec {
    private const val SEPARATOR = '\t'
    private const val LIST_SEPARATOR = '\u001F'
    private const val FIELD_COUNT = 13

    fun encode(record: BuildRecord): String = listOf(
        record.id,
        record.projectId,
        record.projectName,
        record.type.name,
        record.status.name,
        record.startedAtMillis.toString(),
        record.durationMillis.toString(),
        record.tasksSeen.toString(),
        record.errorCount.toString(),
        record.warningCount.toString(),
        record.artifactNames.joinToString(LIST_SEPARATOR.toString()),
        record.failureCode.orEmpty(),
        record.failureDetail.orEmpty(),
    ).joinToString(SEPARATOR.toString()) { escape(it) }

    /** Null bila baris rusak; baris rusak dilewati, bukan membuat seluruh riwayat gagal dibaca. */
    fun decode(line: String): BuildRecord? {
        val fields = splitFields(line)
        if (fields.size != FIELD_COUNT) return null
        val type = BuildType.entries.firstOrNull { it.name == fields[3] } ?: return null
        val status = BuildStatus.entries.firstOrNull { it.name == fields[4] } ?: return null
        val started = fields[5].toLongOrNull() ?: return null
        val duration = fields[6].toLongOrNull() ?: return null
        val tasks = fields[7].toIntOrNull() ?: return null
        val errors = fields[8].toIntOrNull() ?: return null
        val warnings = fields[9].toIntOrNull() ?: return null
        if (fields[0].isEmpty()) return null
        val names = if (fields[10].isEmpty()) emptyList() else fields[10].split(LIST_SEPARATOR)
        return BuildRecord(
            id = fields[0],
            projectId = fields[1],
            projectName = fields[2],
            type = type,
            status = status,
            startedAtMillis = started,
            durationMillis = duration,
            tasksSeen = tasks,
            errorCount = errors,
            warningCount = warnings,
            artifactNames = names,
            failureCode = fields[11].ifEmpty { null },
            failureDetail = fields[12].ifEmpty { null },
        )
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '\t' -> out.append("\\t")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /** Memecah di tab yang tidak di-escape lalu membuka escape tiap kolom. */
    private fun splitFields(line: String): List<String> {
        val fields = ArrayList<String>()
        val current = StringBuilder()
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            if (ch == '\\') {
                if (index + 1 >= line.length) return emptyList()
                when (line[index + 1]) {
                    '\\' -> current.append('\\')
                    't' -> current.append('\t')
                    'n' -> current.append('\n')
                    'r' -> current.append('\r')
                    else -> return emptyList()
                }
                index += 2
            } else if (ch == SEPARATOR) {
                fields.add(current.toString())
                current.setLength(0)
                index += 1
            } else {
                current.append(ch)
                index += 1
            }
        }
        fields.add(current.toString())
        return fields
    }
}
