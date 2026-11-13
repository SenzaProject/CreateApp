package com.senzaproject.createapp.ui.builder

import android.content.Context
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.BuildFailure
import com.senzaproject.createapp.domain.builder.BuildPhase
import com.senzaproject.createapp.domain.builder.BuildStatus
import com.senzaproject.createapp.domain.builder.BuildType
import com.senzaproject.createapp.domain.builder.HelperError
import com.senzaproject.createapp.ui.terminal.toText

/** Teks yang dapat dilokalisasi untuk layar Build. Semua keputusan status dibuat di domain; di sini hanya bahasa. */

fun BuildType.labelRes(): Int = when (this) {
    BuildType.DEBUG -> R.string.bld_type_debug
    BuildType.RELEASE -> R.string.bld_type_release
    BuildType.BUNDLE -> R.string.bld_type_bundle
}

fun BuildStatus.labelRes(): Int = when (this) {
    BuildStatus.IDLE -> R.string.bld_status_idle
    BuildStatus.RUNNING -> R.string.bld_status_running
    BuildStatus.CANCELLING -> R.string.bld_status_cancelling
    BuildStatus.SUCCESS -> R.string.bld_status_success
    BuildStatus.FAILED -> R.string.bld_status_failed
    BuildStatus.CANCELLED -> R.string.bld_status_cancelled
}

fun BuildPhase.labelRes(): Int = when (this) {
    BuildPhase.SYNC -> R.string.bld_phase_sync
    BuildPhase.PREPARE -> R.string.bld_phase_prepare
    BuildPhase.WRAPPER -> R.string.bld_phase_wrapper
    BuildPhase.BUILD -> R.string.bld_phase_build
    BuildPhase.COLLECT -> R.string.bld_phase_collect
    BuildPhase.VERIFY -> R.string.bld_phase_verify
}

fun ArtifactValidation.labelRes(): Int = when (this) {
    ArtifactValidation.NOT_CHECKED -> R.string.bld_artifact_unchecked
    ArtifactValidation.VALID -> R.string.bld_artifact_valid
    ArtifactValidation.INVALID -> R.string.bld_artifact_invalid
    ArtifactValidation.MISSING -> R.string.bld_artifact_missing
}

fun HelperError.textRes(): Int = when (this) {
    HelperError.BAD_ARGS -> R.string.bld_helper_bad_args
    HelperError.NO_SOURCE -> R.string.bld_helper_no_source
    HelperError.NOT_GRADLE_PROJECT -> R.string.bld_helper_not_gradle
    HelperError.NO_JAVA -> R.string.bld_helper_no_java
    HelperError.NO_WRAPPER -> R.string.bld_helper_no_wrapper
    HelperError.WRAPPER_FAILED -> R.string.bld_helper_wrapper_failed
    HelperError.ALREADY_RUNNING -> R.string.bld_helper_already_running
    HelperError.COPY_FAILED -> R.string.bld_helper_copy_failed
    HelperError.NO_ARTIFACT -> R.string.bld_helper_no_artifact
    HelperError.UNKNOWN -> R.string.bld_helper_unknown
}

fun BuildFailure.toText(context: Context): String = when (this) {
    is BuildFailure.Runner -> failure.toText(context, detail)
    is BuildFailure.Sync -> context.getString(R.string.bld_fail_sync)
    is BuildFailure.Helper -> context.getString(error.textRes())
    is BuildFailure.GradleExit -> context.getString(R.string.bld_fail_gradle_exit, code)
    BuildFailure.IncompleteOutput -> context.getString(R.string.bld_fail_incomplete)
    is BuildFailure.ArtifactInvalid -> context.getString(R.string.bld_fail_artifact_invalid, names.joinToString(", "))
}

/** Teks untuk kode kegagalan yang tersimpan di riwayat (bentuk ringkas dari [BuildFailure.toText]). */
fun failureCodeText(context: Context, code: String?, detail: String?): String? {
    if (code == null) return null
    if (code == "GRADLE_EXIT") {
        val exit = detail?.toIntOrNull()
        return if (exit != null) context.getString(R.string.bld_fail_gradle_exit, exit) else context.getString(R.string.bld_status_failed)
    }
    if (code == "INCOMPLETE_OUTPUT") return context.getString(R.string.bld_fail_incomplete)
    if (code == "ARTIFACT_INVALID") return context.getString(R.string.bld_fail_artifact_invalid, detail.orEmpty())
    if (code == "SYNC") return context.getString(R.string.bld_fail_sync)
    if (code.startsWith("HELPER_")) {
        val error = HelperError.fromCode(code.removePrefix("HELPER_"))
        return context.getString(error.textRes())
    }
    return context.getString(R.string.bld_status_failed)
}

/** Nama butir pemeriksaan lingkungan dari helper; id tak dikenal ditampilkan apa adanya. */
fun toolchainItemLabel(context: Context, id: String): String {
    val res = when (id) {
        "arch" -> R.string.bld_item_arch
        "java" -> R.string.bld_item_java
        "wrapper" -> R.string.bld_item_wrapper
        "sdk" -> R.string.bld_item_sdk
        "platform" -> R.string.bld_item_platform
        "buildtools" -> R.string.bld_item_buildtools
        "aapt2" -> R.string.bld_item_aapt2
        "memory" -> R.string.bld_item_memory
        "storage" -> R.string.bld_item_storage
        "wakelock" -> R.string.bld_item_wakelock
        "source" -> R.string.bld_item_source
        else -> return id
    }
    return context.getString(res)
}

/** 3661000 -> "1:01:01"; 65000 -> "1:05"; 5000 -> "0:05". */
fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
}

fun BuildMessage.textRes(): Int = when (this) {
    BuildMessage.NEED_PROJECT -> R.string.bld_msg_need_project
    BuildMessage.NEED_TERMUX -> R.string.bld_msg_need_termux
    BuildMessage.NEED_FOLDER -> R.string.bld_msg_need_folder
    BuildMessage.FOLDER_UNSUPPORTED -> R.string.bld_msg_folder_unsupported
    BuildMessage.FOLDER_SAVED -> R.string.bld_msg_folder_saved
    BuildMessage.BUSY -> R.string.bld_msg_busy
    BuildMessage.PICKER_UNAVAILABLE -> R.string.bld_msg_picker_unavailable
    BuildMessage.CLEAN_DONE -> R.string.bld_msg_clean_done
    BuildMessage.CLEAN_BUSY -> R.string.bld_msg_clean_busy
    BuildMessage.CLEAN_FAILED -> R.string.bld_msg_clean_failed
}
