package com.samcod3.meditrack.ai

import com.samcod3.meditrack.domain.model.LeafletSummary
import com.samcod3.meditrack.domain.model.ParsedMedication

/**
 * Result from AI-powered text extraction.
 * @param text The full extracted text (for display)
 * @param usedAI Whether Gemini Nano was used (true) or fallback logic (false)
 * @param name The medication name for CIMA search (brand + active ingredient, NO modifiers)
 * @param modifier Optional modifier (FORTE, FLAS, RAPID, RETARD) for filtering
 * @param dosage Dosage extracted (e.g., "600 mg") for filtering - applied automatically
 * @param presentation Pharmaceutical form (e.g., "comprimidos", "sobres") for filtering
 */
data class AIResult(
    val text: String,
    val usedAI: Boolean,
    val name: String = "",           // Name for search (brand + active)
    val modifier: String? = null,     // FORTE, FLAS, etc. - for filter chip
    val dosage: String? = null,       // For filter chip (auto-applied)
    val presentation: String? = null  // For filter chip
)

/**
 * Result from analyzing CIMA search results for unique filter options
 */
data class ResultsAnalysis(
    val dosages: List<String> = emptyList(),
    val presentations: List<String> = emptyList()
)

/**
 * Tool Calling Pattern - Actions that Gemini can request
 */
sealed class ToolAction {
    /**
     * Gemini requests a CIMA search with the extracted medication name
     */
    data class Search(
        val query: String,
        val modifier: String? = null,
        val scannedDosage: String? = null,
        val scannedPresentation: String? = null
    ) : ToolAction()
    
    /**
     * Gemini has analyzed results and provides filter options
     */
    data class Complete(
        val searchQuery: String,
        val filters: ResultsAnalysis,
        val autoApplyDosage: String? = null
    ) : ToolAction()
    
    /**
     * Gemini couldn't process the input
     */
    data class Error(val message: String) : ToolAction()
}

/**
 * Service interface for AI-powered text extraction from OCR results.
 * Implements Tool Calling pattern for unified flow.
 */
interface AIService {
    /**
     * Process OCR text with Tool Calling pattern.
     * Returns a ToolAction.Search that the app should execute.
     * @param ocrText Raw text from ML Kit Text Recognition
     * @return ToolAction.Search with query to execute, or ToolAction.Error
     */
    suspend fun processOcrText(ocrText: String): ToolAction
    
    /**
     * Continue processing after receiving CIMA results.
     * Analyzes results and returns filter options.
     * @param originalQuery The original search query
     * @param resultNames List of medication names from CIMA
     * @param scannedDosage Dosage from OCR (for auto-apply)
     * @return ToolAction.Complete with filters, or ToolAction.Error
     */
    suspend fun processSearchResults(
        originalQuery: String,
        resultNames: List<String>,
        scannedDosage: String? = null
    ): ToolAction
    
    // Legacy methods (kept for compatibility)
    suspend fun extractMedicationName(ocrText: String): AIResult
    suspend fun analyzeResults(medicationNames: List<String>): ResultsAnalysis
    
    /**
     * Structures a specific HTML content fragment (e.g. a section) into ContentBlocks.
     * Use this for chunk-based processing to respect Gemini Nano context limits.
     */
    suspend fun structureHtmlContent(htmlFragment: String): List<com.samcod3.meditrack.domain.model.ContentBlock>
    
    /**
     * Generates a quick summary of the leaflet (Indications, Dosage, Side Effects) from the full HTML.
     */
    suspend fun generateLeafletSummary(html: String): LeafletSummary?
    
    /**
     * Parse treatment text (from PDF OCR) to extract medications and schedules.
     * Used for importing treatment from exported PDFs.
     */
    suspend fun parseTreatmentText(text: String): List<ParsedMedication>
    
    fun isAvailable(): Boolean
}
