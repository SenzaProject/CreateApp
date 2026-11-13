package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppResult
import java.io.OutputStream

// Tahap 7 — APK Manager. Seluruh isi file ini Kotlin murni (tanpa Android) agar bisa diuji di JVM.
// Prinsip utama: daftar artefak berasal dari FILE YANG BENAR-BENAR ADA di folder bersama
// (`out/<id proyek>/`), bukan dari riwayat. Riwayat hanya dipakai untuk melengkapi keterangan
// (jenis build, waktu build, nama proyek) dan tidak pernah menambah entri.

/** Satu file hasil build yang benar-benar ada di folder bersama (`out/<id proyek>/<nama>`). */
data class StoredArtifact(
    val projectId: String,
    val name: String,
    val kind: ArtifactKind,
    /** Ukuran nyata file saat didaftar. */
    val sizeBytes: Long,
    /** Waktu ubah terakhir dari penyedia dokumen; 0 bila tidak dilaporkan. */
    val modifiedAtMillis: Long,
) {
    /** Identitas stabil antar pemuatan ulang: id proyek + nama file. */
    val key: String get() = "$projectId/$name"

    /** Kunci cache pemeriksaan: berubah bila file berganti (ukuran atau waktu ubah). */
    val fingerprint: String get() = "$projectId/$name|$sizeBytes|$modifiedAtMillis"
}

/**
 * Akses ke folder `out/` di folder bersama. Implementasi memakai Storage Access Framework
 * (`SafArtifactStore` di `:core:project`). Semua fungsi aman dipanggil dari coroutine mana pun.
 */
interface ArtifactStore {
    /** Seluruh APK/AAB yang ada di `out/<id proyek>/` untuk semua proyek. File lain diabaikan. */
    suspend fun list(treeUri: String): AppResult<List<StoredArtifact>>

    /** Memeriksa isi file (ZIP utuh + manifest). Tidak pernah melempar; hasil gagal dibaca = MISSING. */
    suspend fun validate(treeUri: String, artifact: StoredArtifact): ArtifactValidation

    /** Menghapus tepat satu file artefak. Folder proyek yang menjadi kosong ikut dihapus. */
    suspend fun delete(treeUri: String, artifact: StoredArtifact): AppResult<Unit>

    /**
     * Menyalin isi file ke [output] (tidak ditutup oleh fungsi ini). Mengembalikan jumlah byte yang
     * disalin. [onProgress] dipanggil dengan total byte yang sudah tersalin.
     */
    suspend fun copyTo(
        treeUri: String,
        artifact: StoredArtifact,
        output: OutputStream,
        onProgress: (Long) -> Unit,
    ): AppResult<Long>
}

/** Mengapa sebuah artefak tidak bisa dipasang langsung. */
enum class InstallBlock {
    /** AAB hanya untuk Google Play / bundletool, bukan untuk dipasang di ponsel. */
    NOT_APK,

    /** APK release tanpa tanda tangan ditolak Android; penandatanganan dikerjakan di Tahap 8. */
    UNSIGNED,

    /** Struktur file rusak atau tidak lengkap. */
    INVALID_FILE,

    /** Isi file belum selesai diperiksa. */
    NOT_CHECKED,

    /** File sudah tidak ada. */
    MISSING,
}

/** Keterangan build yang menghasilkan sebuah artefak (dari riwayat build). */
data class ArtifactBuildInfo(
    val recordId: String,
    val type: BuildType,
    val startedAtMillis: Long,
    val durationMillis: Long,
)

/** Aturan nama dan jenis artefak yang dipakai bersama oleh domain dan UI. */
object ArtifactRules {
    const val APK_MIME = "application/vnd.android.package-archive"
    const val AAB_MIME = "application/octet-stream"
    private const val MAX_EXPORT_NAME = 120

    fun mimeType(kind: ArtifactKind): String = if (kind == ArtifactKind.APK) APK_MIME else AAB_MIME

    /** APK release dari Gradle tanpa signingConfig diberi nama `*-unsigned.apk`. */
    fun looksUnsigned(kind: ArtifactKind, name: String): Boolean =
        kind == ArtifactKind.APK && name.contains("unsigned", ignoreCase = true)

    /** Nama jenis dari ekstensi file; null bila bukan `.apk` / `.aab`. */
    fun kindOf(fileName: String): ArtifactKind? = when {
        fileName.endsWith(".apk", ignoreCase = true) -> ArtifactKind.APK
        fileName.endsWith(".aab", ignoreCase = true) -> ArtifactKind.AAB
        else -> null
    }

    /** Id proyek hanya huruf, angka, `_` dan `-` (sama dengan aturan helper Termux). */
    fun isSafeProjectId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 80 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }

    /**
     * Nama file saran untuk ekspor: `<nama proyek>-<nama artefak>`. Karakter selain huruf/angka/`.`/`_`/`-`
     * diganti `_`; hasilnya selalu tetap berekstensi asli dan tidak melebihi batas panjang.
     */
    fun exportFileName(projectName: String?, artifactName: String): String {
        val extension = when (kindOf(artifactName)) {
            ArtifactKind.APK -> ".apk"
            ArtifactKind.AAB -> ".aab"
            null -> ""
        }
        val stem = artifactName.dropLast(extension.length)
        val project = projectName?.let { sanitize(it) }.orEmpty().trim('_', '-', '.')
        val joined = if (project.isEmpty() || stem.startsWith(project)) sanitize(stem) else sanitize("$project-$stem")
        val room = (MAX_EXPORT_NAME - extension.length).coerceAtLeast(1)
        val body = joined.take(room).trim('.').ifEmpty { "artefak" }
        return body + extension
    }

    private fun sanitize(text: String): String = buildString {
        for (ch in text) {
            val ok = ch.code < 128 && (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-')
            append(if (ok) ch else '_')
        }
    }
}

