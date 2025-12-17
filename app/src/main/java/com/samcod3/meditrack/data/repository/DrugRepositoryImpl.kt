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
        val sections = mutableListOf<LeafletSection>()
        
        // Find body content
        val body = doc.body()
        
        // Strategy: Find standard headers and split content
        // CIMA HTMLs often structure sections with <p class="epigrafe"> or <strong> or <h3>
        // We look for elements starting with "1. ", "2. ", etc.
        
        val elements = body.allElements
        var currentSectionNum = 0
        var currentTitle = ""
        var currentContent = StringBuilder()
        
        for (element in elements) {
            val text = element.ownText().trim()
            val isHeader = SECTION_PREFIXES.any { prefix -> text.startsWith(prefix, ignoreCase = true) }
            
            // Heuristic for header: starts with "X. ", short length, usually strong or p class=epigrafe
            val nextSectionNum = getSectionNumber(text)
            
            if (nextSectionNum != null && nextSectionNum > currentSectionNum) {
                // Save previous section
                if (currentSectionNum > 0) {
                    sections.add(
                        LeafletSection(
                            number = currentSectionNum,
                            title = currentTitle,
                            content = currentContent.toString()
                        )
                    )
                }
                
                // Start new section
                currentSectionNum = nextSectionNum
                currentTitle = text
                currentContent = StringBuilder()
                // Don't add the header itself to content, or maybe add it as h3?
                // Let's not add it to content to avoid duplication if we use title separately
            } else if (currentSectionNum > 0) {
                // Append content to current section, preserving basic HTML structure
                // We append the outerHtml of relevant block elements
                // BUT: allowElements iterates EVERYTHING (parents and children). We prefer direct children of body or main container.
                // This 'allElements' loop is too granular and will duplicate content.
                // Better strategy: Iterate direct children of main container.
            }
        }
        
        // BETTER STRATEGY: Iterate direct children
        sections.clear()
        currentSectionNum = 0
        currentContent = StringBuilder()
        
        // Find the main container (usually just body or a div with class 'texto_prospecto' or similar)
        // CIMA structure varies, traversing body children is safest generic approach
        val children = body.children()
        
        for (child in children) {
            val text = child.text().trim()
            val nextSectionNum = getSectionNumber(text)
            
            if (nextSectionNum != null && nextSectionNum > currentSectionNum) {
                 if (currentSectionNum > 0) {
                    sections.add(
                        LeafletSection(number = currentSectionNum, title = currentTitle, content = currentContent.toString())
                    )
                }
                currentSectionNum = nextSectionNum
                currentTitle = text
                currentContent = StringBuilder()
            } else if (currentSectionNum > 0) {
                currentContent.append(child.outerHtml())
            }
        }
        
        // Add last section
        if (currentSectionNum > 0) {
            sections.add(
                LeafletSection(number = currentSectionNum, title = currentTitle, content = currentContent.toString())
            )
        }
        
        return sections
    }
    
    private fun getSectionNumber(text: String): Int? {
        if (text.length > 100) return null // Headers aren't huge paragraphs
        
        for (i in 1..6) {
            if (text.startsWith("$i. ") || text.startsWith("$i.- ") || text.startsWith("$i ")) {
                // Verify it matches expected keywords to avoid false positives (e.g. "1.5 mg")
                val lower = text.lowercase()
                if (i == 1 && lower.contains("qué es")) return 1
                if (i == 2 && (lower.contains("necesita saber") || lower.contains("antes de"))) return 2
                if (i == 3 && (lower.contains("cómo") || lower.contains("tomar") || lower.contains("usar"))) return 3
                if (i == 4 && (lower.contains("efectos") || lower.contains("adversos"))) return 4
                if (i == 5 && lower.contains("conservación")) return 5
                if (i == 6 && (lower.contains("contenido") || lower.contains("envase"))) return 6
            }
        }
        return null
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
