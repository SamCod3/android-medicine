package com.samcod3.meditrack.ai

import android.util.Log
import com.samcod3.meditrack.domain.model.ContentBlock
import com.samcod3.meditrack.domain.model.LeafletSummary
import com.samcod3.meditrack.domain.model.ParsedMedication

/**
 * Hybrid AI Service that tries Gemini Nano first, then falls back to regex.
 * This avoids blocking during DI initialization by deferring the check to runtime.
 */
class HybridAIService(
    private val geminiService: GeminiNanoService,
    private val fallbackService: FallbackOcrService
) : AIService {
    
    companion object {
        private const val TAG = "HybridAIService"
    }
    
    override suspend fun extractMedicationName(ocrText: String): AIResult {
        // Try Gemini Nano first
        val geminiResult = geminiService.extractMedicationName(ocrText)
        
        // If Gemini Nano returned a result with AI, use it
        if (geminiResult.usedAI && geminiResult.text.isNotBlank()) {
            Log.d(TAG, "Using Gemini Nano result: ${geminiResult.text}")
            return geminiResult
        }
        
        // Otherwise fall back to regex
        val fallbackResult = fallbackService.extractMedicationName(ocrText)
        Log.d(TAG, "Using fallback result: ${fallbackResult.text}")
        return fallbackResult
    }
    
    /**
     * Analyze CIMA results - try Gemini first, fallback returns empty
     */
    override suspend fun analyzeResults(medicationNames: List<String>): ResultsAnalysis {
        val result = geminiService.analyzeResults(medicationNames)
        if (result.dosages.isNotEmpty() || result.presentations.isNotEmpty()) {
            return result
        }
        return fallbackService.analyzeResults(medicationNames)
    }

    override suspend fun structureHtmlContent(htmlFragment: String): List<ContentBlock> {
        val aiResult = geminiService.structureHtmlContent(htmlFragment)
        if (aiResult.isNotEmpty()) {
            return aiResult
        }
        return fallbackService.structureHtmlContent(htmlFragment)
    }

    override suspend fun generateLeafletSummary(html: String): LeafletSummary? {
        val aiResult = geminiService.generateLeafletSummary(html)
        if (aiResult != null) {
            return aiResult
        }
        return fallbackService.generateLeafletSummary(html)
    }
    
    /**
     * Tool Calling: Process OCR - try Gemini, fallback if error
     */
    override suspend fun processOcrText(ocrText: String): ToolAction {
        val geminiResult = geminiService.processOcrText(ocrText)
        
        if (geminiResult is ToolAction.Search) {
            Log.d(TAG, "Tool: Gemini OCR → Search(${geminiResult.query})")
            return geminiResult
        }
        
        // Fallback
        val fallbackResult = fallbackService.processOcrText(ocrText)
        Log.d(TAG, "Tool: Fallback OCR → $fallbackResult")
        return fallbackResult
    }
    
    /**
     * Tool Calling: Process search results - try Gemini for analysis
     */
    override suspend fun processSearchResults(
        originalQuery: String,
        resultNames: List<String>,
        scannedDosage: String?
    ): ToolAction {
        val geminiResult = geminiService.processSearchResults(originalQuery, resultNames, scannedDosage)
        
        if (geminiResult is ToolAction.Complete && 
            (geminiResult.filters.dosages.isNotEmpty() || geminiResult.filters.presentations.isNotEmpty())) {
            Log.d(TAG, "Tool: Gemini analysis complete, filters: ${geminiResult.filters}")
            return geminiResult
        }
        
        // Fallback returns empty filters
        return fallbackService.processSearchResults(originalQuery, resultNames, scannedDosage)
    }
    
    /**
     * Parse treatment text - try Gemini first, fallback returns empty
     */
    override suspend fun parseTreatmentText(text: String): List<ParsedMedication> {
        val aiResult = geminiService.parseTreatmentText(text)
        if (aiResult.isNotEmpty()) {
            Log.d(TAG, "Gemini parsed ${aiResult.size} medications from treatment")
            return aiResult
        }
        return fallbackService.parseTreatmentText(text)
    }
    
    override fun isAvailable(): Boolean = true // Always available (has fallback)
    
    /**
     * Generate text response - try Gemini first, fallback returns null
     */
    override suspend fun generateTextResponse(prompt: String): String? {
        val geminiResult = geminiService.generateTextResponse(prompt)
        if (!geminiResult.isNullOrBlank()) {
            return geminiResult
        }
        return fallbackService.generateTextResponse(prompt)
    }
}
