package com.samcod3.meditrack.domain.usecase

import android.util.Log
import com.samcod3.meditrack.ai.AIService
import com.samcod3.meditrack.data.local.dao.SectionSummaryCacheDao
import com.samcod3.meditrack.data.local.entity.SectionSummaryCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Use case for generating and caching section summaries.
 * Implements chunking strategy for long sections and MapReduce for combining.
 */
class SectionSummaryUseCase(
    private val sectionSummaryCacheDao: SectionSummaryCacheDao,
    private val aiService: AIService
) {
    companion object {
        private const val TAG = "SectionSummaryUseCase"
        
        // Adaptive chunking thresholds
        private const val SINGLE_CHUNK_THRESHOLD = 2500   // ≤2500 = 1 part
        private const val TWO_PARTS_THRESHOLD = 5000      // 2500-5000 = 2 parts
        private const val THREE_PARTS_THRESHOLD = 8000    // 5000-8000 = 3 parts
        // >8000 = 4 parts (max)
        
        private const val TIMEOUT_PER_CHUNK_MS = 20_000L  // 20s timeout per chunk
        private const val DELAY_BETWEEN_PARTS_MS = 2000L  // 2s pause between AI calls
    }
    
    /**
     * Get or generate summary for a section.
     * Uses cached version if content hash matches.
     */
    suspend fun getSectionSummary(
        registrationNumber: String,
        sectionNumber: Int,
        sectionTitle: String,
        sectionContent: String
    ): Result<String> = getSectionSummaryWithProgress(
        registrationNumber, sectionNumber, sectionTitle, sectionContent
    ) { _, _ -> } // No-op callback
    
    /**
     * Get or generate summary for a section with retry notifications.
     * @param onRetry Called when a retry is about to happen (attempt, maxRetries)
     */
    suspend fun getSectionSummaryWithProgress(
        registrationNumber: String,
        sectionNumber: Int,
        sectionTitle: String,
        sectionContent: String,
        onRetry: (Int, Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Check cache
            val contentHash = SectionSummaryCacheEntity.hashContent(sectionContent)
            val cached = sectionSummaryCacheDao.getSummary(registrationNumber, sectionNumber)
            
            if (cached != null && cached.contentHash == contentHash) {
                Log.d(TAG, "Cache hit for section $sectionNumber of $registrationNumber")
                return@withContext Result.success(cached.summary)
            }
            
            Log.d(TAG, "Cache miss. Generating summary for section $sectionNumber (${sectionContent.length} chars)")
            
            // 2. Check AI availability
            if (!aiService.isAvailable()) {
                return@withContext Result.failure(Exception("AI no disponible"))
            }
            
            // 3. Generate summary (with chunking if needed)
            val summary = if (sectionContent.length <= SINGLE_CHUNK_THRESHOLD) {
                generateSingleChunkSummary(sectionTitle, sectionContent)
            } else {
                generateMultiChunkSummary(sectionTitle, sectionContent)
            }
            
            if (summary == null) {
                return@withContext Result.failure(Exception("No se pudo generar el resumen"))
            }
            
            // 4. Cache the result
            val cacheEntity = SectionSummaryCacheEntity(
                registrationNumber = registrationNumber,
                sectionNumber = sectionNumber,
                contentHash = contentHash,
                summary = summary
            )
            sectionSummaryCacheDao.insertSummary(cacheEntity)
            Log.d(TAG, "Cached summary for section $sectionNumber")
            
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating section summary", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate summary for a short section (single AI call).
     */
    private suspend fun generateSingleChunkSummary(title: String, content: String): String? {
        val prompt = buildSummaryPrompt(title, content)
        
        return try {
            withTimeout(TIMEOUT_PER_CHUNK_MS) {
                aiService.generateTextResponse(prompt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Single chunk summary failed", e)
            null
        }
    }
    /**
     * Generate summary for a long section using Adaptive Iterative Refinement:
     * - 2500-5000 chars: 2 parts
     * - 5000-8000 chars: 3 parts  
     * - >8000 chars: 4 parts (max)
     * 
     * Uses recursive refinement: each step combines previous summary with new part.
     */
    private suspend fun generateMultiChunkSummary(title: String, content: String): String? {
        // Determine number of parts based on length
        val numParts = when {
            content.length <= TWO_PARTS_THRESHOLD -> 2
            content.length <= THREE_PARTS_THRESHOLD -> 3
            else -> 4
        }
        
        Log.d(TAG, "Adaptive chunking: ${content.length} chars -> $numParts parts")
        
        // Check if this is a dosage section
        val isDosageSection = title.contains("dosis", ignoreCase = true) ||
                              title.contains("posolog", ignoreCase = true) ||
                              title.contains("tomar", ignoreCase = true) ||
                              title.contains("usar", ignoreCase = true)
        
        val dosageNote = if (isDosageSection) " Si hay dosis específicas (mg, ml, comprimidos), inclúyelas." else ""
        val formatNote = "Escribe en TEXTO PLANO, sin markdown (##, **, -)."
        
        // Split content into equal parts at sentence boundaries
        val parts = splitIntoParts(content, numParts)
        Log.d(TAG, "Split into ${parts.size} parts: ${parts.map { it.length }}")
        
        // Step 1: Summarize first part
        val firstPrompt = """
            Resume la PRIMERA PARTE (1/${parts.size}) de la sección "$title" de un prospecto médico.
            Extrae los puntos más importantes en 2-3 frases.$dosageNote
            $formatNote
            
            ${parts[0]}
        """.trimIndent()
        
        var currentSummary = try {
            withTimeout(TIMEOUT_PER_CHUNK_MS) {
                aiService.generateTextResponse(firstPrompt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Part 1 summary failed", e)
            null
        }
        
        if (currentSummary == null) {
            // Fallback: try just the beginning
            val truncated = content.take(SINGLE_CHUNK_THRESHOLD)
            return generateSingleChunkSummary(title, truncated)
        }
        
        Log.d(TAG, "Part 1 summary: ${currentSummary.length} chars")
        
        // Steps 2-N: Refine with subsequent parts
        for (i in 1 until parts.size) {
            // Pause between requests to let AICore recover
            kotlinx.coroutines.delay(DELAY_BETWEEN_PARTS_MS)
            
            val partContent = parts[i]
            val isLastPart = i == parts.size - 1
            
            val refinePrompt = """
                Tienes un resumen parcial (${i}/${parts.size}) de la sección "$title":
                
                --- RESUMEN ACTUAL ---
                $currentSummary
                --- FIN RESUMEN ---
                
                Lee la PARTE ${i + 1} de ${parts.size} y ${if (isLastPart) "genera el RESUMEN FINAL COMPLETO" else "actualiza el resumen"} (3-4 frases).$dosageNote
                $formatNote
                
                --- PARTE ${i + 1} ---
                $partContent
            """.trimIndent()
            
            val refined = try {
                withTimeout(TIMEOUT_PER_CHUNK_MS) {
                    aiService.generateTextResponse(refinePrompt)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Part ${i + 1} refinement failed", e)
                null
            }
            
            if (refined != null) {
                currentSummary = refined
                Log.d(TAG, "Part ${i + 1} refined: ${currentSummary.length} chars")
            } else {
                Log.w(TAG, "Part ${i + 1} failed, keeping previous summary")
                // Keep current summary, don't fail completely
            }
        }
        
        return currentSummary
    }
    
    /**
     * Split text into N roughly equal parts at sentence boundaries.
     */
    private fun splitIntoParts(text: String, numParts: Int): List<String> {
        if (numParts <= 1) return listOf(text)
        
        val partSize = text.length / numParts
        val parts = mutableListOf<String>()
        var startIdx = 0
        
        for (i in 0 until numParts - 1) {
            val targetEnd = startIdx + partSize
            val splitPoint = findSentenceBreak(text, targetEnd)
            parts.add(text.substring(startIdx, splitPoint).trim())
            startIdx = splitPoint
        }
        
        // Last part gets the remainder
        parts.add(text.substring(startIdx).trim())
        
        return parts.filter { it.isNotEmpty() }
    }
    
    /**
     * Find a sentence break near the target position.
     * Looks for ". " or newline within 200 chars of target.
     */
    private fun findSentenceBreak(text: String, target: Int): Int {
        val searchRange = 200
        val start = maxOf(0, target - searchRange)
        val end = minOf(text.length, target + searchRange)
        
        // Look for sentence end near target
        for (i in target downTo start) {
            if (i > 0 && (text[i - 1] == '.' || text[i - 1] == '\n') && 
                (i >= text.length || text[i] == ' ' || text[i] == '\n')) {
                return i
            }
        }
        
        // If not found before, look after
        for (i in target until end) {
            if (i > 0 && (text[i - 1] == '.' || text[i - 1] == '\n') && 
                (i >= text.length || text[i] == ' ' || text[i] == '\n')) {
                return i
            }
        }
        
        // Fallback to midpoint
        return target
    }
    
    /**
     * Build the prompt for single-chunk summarization.
     */
    private fun buildSummaryPrompt(title: String, content: String): String {
        val isDosageSection = title.contains("dosis", ignoreCase = true) ||
                              title.contains("posolog", ignoreCase = true) ||
                              title.contains("tomar", ignoreCase = true) ||
                              title.contains("usar", ignoreCase = true)
        
        val dosageInstruction = if (isDosageSection) {
            "\nIMPORTANTE: Si hay dosis específicas (mg, ml, comprimidos, etc.), INCLÚYELAS en el resumen."
        } else ""
        
        return """
            Resume esta sección del prospecto médico en 3-4 frases claras para un paciente.
            Incluye la información más importante: para qué sirve, cómo tomarlo, o precauciones clave.$dosageInstruction
            
            FORMATO: Escribe en TEXTO PLANO. NO uses markdown (##, **, -, •). Solo frases normales.
            
            Sección: $title
            
            Contenido:
            $content
            
            Resumen (texto plano, 3-4 frases):
        """.trimIndent()
    }
    
    /**
     * Generate a refined summary using a specific refinement mode.
     * This always regenerates (no cache check) but saves the result.
     */
    suspend fun generateRefinedSummary(
        registrationNumber: String,
        sectionNumber: Int,
        sectionTitle: String,
        sectionContent: String,
        mode: com.samcod3.meditrack.domain.model.RefinementMode
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating refined summary with mode: ${mode.name}")
            
            if (!aiService.isAvailable()) {
                return@withContext Result.failure(Exception("AI no disponible"))
            }
            
            val prompt = buildRefinedPrompt(sectionTitle, sectionContent, mode)
            
            val summary = try {
                withTimeout(TIMEOUT_PER_CHUNK_MS * 2) { // Extra time for refined prompts
                    aiService.generateTextResponse(prompt)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Refined summary failed", e)
                null
            }
            
            if (summary == null) {
                return@withContext Result.failure(Exception("No se pudo generar el resumen"))
            }
            
            // Clean any residual markdown from AI response
            val cleanedSummary = cleanMarkdown(summary)
            
            // Cache the refined result
            val contentHash = SectionSummaryCacheEntity.hashContent(sectionContent)
            val cacheEntity = SectionSummaryCacheEntity(
                registrationNumber = registrationNumber,
                sectionNumber = sectionNumber,
                contentHash = contentHash,
                summary = cleanedSummary
            )
            sectionSummaryCacheDao.insertSummary(cacheEntity)
            Log.d(TAG, "Cached refined summary for section $sectionNumber")
            
            Result.success(cleanedSummary)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating refined summary", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build prompt for refined summary based on mode.
     */
    private fun buildRefinedPrompt(
        title: String, 
        content: String, 
        mode: com.samcod3.meditrack.domain.model.RefinementMode
    ): String {
        // Format note at beginning is more effective
        val formatNote = "REGLA OBLIGATORIA: Escribe SOLO texto plano. PROHIBIDO usar: ## (títulos), ** (negrita), - (listas), * (viñetas), __ (subrayado). Solo frases normales separadas por puntos."
        val sourceNote = "Usa SOLO la información del contenido proporcionado. NO inventes."
        
        val instruction = when (mode) {
            com.samcod3.meditrack.domain.model.RefinementMode.REGENERATE -> 
                "Resume esta sección del prospecto en 3-4 frases claras para un paciente."
                
            com.samcod3.meditrack.domain.model.RefinementMode.MORE_DETAIL -> 
                "Resume esta sección de forma DETALLADA (5-6 frases). Incluye todos los puntos importantes que aparecen en el contenido."
                
            com.samcod3.meditrack.domain.model.RefinementMode.SIMPLER -> 
                "Resume el contenido en 2 frases MUY SIMPLES, como si explicaras a alguien sin conocimientos médicos."
                
            com.samcod3.meditrack.domain.model.RefinementMode.FOCUS_DOSAGE -> 
                "Del contenido proporcionado, extrae SOLO la información de DOSIS: cuántos mg/ml, cuántas veces al día, duración del tratamiento. Incluye los números exactos que aparecen."
                
            com.samcod3.meditrack.domain.model.RefinementMode.FOR_CHILD -> 
                "Del contenido proporcionado, extrae la información relevante para NIÑOS: dosis pediátricas, precauciones en menores, contraindicaciones en niños. Si no hay información específica para niños, indícalo."
                
            com.samcod3.meditrack.domain.model.RefinementMode.FOR_ELDERLY -> 
                "Del contenido proporcionado, extrae la información relevante para PERSONAS MAYORES: ajustes de dosis en ancianos, precauciones especiales. Si no hay información específica para ancianos, indícalo."
                
            com.samcod3.meditrack.domain.model.RefinementMode.SERIOUS_EFFECTS -> 
                "Del contenido proporcionado, menciona SOLO los efectos adversos GRAVES o MUY FRECUENTES. Ignora los raros o leves."
                
            com.samcod3.meditrack.domain.model.RefinementMode.ALL_EFFECTS -> 
                "Del contenido proporcionado, menciona TODOS los efectos adversos, organizados por frecuencia si es posible."
                
            com.samcod3.meditrack.domain.model.RefinementMode.ALCOHOL -> 
                "Del contenido proporcionado, busca y responde: ¿Se menciona algo sobre el alcohol? Si no se menciona, indícalo."
                
            com.samcod3.meditrack.domain.model.RefinementMode.PREGNANCY -> 
                "Del contenido proporcionado, busca y responde: ¿Qué dice sobre embarazo y lactancia? Si no se menciona, indícalo."
        }
        
        return """
            $formatNote
            
            $instruction
            $sourceNote
            
            Sección: $title
            
            Contenido:
            $content
        """.trimIndent()
    }
    
    /**
     * Remove markdown formatting from AI response.
     */
    private fun cleanMarkdown(text: String): String {
        return text
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "") // Remove ## headers
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // Remove **bold**
            .replace(Regex("\\*([^*]+)\\*"), "$1") // Remove *italic*
            .replace(Regex("__([^_]+)__"), "$1") // Remove __underline__
            .replace(Regex("^[-*•]\\s+", RegexOption.MULTILINE), "") // Remove - * • bullets
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "") // Remove 1. 2. numbering
            .replace(Regex("\\n{3,}"), "\n\n") // Collapse multiple newlines
            .trim()
    }
    
    /**
     * Delete cached summary for a specific section.
     * This allows regenerating the summary.
     */
    suspend fun deleteSectionCache(registrationNumber: String, sectionNumber: Int) {
        sectionSummaryCacheDao.deleteSectionSummary(registrationNumber, sectionNumber)
    }
    
    /**
     * Clear all cached summaries (for testing/debugging).
     */
    suspend fun clearCache() {
        sectionSummaryCacheDao.clearAll()
    }
    
    /**
     * Clear expired cache entries.
     */
    suspend fun clearExpiredCache(maxAgeDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        sectionSummaryCacheDao.deleteExpired(cutoffTime)
    }
}
