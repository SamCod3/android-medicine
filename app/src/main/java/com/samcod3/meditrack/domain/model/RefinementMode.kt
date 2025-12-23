package com.samcod3.meditrack.domain.model

/**
 * Different refinement modes for AI summary generation.
 * Each mode modifies the prompt to focus on specific aspects.
 */
enum class RefinementMode(
    val displayName: String,
    val emoji: String
) {
    // General options (available for all sections)
    REGENERATE("Regenerar resumen", "🔄"),
    MORE_DETAIL("Más detallado", "📝"),
    SIMPLER("Más simple", "✂️"),
    
    // Dosage-specific (section 3: Cómo tomar/usar)
    FOCUS_DOSAGE("Enfócate en dosis exactas", "💊"),
    FOR_CHILD("Simplifica para niño", "👶"),
    FOR_ELDERLY("Simplifica para anciano", "👴"),
    
    // Side effects (section 4: Efectos adversos)
    SERIOUS_EFFECTS("Solo efectos graves", "⚠️"),
    ALL_EFFECTS("Lista todos los efectos", "📋"),
    
    // Interactions/Precautions (section 2: Antes de tomar)
    ALCOHOL("¿Puedo beber alcohol?", "🍷"),
    PREGNANCY("Embarazo/lactancia", "🤰");
    
    companion object {
        /**
         * Get available refinement options based on section title.
         */
        fun getOptionsForSection(sectionTitle: String): List<RefinementMode> {
            val generalOptions = listOf(REGENERATE, MORE_DETAIL, SIMPLER)
            
            val titleLower = sectionTitle.lowercase()
            
            val specificOptions = when {
                // Section 3: Dosage
                titleLower.contains("tomar") || 
                titleLower.contains("usar") ||
                titleLower.contains("dosis") ||
                titleLower.contains("posolog") -> {
                    listOf(FOCUS_DOSAGE, FOR_CHILD, FOR_ELDERLY)
                }
                
                // Section 4: Side effects
                titleLower.contains("efectos adversos") ||
                titleLower.contains("efectos secundarios") -> {
                    listOf(SERIOUS_EFFECTS, ALL_EFFECTS)
                }
                
                // Section 2: Before taking / Interactions
                titleLower.contains("antes de") ||
                titleLower.contains("tener en cuenta") ||
                titleLower.contains("precaucion") ||
                titleLower.contains("interaccion") -> {
                    listOf(ALCOHOL, PREGNANCY)
                }
                
                else -> emptyList()
            }
            
            return generalOptions + specificOptions
        }
    }
}
