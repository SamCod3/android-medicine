package com.samcod3.meditrack.domain.model

import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion

/**
 * Domain model for a medication reminder.
 */
data class Reminder(
    val id: String,
    val medicationId: String,
    val medicationName: String = "",
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Int,
    // Structured dosage
    val dosageQuantity: Int,
    val dosageType: DosageType,
    val dosagePortion: Portion?,
    val enabled: Boolean
) {
    val timeFormatted: String
        get() = String.format("%02d:%02d", hour, minute)
    
    val daysFormatted: String
        get() = when (daysOfWeek) {
            0 -> "Todos los días"
            127 -> "Todos los días" // All bits set
            else -> buildString {
                if ((daysOfWeek and 1) != 0) append("L ")
                if ((daysOfWeek and 2) != 0) append("M ")
                if ((daysOfWeek and 4) != 0) append("X ")
                if ((daysOfWeek and 8) != 0) append("J ")
                if ((daysOfWeek and 16) != 0) append("V ")
                if ((daysOfWeek and 32) != 0) append("S ")
                if ((daysOfWeek and 64) != 0) append("D")
            }.trim()
        }
    
    /**
     * Formatted dosage string for display.
     */
    val dosageFormatted: String
        get() = when (dosageType) {
            DosageType.PORCION -> {
                val portionText = dosagePortion?.displayName ?: Portion.ENTERA.displayName
                "$dosageQuantity ${if (dosageQuantity == 1) dosageType.singular else dosageType.displayName} ($portionText)"
            }
            else -> "$dosageQuantity ${if (dosageQuantity == 1) dosageType.singular else dosageType.displayName}"
        }
}
