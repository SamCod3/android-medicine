package com.samcod3.meditrack.data.repository

import android.util.Log
import com.samcod3.meditrack.data.remote.api.CimaApiService
import com.samcod3.meditrack.data.remote.dto.MedicationDto
import com.samcod3.meditrack.domain.model.ActiveIngredient
import com.samcod3.meditrack.domain.model.Leaflet
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class DrugRepositoryImpl(
    private val cimaApi: CimaApiService
) : DrugRepository {
    
    companion object {
        private const val TAG = "DrugRepository"
        // Standard section titles prefixes
        private val SECTION_PREFIXES = listOf(
            "1. Qué es",
            "2. Qué necesita saber", "2. Antes de tomar",
            "3. Cómo tomar", "3. Cómo usar",
            "4. Posibles efectos",
            "5. Conservación",
            "6. Contenido"
        )
    }
    
    override suspend fun getMedicationByNationalCode(nationalCode: String): Result<Medication> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching medication for CN: $nationalCode")
                val response = cimaApi.getMedicationByNationalCode(nationalCode)
                
                if (response.isSuccessful && response.body() != null) {
                    val medication = response.body()!!.toDomain(nationalCode)
                    Result.success(medication)
                } else {
                    Result.failure(Exception("Medicamento no encontrado (código: ${response.code()})"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching medication", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getLeaflet(registrationNumber: String): Result<Leaflet> {
        return withContext(Dispatchers.IO) {
            try {
                // Get medication info first to have the URL if needed
                val medicationResult = getMedicationByCode(registrationNumber)
                if (medicationResult.isFailure) {
                    return@withContext Result.failure(medicationResult.exceptionOrNull()!!)
                }
                val medication = medicationResult.getOrThrow()

                Log.d(TAG, "Fetching leaflet for registration: $registrationNumber")
                
                // 1. Try fetching segmented sections from API
                val sectionDeferreds = (1..6).map { sectionNum ->
                    async {
                        try {
                            val response = cimaApi.getLeafletSection(registrationNumber, sectionNum)
                            if (response.isSuccessful) response.body() else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                
                var sections = sectionDeferreds.awaitAll()
                    .mapIndexedNotNull { index, dto ->
                        dto?.let {
                            LeafletSection(
                                number = index + 1,
                                title = it.title ?: LeafletSection.SECTION_TITLES.getOrElse(index) { "Sección ${index + 1}" },
                                content = it.content ?: ""
                            )
                        }
                    }
                
                // 2. Fallback: Parse HTML if segmented content is missing
                if (sections.isEmpty() && !medication.leafletUrl.isNullOrBlank()) {
                    Log.d(TAG, "Segmented content missing, parsing HTML from: ${medication.leafletUrl}")
                    try {
                        val parsedSections = parseHtmlLeaflet(medication.leafletUrl)
                        if (parsedSections.isNotEmpty()) {
                            sections = parsedSections
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "HTML parsing failed", e)
                    }
                }
                
                Result.success(Leaflet(medication, sections))
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching leaflet", e)
                Result.failure(e)
            }
        }
    }
    
    private suspend fun parseHtmlLeaflet(url: String): List<LeafletSection> {
        val response = cimaApi.downloadUrl(url)
        if (!response.isSuccessful || response.body() == null) return emptyList()
        
        val html = response.body()!!.string()
        val doc = Jsoup.parse(html)
        var sections = mutableListOf<LeafletSection>()
        
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
            
            if (sectionConnectors.size >= 3) { // If we found at least 3 sections via index, trust this method
                Log.d(TAG, "Using Index-Based parsing strategy")
                // Sort by section number
                sectionConnectors.sortBy { it.first }
                
                for (i in 0 until sectionConnectors.size) {
                    val (num, id) = sectionConnectors[i]
                    val nextId = if (i < sectionConnectors.size - 1) sectionConnectors[i+1].second else null
                    
                    // Find start element
                    // Can be id="id" or name="id"
                    var startEl = doc.getElementById(id)
                    if (startEl == null) {
                        startEl = doc.select("[name='$id']").first()
                    }
                    
                    if (startEl != null) {
                        val title = startEl.text().ifBlank { "Sección $num" }
                        val contentBuilder = StringBuilder()
                        
                        // Collect siblings until next ID or end
                        var current = startEl.nextElementSibling()
                        var reachedNext = false
                        
                        while (current != null) {
                            // Check if we reached next section
                            if (nextId != null) {
                                if (current.id() == nextId || current.attr("name") == nextId) {
                                    reachedNext = true
                                    break
                                }
                                // Also check descendants (sometimes the anchor is inside a div)
                                if (current.select("#$nextId, [name='$nextId']").isNotEmpty()) {
                                    reachedNext = true
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
        
        // STRATEGY 2: Fallback to Regex / Text Scanning (Previous Logic)
        Log.d(TAG, "Using Regex-Based parsing strategy")
        // ... (existing regex logic) ...
        
        val container = doc.select(".texto_prospecto").first() ?: doc.body()
        val headerRegex = Regex("""^([1-6])[\s\.\-\)]+(.*)""")
        
        var currentSectionNum = 0
        var currentTitle = ""
        var currentContent = StringBuilder()
        
        val elements = container.select("p, div, h1, h2, h3, h4, h5, h6, li, strong, b")
        
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
                    // Helper to validate title content
                    if (isValidSectionTitle(num, match.groupValues[2])) {
                        foundSectionNum = num
                        currentTitle = text
                    }
                } catch (e: Exception) {}
            }
            
            // Fallback: Check without number if regex failed but text looks like a header (e.g. "DATOS CLÍNICOS")
            // Not implemented to keep it simple, regex is usually enough.
            
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
    
    private fun getSectionNumberFromText(text: String): Int? {
        val lower = text.lowercase()
        // Check for number explicitly first "1. "
        for (i in 1..6) {
           if (text.startsWith("$i") && (text.contains(".") || text.contains("-") || text.contains(" "))) {
               if (isValidSectionTitle(i, lower)) return i
           }
        }
        // Should we check without number? Some indexes are just "Qué es..."
        if (lower.contains("qué es") || lower.contains("que es")) return 1
        if (lower.contains("antes de") || lower.contains("necesita saber")) return 2
        if (lower.contains("cómo tomar") || lower.contains("como usar") || lower.contains("posología")) return 3
        if (lower.contains("efectos adversos")) return 4
        if (lower.contains("conservación")) return 5
        if (lower.contains("contenido del envase") || lower.contains("información adicional")) return 6
        
        return null
    }
    
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


    override suspend fun getLeafletSection(registrationNumber: String, section: Int): Result<LeafletSection?> {
        // ... same as before
        return withContext(Dispatchers.IO) {
            try {
                val response = cimaApi.getLeafletSection(registrationNumber, section)
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!
                    Result.success(
                        LeafletSection(
                            number = section,
                            title = dto.title ?: LeafletSection.SECTION_TITLES.getOrElse(section - 1) { "Sección $section" },
                            content = dto.content ?: ""
                        )
                    )
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private suspend fun getMedicationByCode(registrationNumber: String): Result<Medication> {
        // Need to use getMedicationByNationalCode logic or search?
        // Actually we don't have an easy way to get medication just by registration number via API without CN.
        // But for getLeaflet we already HAVE the Medication object in the ViewModel usually.
        // However, this method signature getLeaflet takes only reg number. 
        // We cheated before by returning a dummy.
        // To get the URL, we might need to search by registration number?
        // Wait, CIMA API doesn't seem to have "get by registration number".
        // BUT, usually we call this AFTER getMedicationByNationalCode.
        // If we want the URL, we need the Medication object associated with this registration number.
        // If we don't have it (because we only passed reg number), we might fail to get the URL unless we search.
        // IMPROVEMENT: change getLeaflet to take Medication object or fetch it if possible.
        // Or assume the caller (ViewModel) has the URL and passes it.
        // Refactor: getLeaflet(medication: Medication).
        
        // But to avoid breaking Interface changes right now, let's look at getMedicationByCode usage.
        // In the previous code, getMedicationByCode returned a dummy.
        // If I want the leafletUrl, I need it.
        
        // WORKAROUND: The ViewModel has the medication.
        // But Repositories shouldn't depend on ViewModels.
        // Ideally getLeaflet interface should accept the data it needs (URL) or fetch it.
        
        // Since I'm in DrugRepositoryImpl, I can fetch by CN? I don't have CN here, only reg number.
        // BUT, I can see getMedicationByCode in previous implementation was mostly dummy.
        
        // Let's assume for this specific flow, we might need to change the interface of getLeaflet to accept `leafletUrl` as optional,
        // or accept `Medication` object.
        
        // Let's change `getLeaflet(registrationNumber: String)` to `getLeaflet(medication: Medication)`.
        // This requires updating interface and ViewModel. This is cleaner.
        return Result.failure(Exception("Use overload with Medication object"))
    }
    
    // OVERLOAD or Change Interface
    override suspend fun getLeaflet(medication: Medication): Result<Leaflet> {
         val registrationNumber = medication.registrationNumber
         // Copy logic from above, but now we have 'medication.leafletUrl' valid
         
         return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching leaflet for ${medication.name}")
                
                 val sectionDeferreds = (1..6).map { sectionNum ->
                    async {
                        try {
                            val response = cimaApi.getLeafletSection(registrationNumber, sectionNum)
                            if (response.isSuccessful) response.body() else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                
                var sections = sectionDeferreds.awaitAll()
                    .mapIndexedNotNull { index, dto ->
                        dto?.let {
                            LeafletSection(
                                number = index + 1,
                                title = it.title ?: LeafletSection.SECTION_TITLES.getOrElse(index) { "Sección ${index + 1}" },
                                content = it.content ?: ""
                            )
                        }
                    }
                
                if (sections.isEmpty() && !medication.leafletUrl.isNullOrBlank()) {
                     Log.d(TAG, "Parsing HTML from ${medication.leafletUrl}")
                     val parsed = parseHtmlLeaflet(medication.leafletUrl!!)
                     if (parsed.isNotEmpty()) sections = parsed
                }
                
                Result.success(Leaflet(medication, sections))
            } catch (e: Exception) {
                Result.failure(e)
            }
         }
    }

    private fun MedicationDto.toDomain(nationalCode: String?): Medication {
        return Medication(
            registrationNumber = registrationNumber ?: "",
            name = name ?: "Sin nombre",
            laboratory = laboratory ?: "",
            prescriptionRequired = prescriptionCondition?.isNotEmpty() == true,
            affectsDriving = affectsDriving ?: false,
            hasWarningTriangle = hasTriangle ?: false,
            activeIngredients = activeIngredients?.map {
                ActiveIngredient(
                    name = it.name ?: "",
                    quantity = it.quantity ?: "",
                    unit = it.unit ?: ""
                )
            } ?: emptyList(),
            leafletUrl = documents?.find { it.type == 2 }?.htmlUrl,
            photoUrl = photos?.firstOrNull()?.url,
            nationalCode = nationalCode ?: presentations?.firstOrNull()?.nationalCode
        )
    }
}
