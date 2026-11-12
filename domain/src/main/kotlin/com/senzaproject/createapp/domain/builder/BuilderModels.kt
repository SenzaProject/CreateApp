package com.senzaproject.createapp.domain.builder

import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.terminal.TerminalFailure

// Catatan: paket ini sengaja bernama "builder", bukan "build", karena .gitignore mengabaikan
// semua folder bernama "build/" dan folder itu akan hilang dari repository.

enum class ArtifactKind { APK, AAB }

/** Jenis build; [gradleTask] adalah satu-satunya task yang dijalankan dan sama dengan daftar izin di helper. */
enum class BuildType(val gradleTask: String, val artifactKind: ArtifactKind) {
    DEBUG("assembleDebug", ArtifactKind.APK),
    RELEASE("assembleRelease", ArtifactKind.APK),
    BUNDLE("bundleRelease", ArtifactKind.AAB),
}

enum class BuildStatus { IDLE, RUNNING, CANCELLING, SUCCESS, FAILED, CANCELLED }

/** Tahap yang sedang dikerjakan. SYNC dan VERIFY dikerjakan aplikasi; sisanya dilaporkan helper di Termux. */
enum class BuildPhase { SYNC, PREPARE, WRAPPER, BUILD, COLLECT, VERIFY }

enum class IssueSeverity { ERROR, WARNING }

data class BuildIssue(
    val severity: IssueSeverity,
    val message: String,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
)

/** Hasil pemeriksaan isi file artefak oleh aplikasi (bukan sekadar klaim helper). */
enum class ArtifactValidation { NOT_CHECKED, VALID, INVALID, MISSING }

data class BuildArtifact(
    val kind: ArtifactKind,
    val name: String,
    val sizeBytes: Long,
    val validation: ArtifactValidation = ArtifactValidation.NOT_CHECKED,
) {
    /** APK release tanpa tanda tangan tidak bisa dipasang; penandatanganan dikerjakan pada Tahap 8. */
    val isUnsigned: Boolean get() = kind == ArtifactKind.APK && name.contains("unsigned", ignoreCase = true)
}

/** Kode kesalahan dari helper Termux (lihat `createapp-build.sh`). */
enum class HelperError {
    BAD_ARGS,
    NO_SOURCE,
    NOT_GRADLE_PROJECT,
    NO_JAVA,
    NO_WRAPPER,
    WRAPPER_FAILED,
    ALREADY_RUNNING,
    COPY_FAILED,
    NO_ARTIFACT,
    UNKNOWN,
    ;

    companion object {
        fun fromCode(code: String): HelperError =
            entries.firstOrNull { it.name == code && it != UNKNOWN } ?: UNKNOWN
    }
}

/** Alasan sebuah build gagal. Teks untuk pengguna dibuat di lapisan UI. */
sealed interface BuildFailure {
    /** Perintah ke Termux tidak bisa dijalankan atau tidak berhasil (izin, Termux hilang, waktu habis, dll). */
    data class Runner(val failure: TerminalFailure, val detail: String?) : BuildFailure

    /** Proyek gagal disalin ke folder bersama. */
    data class Sync(val error: AppError) : BuildFailure

    /** Helper menolak atau gagal sebelum/sesudah Gradle (Java tidak ada, wrapper gagal, dll). */
    data class Helper(val error: HelperError) : BuildFailure

    /** Gradle selesai dengan kode keluar bukan nol. */
    data class GradleExit(val code: Int) : BuildFailure

    /** Perintah selesai tetapi penanda akhir dari helper tidak lengkap, jadi hasilnya tidak bisa dipercaya. */
    data object IncompleteOutput : BuildFailure

    /** File artefak tidak ada atau bukan APK/AAB yang valid menurut pemeriksaan aplikasi. */
    data class ArtifactInvalid(val names: List<String>) : BuildFailure
}

/** Kode singkat yang stabil untuk riwayat build; teks ditentukan UI. */
val BuildFailure.code: String
    get() = when (this) {
        is BuildFailure.Runner -> "RUNNER_" + failure.name
        is BuildFailure.Sync -> "SYNC"
        is BuildFailure.Helper -> "HELPER_" + error.name
        is BuildFailure.GradleExit -> "GRADLE_EXIT"
        BuildFailure.IncompleteOutput -> "INCOMPLETE_OUTPUT"
        is BuildFailure.ArtifactInvalid -> "ARTIFACT_INVALID"
    }

/** Satu permintaan build. [sharedRootPath] adalah path folder bersama yang terlihat oleh Termux. */
data class BuildRequest(
    val projectId: String,
    val projectName: String,
    val type: BuildType,
    val sharedTreeUri: String,
    val sharedRootPath: String,
)

/** Keadaan build yang ditampilkan UI. */
data class BuildState(
    val status: BuildStatus = BuildStatus.IDLE,
    val request: BuildRequest? = null,
    val phase: BuildPhase? = null,
    val currentTask: String? = null,
    val tasksSeen: Int = 0,
    val issues: List<BuildIssue> = emptyList(),
    val issuesTruncated: Boolean = false,
    val artifacts: List<BuildArtifact> = emptyList(),
    val logLines: List<String> = emptyList(),
    val droppedLogLines: Int = 0,
    val failure: BuildFailure? = null,
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long = 0L,
    val gradleDuration: String? = null,
    val exitCode: Int? = null,
    val noResponseYet: Boolean = false,
    val outputTruncated: Boolean = false,
    /** True bila log Gradle sedang/sudah dibaca langsung dari file bersama (bukan hanya setelah build selesai). */
    val liveLog: Boolean = false,
    /** True bila pembatalan hanya berhenti menunggu: build mungkin masih berjalan di Termux. */
    val mayStillRun: Boolean = false,
) {
    val isActive: Boolean get() = status == BuildStatus.RUNNING || status == BuildStatus.CANCELLING
}

/** Ringkasan satu build untuk riwayat. */
data class BuildRecord(
    val id: String,
    val projectId: String,
    val projectName: String,
    val type: BuildType,
    val status: BuildStatus,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val tasksSeen: Int,
    val errorCount: Int,
    val warningCount: Int,
    val artifactNames: List<String>,
    val failureCode: String?,
    val failureDetail: String?,
)
