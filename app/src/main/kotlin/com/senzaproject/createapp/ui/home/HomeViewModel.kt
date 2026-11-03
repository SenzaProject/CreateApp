package com.senzaproject.createapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.core.common.AppDispatchers
import com.senzaproject.createapp.core.common.CrashReporter
import com.senzaproject.createapp.domain.diagnostics.AppDiagnostics
import com.senzaproject.createapp.domain.diagnostics.GetAppDiagnosticsUseCase
import com.senzaproject.createapp.domain.logging.Logger
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val diagnostics: AppDiagnostics? = null,
    val lastCrash: String? = null,
)

class HomeViewModel(
    private val getAppDiagnostics: GetAppDiagnosticsUseCase,
    private val crashReporter: CrashReporter,
    private val dispatchers: AppDispatchers,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            val lastCrash = withContext(dispatchers.io) { crashReporter.readLastCrash() }
            when (val result = getAppDiagnostics()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasError = false,
                        diagnostics = result.value,
                        lastCrash = lastCrash,
                    )
                }

                is AppResult.Failure -> {
                    logger.e(TAG, "Gagal memuat diagnostik: ${result.error.message}", result.error.cause)
                    _uiState.update {
                        it.copy(isLoading = false, hasError = true, lastCrash = lastCrash)
                    }
                }
            }
        }
    }

    fun clearCrashReport() {
        viewModelScope.launch {
            withContext(dispatchers.io) { crashReporter.clear() }
            _uiState.update { it.copy(lastCrash = null) }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
