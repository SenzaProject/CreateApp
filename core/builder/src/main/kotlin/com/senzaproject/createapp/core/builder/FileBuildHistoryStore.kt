package com.senzaproject.createapp.core.builder

import com.senzaproject.createapp.core.common.LogRedactor
import com.senzaproject.createapp.domain.builder.BuildHistoryStore
import com.senzaproject.createapp.domain.builder.BuildRecord
import com.senzaproject.createapp.domain.builder.BuildRecordCodec
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Riwayat build di [directory]: `history.tsv` (satu baris per build, format [BuildRecordCodec]) dan
 * `logs/<id>.log`. Log disamarkan dengan [LogRedactor] sebelum ditulis, dan dipotong ke [maxLogChars]
 * karakter terakhir. Indeks ditulis lewat file sementara lalu dipindah sehingga tidak pernah setengah jadi.
 */
class FileBuildHistoryStore(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
    private val maxLogChars: Int = DEFAULT_MAX_LOG_CHARS,
) : BuildHistoryStore {

    private val mutex = Mutex()
    private val indexFile = File(directory, INDEX_NAME)
    private val logDirectory = File(directory, LOG_DIR_NAME)

    override suspend fun load(): List<BuildRecord> = withContext(ioDispatcher) {
        mutex.withLock { readIndex().reversed() }
    }

    override suspend fun append(record: BuildRecord, logText: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            require(isSafeId(record.id)) { "Id riwayat tidak valid" }
            if (!logDirectory.isDirectory && !logDirectory.mkdirs() && !logDirectory.isDirectory) {
                throw java.io.IOException("Gagal membuat folder log: ${logDirectory.path}")
            }
            File(logDirectory, record.id + LOG_SUFFIX).writeText(prepareLog(logText), Charsets.UTF_8)

            val records = ArrayList(readIndex())
            records.removeAll { it.id == record.id }
            records.add(record)
            while (records.size > maxRecords) {
                val dropped = records.removeAt(0)
                if (isSafeId(dropped.id)) File(logDirectory, dropped.id + LOG_SUFFIX).delete()
            }
            writeIndex(records)
        }
    }

    override suspend fun readLog(id: String): String? = withContext(ioDispatcher) {
        if (!isSafeId(id)) return@withContext null
        mutex.withLock {
            val file = File(logDirectory, id + LOG_SUFFIX)
            if (file.isFile) file.readText(Charsets.UTF_8) else null
        }
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            logDirectory.deleteRecursively()
            indexFile.delete()
        }
    }

    private fun prepareLog(text: String): String {
        val redacted = LogRedactor.redact(text)
        return if (redacted.length > maxLogChars) "… (dipotong)\n" + redacted.substring(redacted.length - maxLogChars) else redacted
    }

    private fun readIndex(): List<BuildRecord> {
        if (!indexFile.isFile) return emptyList()
        return indexFile.readLines(Charsets.UTF_8).mapNotNull { line ->
            if (line.isBlank()) null else BuildRecordCodec.decode(line)
        }
    }

    private fun writeIndex(records: List<BuildRecord>) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw java.io.IOException("Gagal membuat folder riwayat: ${directory.path}")
        }
        val temp = File(directory, "$INDEX_NAME.tmp")
        temp.writeText(records.joinToString("\n", postfix = "\n") { BuildRecordCodec.encode(it) }, Charsets.UTF_8)
        Files.move(temp.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun isSafeId(id: String): Boolean =
        id.isNotEmpty() && id.length <= MAX_ID_LENGTH && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private companion object {
        const val INDEX_NAME = "history.tsv"
        const val LOG_DIR_NAME = "logs"
        const val LOG_SUFFIX = ".log"
        const val DEFAULT_MAX_RECORDS = 30
        const val DEFAULT_MAX_LOG_CHARS = 1_000_000
        const val MAX_ID_LENGTH = 80
    }
}
