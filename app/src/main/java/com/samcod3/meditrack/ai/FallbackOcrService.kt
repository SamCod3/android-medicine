package com.samcod3.meditrack.ai

import android.util.Log
import com.samcod3.meditrack.domain.model.ContentBlock
import com.samcod3.meditrack.domain.model.LeafletSummary
import com.samcod3.meditrack.domain.model.ParsedMedication

/**
 * Fallback AI Service that uses regex/heuristics to extract medication names.
 * This is used when Gemini Nano is not available on the device.
 * 
 * This implementation extracts the logic from the original cleanOcrText() function
 * in SearchScreen.kt to maintain consistency.
 */
class FallbackOcrService : AIService {
    
    companion object {
        private const val TAG = "FallbackOcrService"
    }
    
    override suspend fun extractMedicationName(ocrText: String): AIResult {
        val cleanedText = cleanOcrText(ocrText)
        Log.d(TAG, "Fallback extraction: '$cleanedText' from '$ocrText'")
        return AIResult(text = cleanedText, usedAI = false)
    }
    
    override suspend fun analyzeResults(medicationNames: List<String>): ResultsAnalysis {
        // Fallback doesn't analyze results, returns empty
        return ResultsAnalysis()
    }

    override suspend fun structureHtmlContent(htmlFragment: String): List<ContentBlock> {
        return emptyList()
    }

    override suspend fun generateLeafletSummary(html: String): LeafletSummary? {
        return null // Fallback does not support summary
    }
    
    // Tool Calling methods - Fallback returns basic results
    override suspend fun processOcrText(ocrText: String): ToolAction {
        val cleanedText = cleanOcrText(ocrText)
        return if (cleanedText.isNotBlank()) {
            ToolAction.Search(query = cleanedText)
        } else {
            ToolAction.Error("Could not extract medication name")
        }
    }
    
    override suspend fun processSearchResults(
        originalQuery: String,
        resultNames: List<String>,
        scannedDosage: String?
    ): ToolAction {
        // Fallback can't analyze, just return empty filters
        return ToolAction.Complete(
            searchQuery = originalQuery,
            filters = ResultsAnalysis(),
            autoApplyDosage = scannedDosage
        )
    }
    
    override suspend fun parseTreatmentText(text: String): List<ParsedMedication> {
        // Fallback cannot parse treatment text, Gemini Nano is required
        Log.w(TAG, "Treatment parsing not available without Gemini Nano")
        return emptyList()
    }
    
    override fun isAvailable(): Boolean = true // Always available as fallback
    
    /**
     * Fallback cannot generate text responses, requires Gemini Nano.
     */
    override suspend fun generateTextResponse(prompt: String): String? {
        Log.d(TAG, "generateTextResponse not supported in fallback")
        return null
    }
    
    /**
     * Cleans up OCR text for medication name search.
     * - Takes the first meaningful line (likely the medication name)
     * - Removes special characters but keeps accents
     * - Trims and normalizes whitespace
     */
    private fun cleanOcrText(rawText: String): String {
        if (rawText.isBlank()) return ""
        
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 3 } // Filter out very short lines
        
        // Try to find the most likely medication name line
        // Prioritize lines that are MOSTLY alphabetic (to avoid CN numbers)
        // but can contain some digits (for dosage like "10 mg")
        val candidateLine = lines.firstOrNull { line ->
            val letterCount = line.count { it.isLetter() }
            val digitCount = line.count { it.isDigit() }
            // Must have more letters than digits (name, not CN)
            // and reasonable length
            letterCount > digitCount && letterCount >= 3 && line.length in 3..60
        } ?: lines.firstOrNull { it.any { c -> c.isLetter() } } ?: rawText
        
        // Clean the line: keep letters, spaces, and digits (for dosage like "10 mg")
        // Remove only special chars that aren't useful for medication names
        return candidateLine
            .replace(Regex("[^a-zA-Z0-9áéíóúüñÁÉÍÓÚÜÑ\\s.,/-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60) // Limit length
    }
}
