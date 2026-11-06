package com.senzaproject.createapp.domain.result

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorMappingTest {

    @Test
    fun `OperationException dipetakan ke Operation dengan alasannya`() {
        val result = runCatchingApp<Unit> { throw OperationException(OperationFailure.NAME_TAKEN, "sudah ada") }
        val error = (result as AppResult.Failure).error as AppError.Operation
        assertEquals(OperationFailure.NAME_TAKEN, error.reason)
        assertEquals("sudah ada", error.message)
    }

    @Test
    fun `IOException ruang habis dipetakan ke LOW_STORAGE`() {
        val result = runCatchingApp<Unit> { throw IOException("write failed: ENOSPC (No space left on device)") }
        val error = (result as AppResult.Failure).error as AppError.Operation
        assertEquals(OperationFailure.LOW_STORAGE, error.reason)
    }

    @Test
    fun `IOException biasa tetap dipetakan ke Storage`() {
        val result = runCatchingApp<Unit> { throw IOException("gagal baca") }
        assertTrue((result as AppResult.Failure).error is AppError.Storage)
    }
}
