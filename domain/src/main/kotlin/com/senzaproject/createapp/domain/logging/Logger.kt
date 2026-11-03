package com.senzaproject.createapp.domain.logging

/**
 * Abstraksi logging. Implementasi Android ada di modul :core:common.
 * Jangan pernah mengirim password, token, atau isi keystore ke logger.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/** Logger yang tidak melakukan apa pun (untuk pengujian). */
object NoOpLogger : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun i(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}
