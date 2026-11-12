package com.senzaproject.createapp.core.builder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.senzaproject.createapp.domain.apk.ApkMetadata
import java.io.File

/**
 * Membaca metadata APK lewat PackageManager (sistem Android sendiri yang mengurai manifest), sehingga
 * nilai yang tampil adalah isi file, bukan tebakan dari nama. Mengembalikan null bila sistem tidak
 * bisa mengurai file itu.
 */
internal object ApkMetadataReader {

    fun read(context: Context, file: File): ApkMetadata? {
        val pm = context.packageManager
        val path = file.absolutePath

        val info = try {
            pm.getPackageArchiveInfo(path, 0)
        } catch (e: Exception) {
            null
        } ?: return null

        var label: String? = null
        var minSdk: Int? = null
        var targetSdk: Int? = null
        val app = info.applicationInfo
        if (app != null) {
            // Tanpa ini label dan ikon tidak bisa dimuat dari file arsip.
            app.sourceDir = path
            app.publicSourceDir = path
            label = try {
                app.loadLabel(pm).toString()
            } catch (e: Exception) {
                null
            }
            minSdk = app.minSdkVersion
            targetSdk = app.targetSdkVersion
        }

        return ApkMetadata(
            packageName = info.packageName ?: return null,
            label = label,
            versionName = info.versionName,
            versionCode = versionCodeOf(info),
            minSdk = minSdk,
            targetSdk = targetSdk,
            signerCount = signerCount(pm, path),
        )
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    /**
     * Jumlah penanda tangan yang terbaca. null = tidak dapat ditentukan (sistem menolak membaca
     * sertifikat, yang juga terjadi pada APK tanpa tanda tangan di sebagian versi Android); 0 = file
     * terbaca tetapi tanpa penanda tangan. Ini bukan verifikasi kriptografis (Tahap 8).
     */
    @Suppress("DEPRECATION")
    private fun signerCount(pm: PackageManager, path: String): Int? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signed = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = signed?.signingInfo
            if (signed == null || signing == null) null else (signing.apkContentsSigners?.size ?: 0)
        } else {
            val signed = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
            if (signed == null) null else (signed.signatures?.size ?: 0)
        }
    } catch (e: Exception) {
        null
    }
}
