package com.senzaproject.createapp.domain.diagnostics

import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.domain.module.ModuleStatus
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetAppDiagnosticsUseCaseTest {

    private val app = AppMetadata(
        appName = "Create App",
        packageName = "com.senzaproject.createapp",
        versionName = "0.1.0",
        versionCode = 1L,
        isDebug = true,
    )
    private val device = DeviceInfo(
        androidRelease = "14",
        sdkInt = 34,
        manufacturer = "Test",
        model = "Unit",
        supportedAbis = listOf("arm64-v8a"),
    )

    private class FakeRepository(private val result: AppResult<AppDiagnostics>) : DiagnosticsRepository {
        override suspend fun load(): AppResult<AppDiagnostics> = result
    }

    @Test
    fun `modul diurutkan berdasarkan tahap lalu id`() = runTest {
        val modules = listOf(
            ModuleInfo(":core:git", "Git", 9, ModuleStatus.PLANNED),
            ModuleInfo(":core:editor", "Editor", 4, ModuleStatus.PLANNED),
            ModuleInfo(":domain", "Domain", 1, ModuleStatus.READY),
            ModuleInfo(":data", "Data", 1, ModuleStatus.READY),
        )
        val useCase = GetAppDiagnosticsUseCase(
            FakeRepository(AppResult.Success(AppDiagnostics(app, device, modules))),
        )

        val result = useCase()

        val sortedIds = (result as AppResult.Success).value.modules.map { it.id }
        assertEquals(listOf(":data", ":domain", ":core:editor", ":core:git"), sortedIds)
    }

    @Test
    fun `kegagalan repository diteruskan apa adanya`() = runTest {
        val failure = AppResult.Failure(AppError.Storage("gagal"))
        val useCase = GetAppDiagnosticsUseCase(FakeRepository(failure))

        val result = useCase()

        assertTrue(result is AppResult.Failure)
        assertEquals("gagal", (result as AppResult.Failure).error.message)
    }
}
