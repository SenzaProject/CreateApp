package com.senzaproject.createapp.ui.apk

import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.builder.InstallBlock

/** Teks yang dapat dilokalisasi untuk APK Manager. Semua keputusan dibuat di domain; di sini hanya bahasa. */

fun InstallBlock.reasonRes(): Int = when (this) {
    InstallBlock.NOT_APK -> R.string.apk_block_not_apk
    InstallBlock.UNSIGNED -> R.string.apk_block_unsigned
    InstallBlock.INVALID_FILE -> R.string.apk_block_invalid
    InstallBlock.NOT_CHECKED -> R.string.apk_checking
    InstallBlock.MISSING -> R.string.apk_block_missing
}

fun ApkMessage.textRes(): Int = when (this) {
    ApkMessage.BLOCK_NOT_APK -> R.string.apk_block_not_apk
    ApkMessage.BLOCK_UNSIGNED -> R.string.apk_block_unsigned
    ApkMessage.BLOCK_INVALID -> R.string.apk_block_invalid
    ApkMessage.BLOCK_NOT_CHECKED -> R.string.apk_block_not_checked
    ApkMessage.BLOCK_MISSING -> R.string.apk_block_missing
    ApkMessage.SHARE_BLOCKED -> R.string.apk_share_blocked
    ApkMessage.EXPORT_DONE -> R.string.apk_export_done
    ApkMessage.DELETE_DONE -> R.string.apk_delete_done
    ApkMessage.NEED_INSTALL_PERMISSION -> R.string.apk_need_install_permission
    ApkMessage.NO_APP_FOR_ACTION -> R.string.apk_no_app
    ApkMessage.PATH_COPIED -> R.string.apk_path_copied
    ApkMessage.BUSY -> R.string.apk_busy_msg
    ApkMessage.DETAILS_UNREADABLE -> R.string.apk_details_unreadable
}

fun ApkBusyKind.labelRes(): Int = when (this) {
    ApkBusyKind.PREPARE_INSTALL -> R.string.apk_busy_install
    ApkBusyKind.PREPARE_SHARE -> R.string.apk_busy_share
    ApkBusyKind.EXPORT -> R.string.apk_busy_export
    ApkBusyKind.DETAILS -> R.string.apk_busy_details
}
