package com.samcod3.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity for medication reminders/alarms.
 * Each reminder represents a scheduled dose time for a medication.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"])]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val hour: Int,           // 0-23
    val minute: Int,         // 0-59
    
    // Schedule type and configuration
    val scheduleType: String = "DAILY",  // ScheduleType enum name
    val daysOfWeek: Int = 0,             // Bitmask for WEEKLY: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64
    val intervalDays: Int = 1,           // For INTERVAL: every X days
    val dayOfMonth: Int = 1,             // For MONTHLY: day 1-31
    val startDate: Long = System.currentTimeMillis(), // Start date for INTERVAL calculations
    
    // Structured dosage fields
    val dosageQuantity: Int = 1,
    val dosageType: String = "COMPRIMIDO",    // DosageType enum name
    val dosagePortion: String? = null,         // Portion enum name (only for PORCION type)
    
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MONDAY = 1
        const val TUESDAY = 2
        const val WEDNESDAY = 4
        const val THURSDAY = 8
        const val FRIDAY = 16
        const val SATURDAY = 32
        const val SUNDAY = 64
        
        fun isDayEnabled(daysOfWeek: Int, dayFlag: Int): Boolean {
            return (daysOfWeek and dayFlag) != 0
        }
    }
    
    /**
     * Generates the formatted dosage string for display.
     */
    fun formatDosage(): String {
        val type = DosageType.entries.find { it.name == dosageType } ?: DosageType.COMPRIMIDO
        val portion = dosagePortion?.let { p -> Portion.entries.find { it.name == p } }
        
        return when (type) {
            DosageType.PORCION -> {
                val portionText = portion?.displayName ?: Portion.ENTERA.displayName
                "$dosageQuantity ${if (dosageQuantity == 1) type.singular else type.displayName} ($portionText)"
            }
            else -> "$dosageQuantity ${if (dosageQuantity == 1) type.singular else type.displayName}"
        }
    }
    
    /**
     * Generates the formatted schedule string for display.
     */
    fun formatSchedule(): String {
        val type = ScheduleType.entries.find { it.name == scheduleType } ?: ScheduleType.DAILY
        return when (type) {
            ScheduleType.DAILY -> "Todos los días"
            ScheduleType.WEEKLY -> formatWeeklyDays()
            ScheduleType.INTERVAL -> "Cada $intervalDays ${if (intervalDays == 1) "día" else "días"}"
            ScheduleType.MONTHLY -> "Día $dayOfMonth de cada mes"
        }
    }
    
    private fun formatWeeklyDays(): String {
        if (daysOfWeek == 0 || daysOfWeek == 127) return "Todos los días"
        return buildString {
            if ((daysOfWeek and MONDAY) != 0) append("L ")
            if ((daysOfWeek and TUESDAY) != 0) append("M ")
            if ((daysOfWeek and WEDNESDAY) != 0) append("X ")
            if ((daysOfWeek and THURSDAY) != 0) append("J ")
            if ((daysOfWeek and FRIDAY) != 0) append("V ")
            if ((daysOfWeek and SATURDAY) != 0) append("S ")
            if ((daysOfWeek and SUNDAY) != 0) append("D")
        }.trim()
    }
}

/**
 * Types of reminder schedules.
 */
enum class ScheduleType(val displayName: String) {
    DAILY("Todos los días"),
    WEEKLY("Días de la semana"),
    INTERVAL("Cada X días"),
    MONTHLY("Día del mes")
}

/**
 * Types of medication dosages.
 */
enum class DosageType(val displayName: String, val singular: String) {
    COMPRIMIDO("comprimidos", "comprimido"),
    GOTA("gotas", "gota"),
    CAPSULAS("cápsulas", "cápsula"),
    CUCHARADA("cucharadas", "cucharada"),
    CUCHARADITA("cucharaditas", "cucharadita"),
    ML("ml", "ml"),
    PORCION("porciones", "porción"),
    SOBRE("sobres", "sobre"),
    PARCHE("parches", "parche"),
    INHALACION("inhalaciones", "inhalación"),
    APLICACION("aplicaciones", "aplicación")
}

/**
 * Portion fractions for divisible medications.
 */
enum class Portion(val displayName: String) {
    ENTERA("Entera"),
    MEDIA("Media (½)"),
    CUARTO("Un cuarto (¼)"),
    TRES_CUARTOS("Tres cuartos (¾)"),
    MEDIO_CUARTO("Medio cuarto (⅛)")
}
