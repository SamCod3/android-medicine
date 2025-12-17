package com.samcod3.meditrack.domain.model

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
    val dosage: String?,
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
}
