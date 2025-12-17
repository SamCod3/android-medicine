package com.samcod3.meditrack.data.remote.parser

import android.util.Log
import com.samcod3.meditrack.domain.model.LeafletSection
import org.jsoup.Jsoup

/**
 * Parser for extracting structured leaflet sections from CIMA HTML documents.
 * Uses multiple parsing strategies to handle different HTML structures.
 */
object LeafletHtmlParser {
    
    private const val TAG = "LeafletHtmlParser"
    
    /**
     * Parses HTML content and extracts leaflet sections.
     * Tries multiple strategies in order of reliability.
     */
    fun parse(html: String): List<LeafletSection> {
        val doc = Jsoup.parse(html)
        var sections = mutableListOf<LeafletSection>()
        
        // STRATEGY 0: Semantic IDs (h1 id="1" + section)
        // Structure: <h1 id="N">Title</h1> <section>Content</section>
        try {
            var foundSemantic = false
            for (i in 1..6) {
                val id = "$i"
                val header = doc.getElementById(id)
                
                if (header != null && (header.tagName() == "h1" || header.tagName() == "h2" || header.tagName() == "h3")) {
                    val title = header.text().trim()
                    val contentElement = header.nextElementSibling()
                    val content = contentElement?.outerHtml() ?: ""
                    
                    if (content.isNotBlank()) {
                        sections.add(LeafletSection(i, title, content))
                        foundSemantic = true
                    }
                }
            }
            
            if (foundSemantic && sections.size >= 1) {
                Log.d(TAG, "Using Semantic ID parsing strategy")
                return sections
            }
        } catch (e: Exception) {
            Log.e(TAG, "Semantic ID parsing failed", e)
        }
        
        // STRATEGY 1: Parse table of contents (Index links)
        // Look for links that point to internal anchors (#) and contain section keywords
        try {
            val indexLinks = doc.select("a[href^='#']")
            val sectionConnectors = mutableListOf<Pair<Int, String>>() // SectionNum -> TargetId
            
            for (link in indexLinks) {
                val text = link.text().trim()
                val href = link.attr("href").substring(1) // remove #
                
                val num = getSectionNumberFromText(text)
                if (num != null) {
                    // Avoid duplicates, take the first one for each section
                    if (sectionConnectors.none { it.first == num }) {
                        sectionConnectors.add(num to href)
                    }
                }
            }
            
            if (sectionConnectors.size >= 3) {
                Log.d(TAG, "Using Index-Based parsing strategy")
                sectionConnectors.sortBy { it.first }
                
                for (i in 0 until sectionConnectors.size) {
                    val (num, id) = sectionConnectors[i]
                    val nextId = if (i < sectionConnectors.size - 1) sectionConnectors[i+1].second else null
                    
                    var startEl = doc.getElementById(id)
                    if (startEl == null) {
                        startEl = doc.select("[name='$id']").first()
                    }
                    
                    if (startEl != null) {
                        val title = startEl.text().ifBlank { "Sección $num" }
                        val contentBuilder = StringBuilder()
                        
                        var current = startEl.nextElementSibling()
                        
                        while (current != null) {
                            if (nextId != null) {
                                if (current.id() == nextId || current.attr("name") == nextId) {
                                    break
                                }
                                if (current.select("#$nextId, [name='$nextId']").isNotEmpty()) {
                                    break
                                }
                            }
                            
                            contentBuilder.append(current.outerHtml())
                            current = current.nextElementSibling()
                        }
                        
                        sections.add(LeafletSection(num, title, contentBuilder.toString()))
                    }
                }
                
                if (sections.isNotEmpty()) return sections
            }
        } catch (e: Exception) {
            Log.e(TAG, "Index parsing failed, falling back to regex", e)
        }
        
        // STRATEGY 2: Fallback to Regex / Text Scanning
        Log.d(TAG, "Using Regex-Based parsing strategy")
        
        val container = doc.select(".texto_prospecto").first() ?: doc.body()
        val headerRegex = Regex("""^([1-6])[\s\.\-\)]+(.*)""")
        
        var currentSectionNum = 0
        var currentTitle = ""
        var currentContent = StringBuilder()
        
        val elements = container.select("p, div, h1, h2, h3, h4, h5, h6, header, li, strong, b, span")
        
        for (element in elements) {
            val text = element.text().trim()
            if (text.isBlank()) continue
            if (text.length > 200) {
                if (currentSectionNum > 0) currentContent.append("<p>${element.html()}</p>")
                continue
            }
            
            val match = headerRegex.find(text)
            var foundSectionNum: Int? = null
            
            if (match != null) {
                try {
                    val num = match.groupValues[1].toInt()
                    if (isValidSectionTitle(num, match.groupValues[2])) {
                        foundSectionNum = num
                        currentTitle = text
                    }
                } catch (e: Exception) {}
            }
            
            if (foundSectionNum != null && foundSectionNum > currentSectionNum) {
                if (currentSectionNum > 0) {
                    sections.add(LeafletSection(currentSectionNum, currentTitle, currentContent.toString()))
                }
                currentSectionNum = foundSectionNum
                currentContent = StringBuilder()
                currentTitle = text
            } else if (currentSectionNum > 0) {
                if (element.tagName() in listOf("p", "li", "div")) {
                    currentContent.append(element.outerHtml())
                }
            }
        }
        
        if (currentSectionNum > 0) {
            sections.add(LeafletSection(currentSectionNum, currentTitle, currentContent.toString()))
        }
        
        return sections
    }
    
    /**
     * Extracts section number from text that may contain section keywords.
     */
    private fun getSectionNumberFromText(text: String): Int? {
        val lower = text.lowercase()
        
        for (i in 1..6) {
            if (text.startsWith("$i") && (text.contains(".") || text.contains("-") || text.contains(" "))) {
                if (isValidSectionTitle(i, lower)) return i
            }
        }
        
        if (lower.contains("qué es") || lower.contains("que es")) return 1
        if (lower.contains("antes de") || lower.contains("necesita saber")) return 2
        if (lower.contains("cómo tomar") || lower.contains("como usar") || lower.contains("posología")) return 3
        if (lower.contains("efectos adversos")) return 4
        if (lower.contains("conservación")) return 5
        if (lower.contains("contenido del envase") || lower.contains("información adicional")) return 6
        
        return null
    }
    
    /**
     * Validates that the title content matches expected Spanish leaflet section keywords.
     */
    private fun isValidSectionTitle(num: Int, titlePart: String): Boolean {
        val lower = titlePart.lowercase()
        return when (num) {
            1 -> lower.contains("qué es") || lower.contains("que es")
            2 -> lower.contains("necesita saber") || lower.contains("antes de") || lower.contains("tenga cuidado")
            3 -> lower.contains("cómo") || lower.contains("como") || lower.contains("usar")
            4 -> lower.contains("efectos") || lower.contains("adversos")
            5 -> lower.contains("conservación") || lower.contains("conservacion")
            6 -> lower.contains("contenido") || lower.contains("envase") || lower.contains("información")
            else -> false
        }
    }
}
