package com.senzaproject.createapp.domain.result

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppResultTest {

    @Test
    fun `map mengubah nilai pada Success`() {
        val result: AppResult<Int> = AppResult.Success(2)
        assertEquals(AppResult.Success(4), result.map { it * 2 })
    }

    @Test
    fun `map mempertahankan Failure`() {
        val failure: AppResult<Int> = AppResult.Failure(AppError.Unknown("x"))
        assertTrue(failure.map { it * 2 } is AppResult.Failure)
    }

    @Test
    fun `getOrNull mengembalikan nilai atau null`() {
        assertEquals(7, AppResult.Success(7).getOrNull())
        val failure: AppResult<Int> = AppResult.Failure(AppError.Unknown("x"))
        assertNull(failure.getOrNull())
    }

    @Test
    fun `runCatchingApp memetakan IOException ke Storage`() {
        val result = runCatchingApp<Unit> { throw IOException("disk penuh") }
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Storage)
        assertEquals("disk penuh", error.message)
    }

    @Test
    fun `runCatchingApp memetakan SecurityException ke Permission`() {
        val result = runCatchingApp<Unit> { throw SecurityException("ditolak") }
        assertTrue((result as AppResult.Failure).error is AppError.Permission)
    }

    @Test
    fun `runCatchingApp memetakan IllegalArgumentException ke Validation`() {
        val result = runCatchingApp<Unit> { throw IllegalArgumentException("nama kosong") }
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
    }

    @Test
    fun `runCatchingApp memetakan pengecualian lain ke Unknown`() {
        val result = runCatchingApp<Unit> { throw IllegalStateException("aneh") }
        assertTrue((result as AppResult.Failure).error is AppError.Unknown)
    }

    @Test
    fun `runCatchingApp melempar ulang CancellationException`() {
        try {
            runCatchingApp<Unit> { throw CancellationException("dibatalkan") }
            fail("CancellationException seharusnya dilempar ulang")
        } catch (e: CancellationException) {
            assertEquals("dibatalkan", e.message)
        }
    }
}
