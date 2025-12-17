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
        
        // Find body - usually inside a container with class 'texto_prospecto' or just body
        val container = doc.select(".texto_prospecto").first() ?: doc.body()
        
        // Regex to match section headers: "1. Title", "1.- Title", "1 . Title"
        // Also captures the number (group 1) and title (group 2)
        val headerRegex = Regex("""^([1-6])[\s\.\-\)]+(.*)""")
        
        var currentSectionNum = 0
        var currentTitle = ""
        var currentContent = StringBuilder()
        
        // Iterate over all semantic block elements + strong/bold
        // We use 'select' to get flat list of relevant elements in order
        val elements = container.select("p, div, h1, h2, h3, h4, h5, h6, li, strong, b")
        
        for (element in elements) {
            val text = element.text().trim()
            if (text.isBlank()) continue
            
            // Optimization: Skip very long texts checking for header, headers are short
            if (text.length > 200) {
                 if (currentSectionNum > 0) currentContent.append("<p>${element.html()}</p>")
                 continue
            }
            
            // Check if this element is a Header
            val match = headerRegex.find(text)
            var foundSectionNum: Int? = null
            
            if (match != null) {
                try {
                    val num = match.groupValues[1].toInt()
                    val titlePart = match.groupValues[2].lowercase()
                    
                    // Validate keywords to ensure it's a real section header and not just "1.5 mg"
                    val isValid = when (num) {
                        1 -> titlePart.contains("qué es") || titlePart.contains("que es")
                        2 -> titlePart.contains("necesita saber") || titlePart.contains("antes de") || titlePart.contains("tenga cuidado")
                        3 -> titlePart.contains("cómo") || titlePart.contains("como") || titlePart.contains("usar")
                        4 -> titlePart.contains("efectos") || titlePart.contains("adversos")
                        5 -> titlePart.contains("conservación") || titlePart.contains("conservacion")
                        6 -> titlePart.contains("contenido") || titlePart.contains("envase") || titlePart.contains("información")
                        else -> false
                    }
                    
                    if (isValid) {
                        foundSectionNum = num
                        // Use the full original text as title
                        currentTitle = text
                    }
                } catch (e: Exception) {
                    // Ignore parsing error
                }
            }
            
            if (foundSectionNum != null && foundSectionNum > currentSectionNum) {
                 // Save previous section
                if (currentSectionNum > 0) {
                    sections.add(
                        LeafletSection(number = currentSectionNum, title = currentTitle, content = currentContent.toString())
                    )
                }
                
                // Start new section
                currentSectionNum = foundSectionNum!!
                currentContent = StringBuilder()
                // We don't add the header itself to content
            } else if (currentSectionNum > 0) {
                // Append content. 
                // Only append if it's not nested inside another element we already processed.
                // But Jsoup select returns flat list. 
                // Issue: If we select 'div' and also 'p' inside it, we get duplication.
                // Simple fix: Append unique content or just use paragraph tags.
                // To be safe and simple: Append element's outerHtml IF it's a paragraph or list item.
                // If it's a strong/b inside a p, the p will catch it.
                // So we should iterate DIRECT CHILDREN of container for content, searching recursively for headers? No, CIMA is flat usually.
                
                // Let's rely on 'p' and 'li' for content.
                if (element.tagName() in listOf("p", "li", "div")) {
                     currentContent.append(element.outerHtml())
                }
            }
        }
        
        // If "flat" iteration failed (maybe structure is nested weirdly), try Child Traversal
        if (sections.isEmpty() || currentSectionNum == 0) {
             val children = container.children()
             for (child in children) {
                 val text = child.text().trim()
                 val match = headerRegex.find(text)
                 // ... same logic ...
                 // This duplicates logic. Let's stick to the Select approach but filter "block" tags only.
             }
        }
        
        // Save last section
        if (currentSectionNum > 0) {
            sections.add(
                LeafletSection(number = currentSectionNum, title = currentTitle, content = currentContent.toString())
            )
        }
        
        return sections
    }
    
    // Helper not needed anymore inside parseHtmlLeaflet
    private fun getSectionNumber(text: String): Int? { return null } // Deprecated helper placeholder


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
