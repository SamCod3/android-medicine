package com.samcod3.meditrack.domain.model

/**
 * Domain model for medication information
 */
data class Medication(
    val registrationNumber: String,
    val name: String,
    val laboratory: String,
    val prescriptionRequired: Boolean,
    val affectsDriving: Boolean,
    val hasWarningTriangle: Boolean,
    val activeIngredients: List<ActiveIngredient>,
    val leafletUrl: String?,
    val photoUrl: String?,
    val nationalCode: String?
)

data class ActiveIngredient(
    val name: String,
    val quantity: String,
    val unit: String
)

/**
 * Domain model for leaflet section
 */
data class LeafletSection(
    val number: Int,
    val title: String,
    val content: String
) {
    companion object {
        val SECTION_TITLES = listOf(
            "Qué es y para qué se utiliza",
            "Antes de tomar",
            "Cómo tomar",
            "Posibles efectos adversos",
            "Conservación",
            "Información adicional"
        )
    }
}

/**
 * Complete leaflet with all sections
 */
data class Leaflet(
    val medication: Medication,
    val sections: List<LeafletSection>
)
