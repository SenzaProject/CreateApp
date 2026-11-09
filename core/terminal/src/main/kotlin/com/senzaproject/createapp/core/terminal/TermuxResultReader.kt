package com.senzaproject.createapp.core.terminal

import android.content.Intent
import android.os.Bundle

/** Membaca hasil Termux dari intent yang dikirim balik lewat PendingIntent. */
internal object TermuxResultReader {

    private const val RESULT_BUNDLE = "result"
    private const val STDOUT = "stdout"
    private const val STDOUT_ORIGINAL_LENGTH = "stdout_original_length"
    private const val STDERR = "stderr"
    private const val STDERR_ORIGINAL_LENGTH = "stderr_original_length"
    private const val EXIT_CODE = "exitCode"
    private const val ERR = "err"
    private const val ERRMSG = "errmsg"

    /** Null bila intent tidak membawa bundle hasil sama sekali. */
    fun fromIntent(intent: Intent): TermuxResultData? {
        val bundle = intent.getBundleExtra(RESULT_BUNDLE)
            ?: intent.extras?.takeIf { it.containsKey(EXIT_CODE) || it.containsKey(STDOUT) }
            ?: return null
        return TermuxResultData(
            stdout = bundle.getString(STDOUT),
            stderr = bundle.getString(STDERR),
            exitCode = bundle.intOrNull(EXIT_CODE),
            errCode = bundle.intOrNull(ERR),
            errMessage = bundle.getString(ERRMSG),
            stdoutOriginalLength = bundle.lengthOrNull(STDOUT_ORIGINAL_LENGTH),
            stderrOriginalLength = bundle.lengthOrNull(STDERR_ORIGINAL_LENGTH),
        )
    }

    private fun Bundle.intOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null

    /** Panjang asli bisa berupa String (menurut wiki) atau Int; keduanya diterima. */
    private fun Bundle.lengthOrNull(key: String): Int? {
        if (!containsKey(key)) return null
        return getString(key)?.toIntOrNull() ?: getInt(key, -1).takeIf { it >= 0 }
    }
}
