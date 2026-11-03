package com.senzaproject.createapp.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Abstraksi dispatcher agar pekerjaan berat (I/O, build) tidak berjalan di main thread dan mudah diuji. */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
