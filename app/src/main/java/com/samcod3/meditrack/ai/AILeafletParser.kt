package com.samcod3.meditrack.ai

import android.text.Html
import android.util.Log
import com.samcod3.meditrack.data.local.dao.SummaryCacheDao
import com.samcod3.meditrack.data.local.entity.SummaryCacheEntity
import com.samcod3.meditrack.data.remote.parser.LeafletHtmlParser
import com.samcod3.meditrack.domain.model.ContentBlock
import com.samcod3.meditrack.domain.model.LeafletSummary
import com.samcod3.meditrack.domain.model.ParsedLeaflet
import com.samcod3.meditrack.domain.model.ParsedSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hybrid parser that tries AI-powered parsing first, then falls back to regex/DOM parsing.
 * Includes summary caching for repeated medication views.
 */
class AILeafletParser(
    private val aiService: AIService,
    private val summaryCacheDao: SummaryCacheDao? = null
) {
    companion object {
        private const val TAG = "AILeafletParser"
    }

    /**
     * Parse leaflet HTML with optional caching.
     * @param html The leaflet HTML content
     * @param registrationNumber Optional registration number for cache key
     */
    fun parseFlow(html: String, registrationNumber: String? = null): Flow<ParsedLeaflet> = channelFlow {
        try {
            // 1. Always split into sections using legacy parser (robust regex/DOM)
            val legacySections = LeafletHtmlParser.parse(html)
            
            if (legacySections.isEmpty()) {
                send(ParsedLeaflet.EMPTY)
                return@channelFlow
            }
            
            // 2. Emit initial legacy state IMMEDIATELY (Instant Load)
            val initialSections = legacySections.map { section ->
                ParsedSection(
                    title = section.title,
                    content = fallbackContent(section.content)
                )
            }
            // Shared state protected by Mutex
            var currentState = ParsedLeaflet(initialSections)
            val mutex = Mutex()
            
            send(currentState)
            
            val isAiAvailable = aiService.isAvailable()
            if (!isAiAvailable) {
                return@channelFlow
            }
            
            Log.d(TAG, "AI available. Launching split jobs: Summary + Section Formatting")

            // JOB 1: Quick Summary (Parallel) - with cache
            launch {
                try {
                    // Check cache first
                    val cachedSummary = registrationNumber?.let { regNum ->
                        summaryCacheDao?.getSummary(regNum)?.takeIf { !it.isExpired() }
                    }
                    
                    val summary = if (cachedSummary != null) {
                        Log.d(TAG, "Using cached summary for $registrationNumber")
                        LeafletSummary(
                            indications = cachedSummary.indications,
                            dosage = cachedSummary.dosage,
                            warnings = cachedSummary.warnings
                        )
                    } else {
                        // Generate new summary
                        val newSummary = aiService.generateLeafletSummary(html)
                        
                        // Cache it if we have registration number
                        if (newSummary != null && registrationNumber != null && summaryCacheDao != null) {
                            Log.d(TAG, "Caching summary for $registrationNumber")
                            summaryCacheDao.insertSummary(
                                SummaryCacheEntity(
                                    registrationNumber = registrationNumber,
                                    indications = newSummary.indications,
                                    dosage = newSummary.dosage,
                                    warnings = newSummary.warnings
                                )
                            )
                        }
                        newSummary
                    }
                    
                    if (summary != null) {
                        mutex.withLock {
                            currentState = currentState.copy(summary = summary)
                            send(currentState)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Summary generation failed", e)
                }
            }

            // JOB 2: Progressive Formatting (Parallel Loop)
            launch {
                val currentSectionsList = initialSections.toMutableList()
                
                legacySections.forEachIndexed { index, section ->
                    try {
                        // Structure ONLY this section's content
                        val blocks = aiService.structureHtmlContent(section.content)
                        
                        if (blocks.isNotEmpty()) {
                            mutex.withLock {
                                // Update specific section locally
                                currentSectionsList[index] = currentSectionsList[index].copy(content = blocks)
                                // Update shared state
                                currentState = currentState.copy(sections = currentSectionsList.toList())
                                send(currentState)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to structure section '${section.title}' with AI", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in hybrid parsing flow", e)
            send(ParsedLeaflet.EMPTY)
        }
    }

    private fun fallbackContent(html: String): List<ContentBlock> {
        // Simple fallback: Convert HTML to plain text and wrap in a single Paragraph
        // Reverted to simple implementation as per user request to avoid "Smart Fallback" artifacts
        val plainText = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        return if (plainText.isNotEmpty()) {
            listOf(ContentBlock.Paragraph(plainText))
        } else {
            emptyList()
        }
    }
}

