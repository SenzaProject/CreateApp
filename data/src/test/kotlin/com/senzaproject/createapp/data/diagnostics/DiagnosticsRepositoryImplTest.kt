package com.senzaproject.createapp.data.diagnostics

import com.senzaproject.createapp.domain.diagnostics.AppMetadata
import com.senzaproject.createapp.domain.diagnostics.DeviceInfo
import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsRepositoryImplTest {

    private val metadata = AppMetadata("Create App", "com.senzaproject.createapp", "0.1.0", 1L, true)
    private val modules = listOf(ModuleInfo(":data", "Data", 1, ModuleStatus.READY))
    private val device = DeviceInfo("14", 34, "Test", "Unit", listOf("arm64-v8a"))

    @Test
    fun `load menggabungkan metadata, perangkat, dan modul`() = runTest {
        val repository = DiagnosticsRepositoryImpl(
            metadata = metadata,
            deviceInfoSource = object : DeviceInfoSource {
                override fun read(): DeviceInfo = device
            },
            modules = modules,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = repository.load()

        val diagnostics = (result as AppResult.Success).value
        assertEquals(metadata, diagnostics.app)
        assertEquals(device, diagnostics.device)
        assertEquals(modules, diagnostics.modules)
    }

    @Test
    fun `kegagalan membaca perangkat menjadi Failure`() = runTest {
        val repository = DiagnosticsRepositoryImpl(
            metadata = metadata,
            deviceInfoSource = object : DeviceInfoSource {
                override fun read(): DeviceInfo = throw SecurityException("ditolak")
            },
            modules = modules,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = repository.load()

        assertTrue((result as AppResult.Failure).error is AppError.Permission)
    }
}
