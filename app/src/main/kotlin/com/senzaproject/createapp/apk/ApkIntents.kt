package com.senzaproject.createapp.apk

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import com.senzaproject.createapp.domain.builder.ArtifactRules

/** Pembuat Intent untuk memasang, membagikan, dan membuka lokasi artefak. Tidak menjalankan apa pun sendiri. */
object ApkIntents {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** Android 8+ mewajibkan izin "pasang aplikasi tidak dikenal" per aplikasi pemasang. */
    fun canRequestInstalls(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Halaman pengaturan izin pasang aplikasi tidak dikenal untuk Create App. */
    fun unknownSourcesSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))

    fun install(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, ArtifactRules.APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun share(uri: Uri, mimeType: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Membuka folder di aplikasi File sistem. [documentId] berbentuk `primary:Download/...`. */
    fun openFolder(documentId: String): Intent {
        val uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
    }
}

/** Isi APK yang dibaca dari file itu sendiri (bukan dari riwayat). */
data class ApkInfo(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val label: String?,
    val minSdk: Int,
    val targetSdk: Int,
)

object ApkInfoReader {

    /** Null bila file tidak bisa diurai Android sebagai APK. */
    fun read(context: Context, file: java.io.File): ApkInfo? {
        val pm = context.packageManager
        val path = file.absolutePath
        val info = try {
            pm.getPackageArchiveInfo(path, 0)
        } catch (e: Exception) {
            null
        } ?: return null
        val app = info.applicationInfo
        var label: String? = null
        if (app != null) {
            app.sourceDir = path
            app.publicSourceDir = path
            label = try {
                app.loadLabel(pm).toString()
            } catch (e: Exception) {
                null
            }
        }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return ApkInfo(
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = code,
            label = label,
            minSdk = app?.minSdkVersion ?: 0,
            targetSdk = app?.targetSdkVersion ?: 0,
        )
    }
}
