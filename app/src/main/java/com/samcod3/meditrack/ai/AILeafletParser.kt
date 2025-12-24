package com.samcod3.meditrack.ai

import android.util.Log
import com.samcod3.meditrack.data.remote.parser.LeafletHtmlParser
import com.samcod3.meditrack.domain.model.ContentBlock
import com.samcod3.meditrack.domain.model.ParsedLeaflet
import com.samcod3.meditrack.domain.model.ParsedSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Leaflet parser using a two-pass approach:
 * 1. NORMALIZE: Flatten HTML structure, detect pseudo-lists, clean whitespace
 * 2. EXTRACT: Convert normalized HTML to ContentBlocks
 */
class AILeafletParser(
    private val aiService: AIService
) {
    companion object {
        private const val TAG = "AILeafletParser"
        
        // Pattern to detect pseudo-list items (bullet-like text)
        private val PSEUDO_LIST_PATTERN = Regex("""^[\s]*[•\-\*▪◦‣⁃]\s*(.+)""")
        private val NUMBERED_LIST_PATTERN = Regex("""^[\s]*(\d+)[\.\)\-]\s*(.+)""")
    }

    fun parseFlow(html: String, registrationNumber: String? = null): Flow<ParsedLeaflet> {
        return try {
            val legacySections = LeafletHtmlParser.parse(html)
            
            if (legacySections.isEmpty()) {
                flowOf(ParsedLeaflet.EMPTY)
            } else {
                val formattedSections = legacySections.map { section ->
                    ParsedSection(
                        title = section.title,
                        content = formatWithTwoPasses(section.content),
                        rawHtml = section.content  // Keep original HTML for WebView
                    )
                }
                
                Log.d(TAG, "Parsed ${formattedSections.size} sections with two-pass formatter")
                flowOf(ParsedLeaflet(formattedSections))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing leaflet", e)
            flowOf(ParsedLeaflet.EMPTY)
        }
    }

    /**
     * Two-pass formatting pipeline.
     */
    private fun formatWithTwoPasses(html: String): List<ContentBlock> {
        if (html.isBlank()) return emptyList()
        
        // PASS 1: Normalize HTML
        val normalizedElements = pass1Normalize(html)
        
        // PASS 2: Extract ContentBlocks
        return pass2Extract(normalizedElements)
    }

    // ==================== PASS 1: NORMALIZE ====================
    
    /**
     * Normalizes HTML into a flat list of simple elements.
     * Each element is either:
     * - A paragraph (text content)
     * - A list item (bullet point)
     */
    private data class NormalizedElement(
        val type: ElementType,
        val text: String,
        val number: Int? = null  // For numbered items
    )
    
    private enum class ElementType { PARAGRAPH, BULLET, NUMBERED }
    
    private fun pass1Normalize(html: String): List<NormalizedElement> {
        val doc = Jsoup.parseBodyFragment(html)
        val result = mutableListOf<NormalizedElement>()
        
        // Recursively collect all text content
        collectContent(doc.body(), result)
        
        // Post-process: merge consecutive tiny paragraphs that might be split
        return mergeFragments(result)
    }
    
    /**
     * Recursively collects content from all nodes.
     */
    private fun collectContent(node: Node, result: MutableList<NormalizedElement>) {
        when (node) {
            is TextNode -> {
                val text = node.text().trim()
                if (text.isNotEmpty()) {
                    // Check if it's a pseudo-list item
                    val bulletMatch = PSEUDO_LIST_PATTERN.find(text)
                    val numberedMatch = NUMBERED_LIST_PATTERN.find(text)
                    
                    when {
                        bulletMatch != null -> {
                            result.add(NormalizedElement(ElementType.BULLET, bulletMatch.groupValues[1].trim()))
                        }
                        numberedMatch != null -> {
                            result.add(NormalizedElement(
                                ElementType.NUMBERED, 
                                numberedMatch.groupValues[2].trim(),
                                numberedMatch.groupValues[1].toIntOrNull()
                            ))
                        }
                        else -> {
                            result.add(NormalizedElement(ElementType.PARAGRAPH, text))
                        }
                    }
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                
                when (tag) {
                    // List items become bullets
                    "li" -> {
                        val text = extractAllText(node).trim()
                        if (text.isNotEmpty()) {
                            // Check if parent is OL or UL
                            val parentTag = node.parent()?.nodeName()?.lowercase()
                            if (parentTag == "ol") {
                                val index = node.elementSiblingIndex() + 1
                                result.add(NormalizedElement(ElementType.NUMBERED, text, index))
                            } else {
                                result.add(NormalizedElement(ElementType.BULLET, text))
                            }
                        }
                    }
                    
                    // Skip these containers but process children
                    "ul", "ol" -> {
                        for (child in node.childNodes()) {
                            collectContent(child, result)
                        }
                    }
                    
                    // Block elements: collect text as paragraph
                    "p", "div", "section", "article" -> {
                        // Check if contains nested list
                        val nestedList = node.selectFirst("ul, ol")
                        if (nestedList != null) {
                            // Get text before the list
                            val textBefore = getTextBeforeElement(node, nestedList)
                            if (textBefore.isNotEmpty()) {
                                addParagraphOrPseudoList(textBefore, result)
                            }
                            // Process the list
                            collectContent(nestedList, result)
                            // Get text after the list
                            val textAfter = getTextAfterElement(node, nestedList)
                            if (textAfter.isNotEmpty()) {
                                addParagraphOrPseudoList(textAfter, result)
                            }
                        } else {
                            val text = extractAllText(node).trim()
                            if (text.isNotEmpty()) {
                                addParagraphOrPseudoList(text, result)
                            }
                        }
                    }
                    
                    // Line breaks: treat as paragraph separator (ignore, natural split)
                    "br" -> { }
                    
                    // Inline elements: process children
                    "span", "strong", "b", "em", "i", "a" -> {
                        for (child in node.childNodes()) {
                            collectContent(child, result)
                        }
                    }
                    
                    // Headers: treat as paragraphs (will be styled by section title)
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        val text = node.text().trim()
                        if (text.isNotEmpty()) {
                            result.add(NormalizedElement(ElementType.PARAGRAPH, text))
                        }
                    }
                    
                    // Tables: extract text as paragraph
                    "table" -> {
                        val text = node.text().trim()
                        if (text.isNotEmpty()) {
                            result.add(NormalizedElement(ElementType.PARAGRAPH, text))
                        }
                    }
                    
                    // Other: recurse into children
                    else -> {
                        for (child in node.childNodes()) {
                            collectContent(child, result)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Adds text as paragraph or pseudo-list items (splitting by line).
     */
    private fun addParagraphOrPseudoList(text: String, result: MutableList<NormalizedElement>) {
        // Split by newlines and check each line
        val lines = text.split(Regex("[\n\r]+"))
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            val bulletMatch = PSEUDO_LIST_PATTERN.find(trimmed)
            val numberedMatch = NUMBERED_LIST_PATTERN.find(trimmed)
            
            when {
                bulletMatch != null -> {
                    result.add(NormalizedElement(ElementType.BULLET, bulletMatch.groupValues[1].trim()))
                }
                numberedMatch != null -> {
                    result.add(NormalizedElement(
                        ElementType.NUMBERED,
                        numberedMatch.groupValues[2].trim(),
                        numberedMatch.groupValues[1].toIntOrNull()
                    ))
                }
                else -> {
                    result.add(NormalizedElement(ElementType.PARAGRAPH, trimmed))
                }
            }
        }
    }
    
    /**
     * Extract all text from an element, preserving bold markers.
     */
    private fun extractAllText(element: Element): String {
        val sb = StringBuilder()
        
        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    val tag = node.tagName().lowercase()
                    if (tag in listOf("ul", "ol")) {
                        // Skip nested lists (handled separately)
                        continue
                    }
                    val innerText = extractAllText(node)
                    when (tag) {
                        "strong", "b" -> {
                            if (innerText.isNotBlank()) {
                                sb.append("**").append(innerText.trim()).append("**")
                            }
                        }
                        "em", "i" -> {
                            if (innerText.isNotBlank()) {
                                sb.append("*").append(innerText.trim()).append("*")
                            }
                        }
                        else -> sb.append(innerText)
                    }
                }
            }
        }
        
        return sb.toString().replace(Regex("\\s+"), " ")
    }
    
    /**
     * Get text content before a specific child element.
     */
    private fun getTextBeforeElement(parent: Element, target: Element): String {
        val sb = StringBuilder()
        for (node in parent.childNodes()) {
            if (node === target) break
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    if (node.tagName().lowercase() !in listOf("ul", "ol")) {
                        sb.append(node.text())
                    }
                }
            }
        }
        return sb.toString().trim()
    }
    
    /**
     * Get text content after a specific child element.
     */
    private fun getTextAfterElement(parent: Element, target: Element): String {
        val sb = StringBuilder()
        var foundTarget = false
        for (node in parent.childNodes()) {
            if (node === target) {
                foundTarget = true
                continue
            }
            if (foundTarget) {
                when (node) {
                    is TextNode -> sb.append(node.text())
                    is Element -> {
                        if (node.tagName().lowercase() !in listOf("ul", "ol")) {
                            sb.append(node.text())
                        }
                    }
                }
            }
        }
        return sb.toString().trim()
    }
    
    /**
     * Merge very short consecutive paragraphs that likely belong together.
     */
    private fun mergeFragments(elements: List<NormalizedElement>): List<NormalizedElement> {
        if (elements.isEmpty()) return elements
        
        val result = mutableListOf<NormalizedElement>()
        var pendingParagraph: StringBuilder? = null
        
        for (elem in elements) {
            when (elem.type) {
                ElementType.PARAGRAPH -> {
                    if (pendingParagraph == null) {
                        pendingParagraph = StringBuilder(elem.text)
                    } else {
                        // If both are short, merge
                        if (pendingParagraph.length < 50 && elem.text.length < 50) {
                            pendingParagraph.append(" ").append(elem.text)
                        } else {
                            // Flush pending and start new
                            result.add(NormalizedElement(ElementType.PARAGRAPH, pendingParagraph.toString()))
                            pendingParagraph = StringBuilder(elem.text)
                        }
                    }
                }
                else -> {
                    // Flush any pending paragraph
                    if (pendingParagraph != null) {
                        result.add(NormalizedElement(ElementType.PARAGRAPH, pendingParagraph.toString()))
                        pendingParagraph = null
                    }
                    result.add(elem)
                }
            }
        }
        
        // Flush remaining
        if (pendingParagraph != null) {
            result.add(NormalizedElement(ElementType.PARAGRAPH, pendingParagraph.toString()))
        }
        
        return result
    }

    // ==================== PASS 2: EXTRACT ====================
    
    /**
     * Converts normalized elements to ContentBlocks.
     */
    private fun pass2Extract(elements: List<NormalizedElement>): List<ContentBlock> {
        return elements.mapNotNull { elem ->
            when (elem.type) {
                ElementType.PARAGRAPH -> {
                    if (elem.text.isNotBlank()) {
                        ContentBlock.Paragraph(elem.text)
                    } else null
                }
                ElementType.BULLET -> {
                    if (elem.text.isNotBlank()) {
                        ContentBlock.BulletItem(elem.text)
                    } else null
                }
                ElementType.NUMBERED -> {
                    if (elem.text.isNotBlank()) {
                        ContentBlock.NumberedItem(elem.number ?: 1, elem.text)
                    } else null
                }
            }
        }
    }
}


