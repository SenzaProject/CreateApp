package com.senzaproject.createapp.core.terminal

import android.content.Context
import android.content.pm.PackageManager
import com.senzaproject.createapp.domain.terminal.TermuxEnvironment
import com.senzaproject.createapp.domain.terminal.TermuxStatus

/**
 * Mendeteksi Termux: terpasang atau tidak, dan apakah izin `com.termux.permission.RUN_COMMAND`
 * sudah diberikan. Deteksi paket butuh `<queries>` di manifest (Android 11+).
 */
class AndroidTermuxEnvironment(private val context: Context) : TermuxEnvironment {

    @Suppress("DEPRECATION")
    override fun status(): TermuxStatus {
        val info = try {
            context.packageManager.getPackageInfo(TermuxPaths.PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return TermuxStatus.NotInstalled
        }
        val granted =
            context.checkSelfPermission(TermuxPaths.PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED
        return if (granted) TermuxStatus.Ready(info.versionName) else TermuxStatus.PermissionMissing
    }
}
