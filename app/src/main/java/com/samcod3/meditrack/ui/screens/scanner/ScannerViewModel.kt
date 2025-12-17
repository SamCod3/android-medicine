package com.samcod3.meditrack.ui.screens.scanner

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScannerUiState(
    val isScanning: Boolean = true,
    val scannedCode: String? = null,
    val error: String? = null
)

class ScannerViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    
    fun onBarcodeScanned(code: String) {
        _uiState.value = _uiState.value.copy(
            scannedCode = code,
            isScanning = false
        )
    }
    
    fun resetScanner() {
        _uiState.value = ScannerUiState()
    }
    
    fun onError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