/** Satu baris di APK Manager: file nyata + keterangan dari riwayat + hasil pemeriksaan isi. */
data class ArtifactEntry(
    val stored: StoredArtifact,
    val projectName: String?,
    val build: ArtifactBuildInfo?,
    val validation: ArtifactValidation,
) {
    val key: String get() = stored.key
    val name: String get() = stored.name
    val kind: ArtifactKind get() = stored.kind
    val sizeBytes: Long get() = stored.sizeBytes
    val projectId: String get() = stored.projectId

    val isUnsigned: Boolean get() = ArtifactRules.looksUnsigned(stored.kind, stored.name)

    /** Waktu build: waktu ubah file bila ada, jika tidak waktu selesai build dari riwayat, jika tidak 0. */
    val builtAtMillis: Long
        get() = when {
            stored.modifiedAtMillis > 0L -> stored.modifiedAtMillis
            build != null -> build.startedAtMillis + build.durationMillis
            else -> 0L
        }

    /** Null bila APK ini boleh dicoba dipasang. Urutan pemeriksaan menentukan alasan yang ditampilkan. */
    val installBlock: InstallBlock?
        get() = when {
            validation == ArtifactValidation.MISSING -> InstallBlock.MISSING
            kind != ArtifactKind.APK -> InstallBlock.NOT_APK
            validation == ArtifactValidation.NOT_CHECKED -> InstallBlock.NOT_CHECKED
            validation == ArtifactValidation.INVALID -> InstallBlock.INVALID_FILE
            isUnsigned -> InstallBlock.UNSIGNED
            else -> null
        }

    val canInstall: Boolean get() = installBlock == null

    /** Bagikan/ekspor boleh selama file ada dan strukturnya tidak rusak (AAB dan APK belum bertanda tangan boleh). */
    val canShare: Boolean
        get() = validation == ArtifactValidation.VALID || validation == ArtifactValidation.NOT_CHECKED
}

data class ArtifactSummary(val count: Int, val totalBytes: Long)

/** Satu artefak di riwayat: apakah file dari build itu masih ada. */
data class HistoryArtifact(val name: String, val available: Boolean)

/** Satu build berhasil di riwayat beserta ketersediaan file-file yang dihasilkannya. */
data class ArtifactHistoryItem(val record: BuildRecord, val artifacts: List<HistoryArtifact>)

data class ArtifactSnapshot(
    val entries: List<ArtifactEntry>,
    val history: List<ArtifactHistoryItem>,
) {
    val summary: ArtifactSummary get() = ArtifactSummary(entries.size, entries.sumOf { it.sizeBytes })
}

object ArtifactCatalog {

    /**
     * Menggabungkan file nyata dengan riwayat. Hanya [stored] yang menjadi entri. Pemilik sebuah file
     * adalah build BERHASIL terbaru untuk proyek yang sama yang mencatat nama itu, karena build baru
     * selalu menghapus isi `out/<id proyek>/` lebih dulu.
     *
     * @param validations hasil pemeriksaan sebelumnya menurut [StoredArtifact.fingerprint]; file yang
     *   berubah (ukuran/waktu) otomatis dianggap belum diperiksa.
     */
    fun merge(
        stored: List<StoredArtifact>,
        records: List<BuildRecord>,
        projectNames: Map<String, String>,
        validations: Map<String, ArtifactValidation>,
    ): List<ArtifactEntry> {
        val entries = stored.map { file ->
            val owner = ownerOf(file, records)
            ArtifactEntry(
                stored = file,
                projectName = projectNames[file.projectId] ?: owner?.projectName,
                build = owner?.let { ArtifactBuildInfo(it.id, it.type, it.startedAtMillis, it.durationMillis) },
                validation = validations[file.fingerprint] ?: ArtifactValidation.NOT_CHECKED,
            )
        }
        return entries.sortedWith(
            compareByDescending<ArtifactEntry> { it.builtAtMillis }
                .thenBy { it.projectId }
                .thenBy { it.name },
        )
    }

    /**
     * Riwayat build berhasil yang menghasilkan artefak, terbaru lebih dulu. Sebuah artefak "tersedia"
     * hanya bila filenya ada DAN build itulah pemiliknya (bukan build yang lebih baru).
     */
    fun history(stored: List<StoredArtifact>, records: List<BuildRecord>): List<ArtifactHistoryItem> {
        return records
            .filter { it.status == BuildStatus.SUCCESS && it.artifactNames.isNotEmpty() }
            .sortedByDescending { it.startedAtMillis }
            .map { record ->
                ArtifactHistoryItem(
                    record = record,
                    artifacts = record.artifactNames.map { name ->
                        val file = stored.firstOrNull { it.projectId == record.projectId && it.name == name }
                        val available = file != null && ownerOf(file, records)?.id == record.id
                        HistoryArtifact(name, available)
                    },
                )
            }
    }

    private fun ownerOf(file: StoredArtifact, records: List<BuildRecord>): BuildRecord? =
        records
            .filter {
                it.status == BuildStatus.SUCCESS && it.projectId == file.projectId && file.name in it.artifactNames
            }
            .maxByOrNull { it.startedAtMillis }
}
