package com.senzaproject.createapp.core.common

import android.util.Log
import com.senzaproject.createapp.domain.logging.Logger

/** Logger Android: menulis ke Logcat dan (opsional) ke file. Semua pesan disamarkan lewat [LogRedactor]. */
class AndroidLogger(
    private val fileSink: LogFileSink? = null,
) : Logger {

    override fun d(tag: String, message: String) {
        val safe = LogRedactor.redact(message)
        Log.d(tag, safe)
        fileSink?.append("D", tag, safe, null)
    }

    override fun i(tag: String, message: String) {
        val safe = LogRedactor.redact(message)
        Log.i(tag, safe)
        fileSink?.append("I", tag, safe, null)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        val safe = LogRedactor.redact(message)
        Log.w(tag, safe, throwable)
        fileSink?.append("W", tag, safe, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        val safe = LogRedactor.redact(message)
        Log.e(tag, safe, throwable)
        fileSink?.append("E", tag, safe, throwable)
    }
}
