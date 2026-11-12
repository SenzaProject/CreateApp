package com.senzaproject.createapp.domain.apk

import com.senzaproject.createapp.domain.builder.ArtifactKind
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.WorkspaceLayout

/**
 * Satu file APK/AAB yang benar-benar ada di `out/<id proyek>` pada folder bersama. Daftar dibuat dari
 * isi folder saat ini (bukan dari ingatan riwayat), sehingga file yang sudah dihapus tidak pernah tampil.
 */
data class ManagedArtifact(
    val projectId: String,
    val name: String,
    val kind: ArtifactKind,
    val sizeBytes: Long,
    /** Waktu ubah terakhir; 0 bila penyedia penyimpanan tidak melaporkannya. */
    val modifiedMillis: Long,
    /** URI dokumen (Storage Access Framework). Hanya dipakai lapisan penyimpanan. */
    val uri: String,
) {
    /** Kunci unik di seluruh folder bersama. */
    val key: String get() = "$projectId/$name"

    /** Petunjuk dari nama file (`...-unsigned.apk`); pemeriksaan sebenarnya ada di [ApkMetadata.signerCount]. */
    val looksUnsigned: Boolean get() = kind == ArtifactKind.APK && name.contains("unsigned", ignoreCase = true)

    /** Path file di penyimpanan internal, sebagaimana dilihat Termux dan aplikasi File. */
    fun path(sharedRootPath: String): String = WorkspaceLayout.outputPath(sharedRootPath, projectId) + "/" + name
}

object ArtifactNames {
    /** Jenis artefak dari ekstensi nama file, atau null bila bukan APK/AAB. */
    fun kindOf(fileName: String): ArtifactKind? = when {
        fileName.endsWith(".apk", ignoreCase = true) -> ArtifactKind.APK
        fileName.endsWith(".aab", ignoreCase = true) -> ArtifactKind.AAB
        else -> null
    }

    /** Tipe MIME untuk berbagi/ekspor. */
    fun mimeType(kind: ArtifactKind): String = when (kind) {
        ArtifactKind.APK -> "application/vnd.android.package-archive"
        ArtifactKind.AAB -> "application/octet-stream"
    }
}

/**
 * Metadata APK yang dibaca sistem Android dari file APK itu sendiri (PackageManager), bukan dari nama file.
 *
 * [signerCount] bertipe nullable dengan sengaja: null berarti "tidak dapat ditentukan" (sistem menolak
 * membaca sertifikat), 0 berarti "tidak bertanda tangan", dan >0 berarti ada tanda tangan. Ini
 * pemeriksaan KEBERADAAN tanda tangan, bukan verifikasi kriptografis (Tahap 8).
 */
data class ApkMetadata(
    val packageName: String,
    val label: String?,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val signerCount: Int?,
)

/** Hasil pemeriksaan isi file artefak oleh aplikasi. */
data class ArtifactDetails(
    val validation: ArtifactValidation,
    val sha256: String?,
    /** Hanya untuk APK yang berhasil dibaca; null untuk AAB atau APK yang tidak bisa diurai. */
    val apk: ApkMetadata?,
)

/** Salinan artefak di cache privat aplikasi yang siap dibagikan/dipasang lewat FileProvider. */
data class StagedArtifact(
    val contentUri: String,
    val sizeBytes: Long,
    val sha256: String,
)
