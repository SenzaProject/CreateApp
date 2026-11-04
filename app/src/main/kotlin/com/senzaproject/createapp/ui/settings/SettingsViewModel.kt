package com.senzaproject.createapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.createapp.domain.diagnostics.AppDiagnostics
import com.senzaproject.createapp.domain.diagnostics.GetAppDiagnosticsUseCase
import com.senzaproject.createapp.domain.logging.Logger
import com.senzaproject.createapp.domain.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val diagnostics: AppDiagnostics? = null,
)

class SettingsViewModel(
    private val getAppDiagnostics: GetAppDiagnosticsUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            when (val result = getAppDiagnostics()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, hasError = false, diagnostics = result.value)
                }

                is AppResult.Failure -> {
                    logger.e(TAG, "Gagal memuat diagnostik: ${result.error.message}", result.error.cause)
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
