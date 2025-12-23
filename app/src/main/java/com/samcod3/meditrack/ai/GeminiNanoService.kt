package com.samcod3.meditrack.ai

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.samcod3.meditrack.domain.model.ParsedMedication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import com.samcod3.meditrack.domain.model.ParsedLeaflet
import com.samcod3.meditrack.domain.model.ParsedSection
import com.samcod3.meditrack.domain.model.ContentBlock
import com.samcod3.meditrack.domain.model.LeafletSummary
import org.jsoup.Jsoup


/**
 * AI Service implementation using Gemini Nano on-device model via ML Kit Prompt API.
 * Uses Generation.getClient() to obtain GenerativeModel for local inference.
 * 
 * IMPORTANT: All initialization is done lazily and asynchronously to avoid ANR.
 */
class GeminiNanoService : AIService {
    
    companion object {
        private const val TAG = "GeminiNanoService"
        
        private const val EXTRACTION_PROMPT = """Analyze this Spanish medication box OCR text and extract information in JSON format.

Extract these 4 fields:
- nombre: Medication name = BRAND + ACTIVE INGREDIENT only. NO modifiers, NO dosage, NO presentation.
- modificador: Optional modifier like FORTE, FLAS, RAPID, RETARD, PLUS. Empty string if none.
- dosis: Dosage amount (e.g., "600 mg", "1 g", "10 mg/ml"). Just the number + unit.
- presentacion: Pharmaceutical form (comprimidos, cápsulas, sobres, jarabe, gotas, polvo). One word.

Examples:
OCR: "CINFAMUCOL ACETILCISTEINA FORTE 600 mg sobres efervescentes"
Output: {"nombre": "CINFAMUCOL ACETILCISTEINA", "modificador": "FORTE", "dosis": "600 mg", "presentacion": "sobres"}

OCR: "IBUPROFENO CINFA 600 mg comprimidos recubiertos"
Output: {"nombre": "IBUPROFENO CINFA", "modificador": "", "dosis": "600 mg", "presentacion": "comprimidos"}

OCR: "NOLOTIL 575 mg cápsulas duras"
Output: {"nombre": "NOLOTIL", "modificador": "", "dosis": "575 mg", "presentacion": "cápsulas"}

OCR: "ASPIRINA PLUS 500 mg/50 mg comprimidos efervescentes"
Output: {"nombre": "ASPIRINA", "modificador": "PLUS", "dosis": "500 mg/50 mg", "presentacion": "comprimidos"}

Return ONLY valid JSON, nothing else.

OCR Text:
"""

    
    private const val LEAFLET_FRAGMENT_PROMPT = """Analyze this HTML fragment from a medication leaflet.
Structure the content into blocks:
- Normal paragraphs -> "p"
- Bold text -> "b"
- Italic text -> "i"
- List items -> "li"
- Numbered items -> "ol"
- Subheadings (titles within text) -> "h"

Return ONLY a JSON array of blocks:
[
  {"t": "p", "v": "Text..."},
  {"t": "b", "v": "Bold text..."},
  {"t": "li", "v": "List item..."},
  {"t": "h", "v": "Subheading..."}
]

HTML Fragment:
"""
    private const val SUMMARY_PROMPT = """Actúa como un farmacéutico experto y resume este prospecto en ESPAÑOL.
Extrae estos 3 puntos clave en formato JSON estricto:

{
  "indications": "Para qué sirve (muy breve)",
  "dosage": "Cómo tomarlo (resumen dosis)",
  "warnings": "Precauciones clave"
}

Reglas:
1. Responde SOLO con el JSON.
2. Usa texto plano sin markdown.
3. Sé muy conciso (máx 1 frase por campo).

HTML:
"""

        private const val TREATMENT_PROMPT = """Extrae medicamentos de este texto de tratamiento médico.

Por cada medicamento devuelve JSON con:
- name: Nombre completo del medicamento (tal cual aparece)
- dosage: Dosis y forma (ej: "1 comprimido", "10 mg")
- times: Array de horas en formato "HH:MM" (ej: ["08:00", "14:00"])
- frequency: "DAILY" o "WEEKLY:L,M,X" o "MONTHLY:15" o "INTERVAL:3"

Ejemplo respuesta:
[
  {"name": "PARACETAMOL ABAMED 1G", "dosage": "1 comprimido", "times": ["08:00", "20:00"], "frequency": "DAILY"},
  {"name": "IBUPROFENO CINFA 600 MG", "dosage": "1 comprimido", "times": ["14:00"], "frequency": "DAILY"}
]

REGLAS:
1. Responde SOLO con el JSON array.
2. Extrae TODOS los medicamentos que encuentres.
3. Si no hay hora exacta, usa ["08:00"].
4. Si no se especifica frecuencia, usa "DAILY".

Texto:
"""
    }
    
