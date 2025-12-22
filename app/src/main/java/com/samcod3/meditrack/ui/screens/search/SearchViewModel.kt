package com.samcod3.meditrack.ui.screens.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.ai.AIService
import com.samcod3.meditrack.ai.ToolAction
import com.samcod3.meditrack.domain.repository.DrugRepository
import com.samcod3.meditrack.domain.model.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Available filter options extracted from CIMA results
 */
data class AvailableFilters(
    val dosages: List<String> = emptyList(),
    val presentations: List<String> = emptyList()
)

data class SearchUiState(
    val query: String = "",
    val results: List<Medication> = emptyList(),    // Filtered results shown to user
    val allResults: List<Medication> = emptyList(), // Original unfiltered results from CIMA
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeFilters: List<String> = emptyList(),   // Currently active filter tokens
    val availableFilters: AvailableFilters = AvailableFilters(), // Dynamic filter options
    val queryTooGeneric: Boolean = false  // True if single word query returns too many results
)

class SearchViewModel(
    private val drugRepository: DrugRepository,
    private val aiService: AIService? = null  // Optional AI service for analyzing results
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    /**
     * Clear all state - call when leaving screen
     */
    fun clearState() {
        _uiState.value = SearchUiState()
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }
    
    /**
     * Set active filter tokens (from chips) and apply local filtering
     */
    fun setActiveFilters(tokens: List<String>) {
        _uiState.update { state ->
            val filtered = applyFilters(state.allResults, tokens)
            state.copy(
                activeFilters = tokens,
                results = filtered
            )
        }
    }
    
    /**
     * Apply chip-based filters to results
     * Each result must contain ALL active tokens in its name
     */
    private fun applyFilters(allResults: List<Medication>, tokens: List<String>): List<Medication> {
        if (tokens.isEmpty()) return allResults
        if (allResults.isEmpty()) return emptyList()
        
        val filtered = allResults.filter { medication ->
            tokens.all { token ->
                medication.name.contains(token, ignoreCase = true)
            }
        }
        
        Log.d("SearchViewModel", "Local filter: ${allResults.size} → ${filtered.size} by tokens: $tokens")
        
        // If filter removes all results, return all results
        return if (filtered.isNotEmpty()) filtered else allResults
    }

    /**
     * Search medications in CIMA. Results are stored and analyzed for filter options.
     * Uses Tool Calling pattern for Gemini analysis.
     */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, results = emptyList(), allResults = emptyList()) }
            
            val result = drugRepository.searchMedications(query)
            val allResults = result.getOrElse { emptyList() }
            
            // Apply current filters to new results
            val activeFilters = _uiState.value.activeFilters
            val filteredResults = applyFilters(allResults, activeFilters)
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    allResults = allResults,
                    results = filteredResults,
                    error = result.exceptionOrNull()?.message
                ) 
            }
            
            // Check if query is too generic (single word with many results)
            val queryWords = query.split(Regex("\\s+")).filter { 
                it.length > 2 && !it.matches(Regex("\\d+\\s*(mg|g|ml|mcg)", RegexOption.IGNORE_CASE))
            }
            val isTooGeneric = queryWords.size == 1 && allResults.size > 30
            
            if (isTooGeneric) {
                _uiState.update { it.copy(queryTooGeneric = true, availableFilters = AvailableFilters()) }
            } else {
                _uiState.update { it.copy(queryTooGeneric = false) }
            }
            
            // Use Tool Calling to analyze results and get dynamic filter options
            if (allResults.size > 1 && allResults.size <= 50 && aiService != null && !isTooGeneric) {
                try {
                    val medicationNames = allResults.map { it.name }
                    val scannedDosage = _uiState.value.activeFilters.firstOrNull()
                    
                    when (val toolResult = aiService.processSearchResults(query, medicationNames, scannedDosage)) {
                        is ToolAction.Complete -> {
                            Log.d("SearchViewModel", "Tool Complete: filters=${toolResult.filters}")
                            
                            _uiState.update { 
                                it.copy(
                                    availableFilters = AvailableFilters(
                                        dosages = toolResult.filters.dosages,
                                        presentations = toolResult.filters.presentations
                                    )
                                ) 
                            }
                            
                            // Auto-apply scanned dosage if present
                            toolResult.autoApplyDosage?.let { dosage ->
                                if (!_uiState.value.activeFilters.contains(dosage)) {
                                    setActiveFilters(listOf(dosage))
                                }
                            }
                        }
                        is ToolAction.Error -> {
                            Log.w("SearchViewModel", "Tool Error: ${toolResult.message}")
                        }
                        else -> { /* Ignore other actions */ }
                    }
                } catch (e: Exception) {
                    Log.e("SearchViewModel", "Error in Tool Calling", e)
                }
            }
        }
    }
}

