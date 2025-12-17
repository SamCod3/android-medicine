package com.samcod3.meditrack.domain.model

import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion
import com.samcod3.meditrack.data.local.entity.ScheduleType

/**
 * Domain model for a medication reminder.
 */
data class Reminder(
    val id: String,
    val medicationId: String,
    val medicationName: String = "",
    val hour: Int,
    val minute: Int,
    // Schedule configuration
    val scheduleType: ScheduleType,
    val daysOfWeek: Int,         // For WEEKLY
    val intervalDays: Int,       // For INTERVAL
    val dayOfMonth: Int,         // For MONTHLY
    val startDate: Long,         // For INTERVAL calculations
    // Structured dosage
    val dosageQuantity: Int,
    val dosageType: DosageType,
    val dosagePortion: Portion?,
    val enabled: Boolean
) {
    val timeFormatted: String
        get() = String.format("%02d:%02d", hour, minute)
    
    /**
     * Formatted schedule string for display.
     */
    val scheduleFormatted: String
        get() = when (scheduleType) {
            ScheduleType.DAILY -> "Todos los días"
            ScheduleType.WEEKLY -> formatWeeklyDays()
            ScheduleType.INTERVAL -> "Cada $intervalDays ${if (intervalDays == 1) "día" else "días"}"
            ScheduleType.MONTHLY -> "Día $dayOfMonth de cada mes"
        }
    
    private fun formatWeeklyDays(): String {
        if (daysOfWeek == 0 || daysOfWeek == 127) return "Todos los días"
        return buildString {
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
