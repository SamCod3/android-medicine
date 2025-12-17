package com.samcod3.meditrack.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.domain.repository.DrugRepository
import com.samcod3.meditrack.domain.model.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Medication> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(
    private val drugRepository: DrugRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, results = emptyList()) }
            
            val result = drugRepository.searchMedications(query)
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    results = result.getOrElse { emptyList() },
                    error = result.exceptionOrNull()?.message
                ) 
            }
        }
    }
}
