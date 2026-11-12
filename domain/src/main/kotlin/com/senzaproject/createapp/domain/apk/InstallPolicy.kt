package com.senzaproject.createapp.domain.apk

import com.senzaproject.createapp.domain.builder.ArtifactKind
import com.senzaproject.createapp.domain.builder.ArtifactValidation

/** Alasan sebuah artefak tidak boleh dipasang sekarang. */
enum class InstallBlock {
    /** AAB tidak bisa dipasang langsung; harus diubah menjadi APK (bundletool) atau diunggah ke Play Console. */
    NOT_APK,

    /** File bukan APK utuh menurut pemeriksaan aplikasi (terpotong, tanpa manifest, dll). */
    INVALID_FILE,

    /** APK tidak bertanda tangan; Android menolaknya. Penandatanganan dikerjakan pada Tahap 8. */
    UNSIGNED,

    /** Aplikasi ini belum diizinkan memasang aplikasi lain (izin "sumber tidak dikenal"). */
    NEED_INSTALL_PERMISSION,
}

/**
 * Aturan satu-satunya untuk tombol Pasang: tombol hanya berhasil bila pemasangan memang mungkin, dan bila
 * tidak, pengguna mendapat alasan yang tepat. Murni dan dapat diuji.
 */
object InstallPolicy {

    /**
     * @param details hasil [ArtifactStore.inspect]; null bila belum/ tidak dapat diperiksa.
     * @param canRequestInstalls hasil `PackageManager.canRequestPackageInstalls()`.
     * @return null bila boleh dipasang.
     */
    fun check(
        artifact: ManagedArtifact,
        details: ArtifactDetails?,
        canRequestInstalls: Boolean,
    ): InstallBlock? {
        if (artifact.kind != ArtifactKind.APK) return InstallBlock.NOT_APK
        if (details != null && details.validation != ArtifactValidation.VALID) return InstallBlock.INVALID_FILE
        if (isUnsigned(artifact, details)) return InstallBlock.UNSIGNED
        if (!canRequestInstalls) return InstallBlock.NEED_INSTALL_PERMISSION
        return null
    }

    /**
     * Tidak bertanda tangan bila sistem melaporkan 0 penanda tangan. Bila sistem tidak bisa menentukan
     * (null), hanya nama file yang menjadi petunjuk; kita tidak memblokir APK hanya karena tebakan.
     */
    fun isUnsigned(artifact: ManagedArtifact, details: ArtifactDetails?): Boolean {
        val signers = details?.apk?.signerCount
        return if (signers != null) signers == 0 else artifact.looksUnsigned
    }
}