    private var generativeModel: GenerativeModel? = null
    private var featureStatus: Int = FeatureStatus.UNAVAILABLE
    private var initialized = false
    
    override suspend fun extractMedicationName(ocrText: String): AIResult {
        // Lazy init on first use (in coroutine context, not blocking)
        if (!initialized) {
            initializeModel()
        }
        
        if (featureStatus != FeatureStatus.AVAILABLE || generativeModel == null) {
            Log.d(TAG, "Gemini Nano not available (status: ${featureStatusToString(featureStatus)})")
            return AIResult(text = "", usedAI = false)
        }
        
        return try {
            val fullPrompt = "$EXTRACTION_PROMPT$ocrText"
            
            val response = withContext(Dispatchers.IO) {
                generativeModel!!.generateContent(fullPrompt)
            }
            
            // Get text from first candidate
            val rawResponse = response.candidates
                .firstOrNull()
                ?.text
                ?.trim()
                ?: ""
            
            Log.d(TAG, "Gemini Nano raw response: '$rawResponse'")
            
            // Parse JSON response
            parseJsonResponse(rawResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error using Gemini Nano", e)
            AIResult(text = "", usedAI = false)
        }
    }
    
    /**
     * Parse JSON response from Gemini and create AIResult
     */
    private fun parseJsonResponse(rawResponse: String): AIResult {
        return try {
            // Clean up response - remove markdown code blocks if present
            val jsonString = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            val json = JSONObject(jsonString)
            
            val nombre = json.optString("nombre", "").trim()
            val modificador = json.optString("modificador", "").trim()
            val dosis = json.optString("dosis", "").trim()
            val presentacion = json.optString("presentacion", "").trim()
            
            // Build full text for display (name + modifier + dosage)
            val fullText = buildString {
                append(nombre)
                if (modificador.isNotEmpty()) append(" $modificador")
                if (dosis.isNotEmpty()) append(" $dosis")
            }
            
            Log.d(TAG, "Parsed: nombre='$nombre', modificador='$modificador', dosis='$dosis', presentacion='$presentacion'")
            
            AIResult(
                text = fullText,
                usedAI = true,
                name = nombre,
                modifier = modificador.ifEmpty { null },
                dosage = dosis.ifEmpty { null },
                presentation = presentacion.ifEmpty { null }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON, using raw response: $rawResponse", e)
            // Fallback: treat entire response as name
            AIResult(
                text = rawResponse,
                usedAI = true,
                name = rawResponse
            )
        }
    }
    
    /**
     * Analyze CIMA search results to extract unique filter options
     */
    override suspend fun analyzeResults(medicationNames: List<String>): ResultsAnalysis {
        if (!initialized) {
            initializeModel()
        }
        
        if (featureStatus != FeatureStatus.AVAILABLE || generativeModel == null || medicationNames.isEmpty()) {
            return ResultsAnalysis()
        }
        
        return try {
            val namesText = medicationNames.joinToString("\n")
            val prompt = """Extract EXACT text fragments from these Spanish medication names.

CRITICAL: Copy text EXACTLY as it appears. Do NOT simplify or interpret.

For each medication name, identify:
1. DOSIS: The dosage part (e.g., "600 mg", "20 mg/ml", "500 mg/50 mg")
2. PRESENTACION: Everything AFTER the dosage (the pharmaceutical form)

Example input:
ACETILCISTEINA CINFA 600 mg polvo para solución oral sobres
IBUPROFENO KERN PHARMA 400 mg comprimidos recubiertos con película
NOLOTIL 575 mg cápsulas duras

Example output:
{"dosis": ["600 mg", "400 mg", "575 mg"], "presentacion": ["polvo para solución oral sobres", "comprimidos recubiertos con película", "cápsulas duras"]}

RULES:
- Copy EXACTLY as written, including accents and full phrases
- "presentacion" must be the COMPLETE text after dosage, not just one word
- Remove duplicates

Medication names:
$namesText

Return ONLY valid JSON."""

            val response = withContext(Dispatchers.IO) {
                generativeModel!!.generateContent(prompt)
            }
            
            val rawResponse = response.candidates
                .firstOrNull()
                ?.text
                ?.trim()
                ?: ""
            
            Log.d(TAG, "Analysis response: '$rawResponse'")
            
            parseAnalysisResponse(rawResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing results", e)
            ResultsAnalysis()
        }
    }
    
    /**
     * Parse analysis JSON response
     */
    private fun parseAnalysisResponse(rawResponse: String): ResultsAnalysis {
        return try {
            val jsonString = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            val json = JSONObject(jsonString)
            
            val dosisArray = json.optJSONArray("dosis")
            val presentacionArray = json.optJSONArray("presentacion")
            
            val dosages = mutableListOf<String>()
            val presentations = mutableListOf<String>()
            
            dosisArray?.let {
                for (i in 0 until it.length()) {
                    dosages.add(it.getString(i))
                }
            }
            
            presentacionArray?.let {
                for (i in 0 until it.length()) {
                    presentations.add(it.getString(i))
                }
            }
            
            Log.d(TAG, "Analyzed: dosages=$dosages, presentations=$presentations")
            
            // Deduplicate and trim
            ResultsAnalysis(
                dosages = dosages.map { it.trim() }.distinct(),
                presentations = presentations.map { it.trim() }.distinct()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse analysis JSON: $rawResponse", e)
            ResultsAnalysis()
        }
    }
    
    /**
     * Tool Calling: Process OCR text and return Search action
     * Uses existing extraction logic but returns ToolAction instead of AIResult
     */
    override suspend fun processOcrText(ocrText: String): ToolAction {
        val result = extractMedicationName(ocrText)
        
        return if (result.usedAI && result.name.isNotBlank()) {
            ToolAction.Search(
                query = result.name,
                modifier = result.modifier,
                scannedDosage = result.dosage,
                scannedPresentation = result.presentation
            )
        } else {
            ToolAction.Error("Could not extract medication name from OCR")
        }
    }
    
    /**
     * Tool Calling: Process CIMA results and return Complete action with filters
     * Reuses analyzeResults logic but returns ToolAction
     */
    override suspend fun processSearchResults(
        originalQuery: String,
        resultNames: List<String>,
        scannedDosage: String?
    ): ToolAction {
        if (resultNames.isEmpty()) {
            return ToolAction.Complete(
                searchQuery = originalQuery,
                filters = ResultsAnalysis(),
                autoApplyDosage = scannedDosage
            )
        }
        
        val analysis = analyzeResults(resultNames)
        
        return ToolAction.Complete(
            searchQuery = originalQuery,
            filters = analysis,
            autoApplyDosage = scannedDosage
        )
    }
    
    override fun isAvailable(): Boolean {
        // Non-blocking check - returns current known state
        // Actual initialization happens in extractMedicationName
        return initialized && featureStatus == FeatureStatus.AVAILABLE
    }
    
    private suspend fun initializeModel() {
        if (initialized) return
        
        try {
            generativeModel = Generation.getClient()
            featureStatus = generativeModel?.checkStatus() ?: FeatureStatus.UNAVAILABLE
            
            Log.d(TAG, "Gemini Nano initialized. Status: ${featureStatusToString(featureStatus)}")
            
            // If downloadable, trigger download (non-blocking)
            if (featureStatus == FeatureStatus.DOWNLOADABLE) {
                Log.d(TAG, "Gemini Nano model is downloadable")
            }
            
            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Gemini Nano", e)
            featureStatus = FeatureStatus.UNAVAILABLE
            initialized = true
        }
    }
    
    private fun featureStatusToString(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "UNKNOWN($status)"
    }

    /**
     * Parses leaflet HTML to extract structured sections using Gemini Nano.
     */
    /**
     * Generates a leaflet summary by processing sections individually.
     * This respects Gemini Nano's 1024 token limit per prompt.
     * 
     * Strategy: Extract key info from each relevant section, then combine.
     */
    override suspend fun generateLeafletSummary(html: String): LeafletSummary? {
        if (!initialized) {
            initializeModel()
        }

        if (featureStatus != FeatureStatus.AVAILABLE || generativeModel == null) {
            return null
        }

        return try {
            // Parse sections using LeafletHtmlParser
            val sections = com.samcod3.meditrack.data.remote.parser.LeafletHtmlParser.parse(html)
            
            if (sections.isEmpty()) {
                Log.w(TAG, "No sections found for summary")
                return null
            }
            
            // Map section numbers to summary fields
            // Section 1: Qué es y para qué → indications
            // Section 3: Cómo tomar → dosage  
            // Section 2 or 4: Advertencias/Efectos → warnings
            
            var indications = ""
            var dosage = ""
            var warnings = ""
            
            // Process relevant sections individually (respecting token limits)
            for (section in sections) {
                val cleanContent = cleanHtmlForAI(section.content)
                // Limit each section to ~3000 chars (~750 tokens) to stay under 1024 with prompt
                val truncatedContent = cleanContent.take(3000)
                
                if (truncatedContent.isBlank()) continue
                
                when (section.number) {
                    1 -> {
                        // Qué es y para qué se utiliza
                        indications = extractSectionSummary(truncatedContent, "indicaciones")
                    }
                    3 -> {
                        // Cómo tomar
                        dosage = extractSectionSummary(truncatedContent, "posología")
                    }
                    2 -> {
                        // Antes de tomar (advertencias)
                        warnings = extractSectionSummary(truncatedContent, "advertencias")
                    }
                    4 -> {
                        // Efectos adversos (solo si no tenemos warnings del section 2)
                        if (warnings.isBlank()) {
                            warnings = extractSectionSummary(truncatedContent, "efectos adversos")
                        }
                    }
                }
                
                // Early exit if we have all three
                if (indications.isNotBlank() && dosage.isNotBlank() && warnings.isNotBlank()) {
                    break
                }
            }
            
            // Only return summary if we got at least indications
            if (indications.isNotBlank()) {
                LeafletSummary(
                    indications = indications,
                    dosage = dosage.ifBlank { "Ver sección 3 del prospecto" },
                    warnings = warnings.ifBlank { "Ver sección 2 del prospecto" }
                )
            } else {
                Log.w(TAG, "Could not extract indications from sections")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating summary from sections", e)
            null
        }
    }
    
    /**
     * Extract a useful summary from a section for a specific field.
     * Prompts are tailored per field to get more specific information.
     */
    private suspend fun extractSectionSummary(sectionContent: String, field: String): String {
        val prompt = when (field) {
            "indicaciones" -> """De este texto de prospecto, extrae PARA QUÉ SIRVE el medicamento.
Responde en 1-2 frases claras y directas. Ejemplo: "Alivio del dolor leve a moderado y fiebre."

Texto:
$sectionContent"""
            "posología" -> """De este texto de prospecto, extrae CÓMO SE TOMA el medicamento (dosis y frecuencia).
Responde en 1-2 frases. Ejemplo: "1 comprimido cada 8 horas. Máximo 3 al día."

Texto:
$sectionContent"""
            "advertencias" -> """De este texto de prospecto, extrae las PRECAUCIONES más importantes (contraindicaciones, interacciones, embarazo).
Responde en 1-2 frases. Ejemplo: "No tomar con alcohol. Contraindicado en embarazo y úlcera gástrica."

Texto:
$sectionContent"""
            "efectos adversos" -> """De este texto de prospecto, extrae los EFECTOS SECUNDARIOS más frecuentes.
Responde en 1-2 frases. Ejemplo: "Puede causar náuseas, dolor de cabeza y mareos."

Texto:
$sectionContent"""
            else -> """Resume este texto en 1-2 frases:
$sectionContent"""
        }

        return try {
            val response = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(15_000L) { // 15 second timeout
                    generativeModel!!.generateContent(prompt)
                }
            }
            
            val text = response.candidates.firstOrNull()?.text?.trim() ?: ""
            // Clean up response - remove quotes
            text.removeSurrounding("\"")
                .replace("\n", " ")
                .take(300) // Allow longer responses
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Timeout extracting $field")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting $field", e)
            ""
        }
    }

    override suspend fun structureHtmlContent(htmlFragment: String): List<ContentBlock> {
        if (!initialized) {
            initializeModel()
        }

        if (featureStatus != FeatureStatus.AVAILABLE || generativeModel == null) {
            return emptyList()
        }

        return try {
            // Pre-process HTML to reduce size and noise
            val cleanHtml = cleanHtmlForAI(htmlFragment)
            
            // Limit HTML size - safer limit for fragments (approx 1500 tokens)
            val truncatedHtml = if (cleanHtml.length > 6000) {
                cleanHtml.substring(0, 6000) + "..."
            } else {
                cleanHtml
            }

            val response = withContext(Dispatchers.IO) {
                generativeModel!!.generateContent(LEAFLET_FRAGMENT_PROMPT + truncatedHtml)
            }

            val rawResponse = response.candidates
                .firstOrNull()
                ?.text
                ?.trim()
                ?: ""

            Log.d(TAG, "Fragment structure response length: ${rawResponse.length}")
            
            parseContentBlocksResponse(rawResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error structuring content with AI", e)
            emptyList()
        }
    }

    private fun cleanHtmlForAI(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            
            // Remove scripts, styles, comments
            doc.select("script, style, meta, link, header, footer, nav, iframe").remove()
            
            // Remove attributes that add noise but no semantic value
            doc.select("*").removeAttr("style").removeAttr("class").removeAttr("width").removeAttr("height")
            
            // Get text-heavy content (usually inside main containers)
            val body = doc.body()
            
            // Use lighter HTML structure
            body.html()
                .replace(Regex("\\s+"), " ") // Normalize whitespace
                .replace(Regex("<!--.*?-->"), "") // Remove comments
                .trim()
        } catch (e: Exception) {
            html // Fallback to raw HTML
        }
    }

    private fun parseContentBlocksResponse(rawResponse: String): List<ContentBlock> {
        return try {
            val jsonString = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // Handle case where AI might return an object instead of array (sometimes happens)
            val jsonArray = if (jsonString.startsWith("{")) {
                JSONObject(jsonString).optJSONArray("blocks") ?: org.json.JSONArray()
            } else {
                org.json.JSONArray(jsonString)
            }
            
            val contentBlocks = mutableListOf<ContentBlock>()
            
            for (j in 0 until jsonArray.length()) {
                val blockJson = jsonArray.getJSONObject(j)
                val type = blockJson.optString("t")
                val value = blockJson.optString("v")
                
                val block = when (type) {
                    "p" -> ContentBlock.Paragraph(value)
                    "b" -> ContentBlock.Bold(value)
                    "i" -> ContentBlock.Italic(value)
                    "li" -> ContentBlock.BulletItem(value)
                    "ol" -> {
                        val num = blockJson.optInt("n", 1)
                        ContentBlock.NumberedItem(num, value)
                    }
                    "h" -> ContentBlock.SubHeading(value)
                    else -> ContentBlock.Paragraph(value)
                }
                
                contentBlocks.add(block)
            }
            
            contentBlocks
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse content blocks JSON", e)
            emptyList()
        }
    }
    
    override suspend fun parseTreatmentText(text: String): List<ParsedMedication> {
        if (!initialized) {
            initializeModel()
        }
        
        if (featureStatus != FeatureStatus.AVAILABLE || generativeModel == null) {
            Log.w(TAG, "Gemini Nano not available for treatment parsing")
            return emptyList()
        }
        
        return try {
            val allMedications = mutableListOf<ParsedMedication>()
            
            // Split text into chunks to avoid Gemini truncating output
            val chunks = splitIntoChunks(text, maxChunkSize = 1000)
            Log.d(TAG, "Processing ${chunks.size} text chunks for medications")
            
            for ((index, chunk) in chunks.withIndex()) {
                if (chunk.isBlank()) continue
                
                Log.d(TAG, "Processing chunk ${index + 1}/${chunks.size}: ${chunk.take(50)}...")
                
                try {
                    val response = withContext(Dispatchers.IO) {
                        generativeModel!!.generateContent(TREATMENT_PROMPT + chunk)
                    }
                    
                    val responseText = response.candidates.firstOrNull()?.text?.trim() ?: ""
                    Log.d(TAG, "Chunk $index response: ${responseText.take(100)}...")
                    
                    if (responseText.isBlank()) continue
                    
                    // Parse this chunk's medications
                    val chunkMeds = parseJsonToMedications(responseText)
                    allMedications.addAll(chunkMeds)
                    Log.d(TAG, "Chunk $index yielded ${chunkMeds.size} medications")
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing chunk $index: ${e.message}")
                }
            }
            
            // Remove duplicates by name
            val uniqueMeds = allMedications
                .distinctBy { it.name.uppercase() }
                .filter { it.name.isNotBlank() }
            
            Log.d(TAG, "Total unique medications parsed: ${uniqueMeds.size}")
            uniqueMeds
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing treatment text", e)
            emptyList()
        }
    }
    
    /**
     * Splits text into chunks, trying to split on line breaks.
     */
    private fun splitIntoChunks(text: String, maxChunkSize: Int): List<String> {
        val lines = text.lines().filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        
        for (line in lines) {
            if (currentChunk.length + line.length > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder()
            }
            currentChunk.appendLine(line)
        }
        
        if (currentChunk.isNotBlank()) {
            chunks.add(currentChunk.toString())
        }
        
        return chunks.ifEmpty { listOf(text.take(maxChunkSize)) }
    }
    
    /**
     * Parses JSON response from a single chunk into medications.
     */
    private fun parseJsonToMedications(responseText: String): List<ParsedMedication> {
        val medications = mutableListOf<ParsedMedication>()
        
        // Extract JSON array
        val jsonStart = responseText.indexOf('[')
        var jsonEnd = responseText.lastIndexOf(']')
        
        if (jsonStart == -1) {
            // Try individual objects
            return parseIndividualMedications(responseText)
        }
        
        // If no closing bracket, try to repair
        val jsonString = if (jsonEnd == -1 || jsonEnd <= jsonStart) {
            repairTruncatedJsonArray(responseText.substring(jsonStart))
        } else {
            responseText.substring(jsonStart, jsonEnd + 1)
        }
        
        try {
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    
                    val times = mutableListOf<String>()
                    val timesArray = obj.optJSONArray("times")
                    if (timesArray != null) {
                        for (j in 0 until timesArray.length()) {
                            times.add(timesArray.getString(j))
                        }
                    }
                    if (times.isEmpty()) times.add("08:00")
                    
                    medications.add(
                        ParsedMedication(
                            name = obj.optString("name", "").trim(),
                            dosage = obj.optString("dosage", "1 comprimido").trim(),
                            times = times,
                            frequency = obj.optString("frequency", "DAILY").trim()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed medication at index $i")
                }
            }
        } catch (e: org.json.JSONException) {
            Log.w(TAG, "JSON array parsing failed, trying individual objects")
            medications.addAll(parseIndividualMedications(jsonString))
        }
        
        return medications
    }
    
    /**
     * Attempts to repair a truncated JSON array by closing unclosed brackets.
     */
    private fun repairTruncatedJsonArray(truncatedJson: String): String {
        var json = truncatedJson.trimEnd()
        
        // Remove any trailing incomplete object after the last comma
        val lastCompleteObject = json.lastIndexOf("},")
        if (lastCompleteObject != -1 && lastCompleteObject > json.lastIndexOf("}]")) {
            json = json.substring(0, lastCompleteObject + 1)
        }
        
        // Count open/close brackets and add missing ones
        var openBrackets = 0
        var openBraces = 0
        for (c in json) {
            when (c) {
                '[' -> openBrackets++
                ']' -> openBrackets--
                '{' -> openBraces++
                '}' -> openBraces--
            }
        }
        
        // Close any open braces first, then brackets
        repeat(openBraces) { json += "}" }
        repeat(openBrackets) { json += "]" }
        
        Log.d(TAG, "Repaired JSON ends with: ...${json.takeLast(50)}")
        return json
    }
    
    /**
     * Parses individual complete medication objects from a partially valid JSON string.
     */
    private fun parseIndividualMedications(jsonString: String): List<ParsedMedication> {
        val medications = mutableListOf<ParsedMedication>()
        
        // Find all complete {...} objects using regex
        val objectPattern = Regex("\\{[^{}]*\"name\"[^{}]*\\}")
        
        objectPattern.findAll(jsonString).forEach { match ->
            try {
                val obj = JSONObject(match.value)
                
                val times = mutableListOf<String>()
                val timesArray = obj.optJSONArray("times")
                if (timesArray != null) {
                    for (j in 0 until timesArray.length()) {
                        times.add(timesArray.getString(j))
                    }
                }
                if (times.isEmpty()) times.add("08:00")
                
                medications.add(
                    ParsedMedication(
                        name = obj.optString("name", "").trim(),
                        dosage = obj.optString("dosage", "1 comprimido").trim(),
                        times = times,
                        frequency = obj.optString("frequency", "DAILY").trim()
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse individual medication: ${match.value.take(50)}")
            }
        }
        
        Log.d(TAG, "Fallback parsing recovered ${medications.size} medications")
        return medications
    }
    
    /**
     * Generate a text response from any prompt.
     * Used for summarization and other text generation tasks.
     * 
     * NOTE: Gemini Nano requires the app to be in TOP foreground.
     * Implements exponential backoff for BUSY errors (ErrorCode 9).
     */
    override suspend fun generateTextResponse(prompt: String): String? {
        if (!initialized) {
            initializeModel()
        }
        
        if (featureStatus != FeatureStatus.AVAILABLE || generativeModel == null) {
            Log.d(TAG, "generateTextResponse: Gemini Nano not available")
            return null
        }
        
        // Exponential backoff with max 3 retries
        val maxRetries = 3
        var lastError: Exception? = null
        
        for (attempt in 0 until maxRetries) {
            try {
                if (attempt > 0) {
                    // Exponential backoff: 1s, 2s, 4s
                    val delayMs = (1000L * (1 shl (attempt - 1)))
                    Log.d(TAG, "generateTextResponse: Retry $attempt after ${delayMs}ms")
                    kotlinx.coroutines.delay(delayMs)
                }
                
                val response = withContext(Dispatchers.Default) {
                    generativeModel!!.generateContent(prompt)
                }
                
                val result = response.candidates
                    .firstOrNull()
                    ?.text
                    ?.trim()
                
                if (result != null) {
                    Log.d(TAG, "generateTextResponse: Got ${result.length} chars on attempt $attempt")
                    return result
                }
            } catch (e: Exception) {
                lastError = e
                val errorMessage = e.message ?: ""
                
                // Check if it's a BUSY error (ErrorCode 9) - worth retrying
                if (errorMessage.contains("ErrorCode 9") || 
                    errorMessage.contains("BUSY") ||
                    errorMessage.contains("quota")) {
                    Log.w(TAG, "generateTextResponse: BUSY/quota error on attempt $attempt, will retry")
                    continue
                }
                
                // Other errors - don't retry
                Log.e(TAG, "generateTextResponse: Non-retryable error", e)
                break
            }
        }
        
        Log.e(TAG, "generateTextResponse: All retries failed", lastError)
        return null
    }
    
    /**
     * Generate text response with progress callback for retries.
     */
    override suspend fun generateTextResponseWithProgress(
        prompt: String,
        onRetry: (attempt: Int, maxRetries: Int) -> Unit
    ): String? {
        if (!isAvailable()) {
            Log.d(TAG, "generateTextResponseWithProgress: Gemini Nano not available")
            return null
        }
        
        val maxRetries = 3
        var lastError: Exception? = null
        
        for (attempt in 0 until maxRetries) {
            try {
                if (attempt > 0) {
                    // Notify UI about retry
                    onRetry(attempt, maxRetries)
                    
                    // Exponential backoff: 1s, 2s, 4s
                    val delayMs = (1000L * (1 shl (attempt - 1)))
                    Log.d(TAG, "generateTextResponseWithProgress: Retry $attempt after ${delayMs}ms")
                    kotlinx.coroutines.delay(delayMs)
                }
                
                val response = withContext(Dispatchers.Default) {
                    generativeModel!!.generateContent(prompt)
                }
                
                val result = response.candidates
                    .firstOrNull()
                    ?.text
                    ?.trim()
                
                if (result != null) {
                    Log.d(TAG, "generateTextResponseWithProgress: Got ${result.length} chars on attempt $attempt")
                    return result
                }
            } catch (e: Exception) {
                lastError = e
                val errorMessage = e.message ?: ""
                
                // Check if it's a BUSY error (ErrorCode 9) - worth retrying
                if (errorMessage.contains("ErrorCode 9") || 
                    errorMessage.contains("BUSY") ||
                    errorMessage.contains("quota")) {
                    Log.w(TAG, "generateTextResponseWithProgress: BUSY/quota error on attempt $attempt, will retry")
                    continue
                }
                
                // Other errors - don't retry
                Log.e(TAG, "generateTextResponseWithProgress: Non-retryable error", e)
                break
            }
        }
        
        Log.e(TAG, "generateTextResponseWithProgress: All retries failed", lastError)
        return null
    }
}
