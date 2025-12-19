package com.samcod3.meditrack.ui.screens.treatment

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.samcod3.meditrack.domain.model.Reminder
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTreatmentScreen(
    profileName: String,
    onBackClick: () -> Unit,
    viewModel: MyTreatmentViewModel = koinViewModel()
) {
    val treatment by viewModel.treatment.collectAsState()
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Mi Tratamiento")
                        Text(
                            text = "${treatment.totalCount} medicamentos activos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (treatment.totalCount > 0) {
                        IconButton(
                            onClick = {
                                shareTreatmentAsPdf(context, profileName, treatment)
                            }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartir tratamiento"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (treatment.totalCount == 0) {
            EmptyTreatmentMessage(modifier = Modifier.padding(paddingValues))
        } else {
            // Expansion states for each group
            var dailyExpanded by remember { mutableStateOf(true) }
            var weeklyExpanded by remember { mutableStateOf(true) }
            var monthlyExpanded by remember { mutableStateOf(true) }
            var intervalExpanded by remember { mutableStateOf(true) }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // DAILY
                if (treatment.daily.isNotEmpty()) {
                    item(key = "daily-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.Today,
                            title = "Todos los días",
                            count = treatment.daily.size,
                            color = MaterialTheme.colorScheme.primary,
                            isExpanded = dailyExpanded,
                            onToggle = { dailyExpanded = !dailyExpanded }
                        )
                    }
                    
                    if (dailyExpanded) {
                        // Group by medication name (base name without dosage)
                        val groupedDaily = treatment.daily.sortedBy { extractBaseName(it.medicationName) }
                        items(groupedDaily, key = { "daily-${it.id}" }) { reminder ->
                            TreatmentReminderCard(reminder = reminder)
                        }
                    }
                }
                
                // WEEKLY
                if (treatment.weekly.isNotEmpty()) {
                    item(key = "weekly-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.CalendarToday,
                            title = "Semanalmente",
                            count = treatment.weekly.values.sumOf { it.size },
                            color = MaterialTheme.colorScheme.secondary,
                            isExpanded = weeklyExpanded,
                            onToggle = { weeklyExpanded = !weeklyExpanded }
                        )
                    }
                    
                    if (weeklyExpanded) {
                        treatment.weekly.forEach { (days, reminders) ->
                            item(key = "weekly-days-$days") {
                                Text(
                                    text = days,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                            val sortedReminders = reminders.sortedBy { extractBaseName(it.medicationName) }
                            items(sortedReminders, key = { "weekly-${it.id}" }) { reminder ->
                                TreatmentReminderCard(
                                    reminder = reminder,
                                    showSchedule = false
                                )
                            }
                        }
                    }
                }
                
                // MONTHLY
                if (treatment.monthly.isNotEmpty()) {
                    item(key = "monthly-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.CalendarMonth,
                            title = "Mensualmente",
                            count = treatment.monthly.size,
                            color = MaterialTheme.colorScheme.tertiary,
                            isExpanded = monthlyExpanded,
                            onToggle = { monthlyExpanded = !monthlyExpanded }
                        )
                    }
                    
                    if (monthlyExpanded) {
                        val groupedMonthly = treatment.monthly.sortedBy { extractBaseName(it.medicationName) }
                        items(groupedMonthly, key = { "monthly-${it.id}" }) { reminder ->
                            TreatmentReminderCard(reminder = reminder)
                        }
                    }
                }
                
                // INTERVAL
                if (treatment.interval.isNotEmpty()) {
                    item(key = "interval-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.EventRepeat,
                            title = "Por intervalo",
                            count = treatment.interval.size,
                            color = MaterialTheme.colorScheme.error,
                            isExpanded = intervalExpanded,
                            onToggle = { intervalExpanded = !intervalExpanded }
                        )
                    }
                    
                    if (intervalExpanded) {
                        val groupedInterval = treatment.interval.sortedBy { extractBaseName(it.medicationName) }
                        items(groupedInterval, key = { "interval-${it.id}" }) { reminder ->
                            TreatmentReminderCard(reminder = reminder)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts the base medication name (without dosage numbers) for grouping.
 * E.g., "Ibuprofeno 600mg" -> "Ibuprofeno"
 */
private fun extractBaseName(medicationName: String): String {
    // Remove common dosage patterns like "600mg", "1000 mg", "20 mcg", etc.
    return medicationName
        .replace(Regex("\\s*\\d+\\s*(mg|mcg|ml|g|ui|iu)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*\\d+[,.]\\d+\\s*(mg|mcg|ml|g)\\b", RegexOption.IGNORE_CASE), "")
        .trim()
        .uppercase()
}

@Composable
private fun EmptyTreatmentMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Medication,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin tratamiento configurado",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Añade medicamentos y configura recordatorios para ver tu tratamiento aquí",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CollapsibleScheduleHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.weight(1f)
            )
            
            // Count badge with better contrast
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = color
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Expand/collapse arrow
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TreatmentReminderCard(
    reminder: Reminder,
    showSchedule: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Medication,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.medicationName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = reminder.dosageFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (showSchedule) {
                    Text(
                        text = reminder.scheduleFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = reminder.timeFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Generates a PDF with the patient's treatment and shares it.
 */
private fun shareTreatmentAsPdf(
    context: Context,
    patientName: String,
    treatment: TreatmentGrouped
) {
    try {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        
        // Paints
        val titlePaint = Paint().apply {
            textSize = 24f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }
        
        val subtitlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.DKGRAY
        }
        
        val headerPaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            color = android.graphics.Color.parseColor("#6750A4") // Primary color
        }
        
        val bodyPaint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }
        
        val lightPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.GRAY
        }
        
        var yPosition = 50f
        val leftMargin = 40f
        val lineHeight = 18f
        
        // Header: Patient Name
        val displayName = patientName.trim().ifEmpty { "Paciente" }
        canvas.drawText("Tratamiento de $displayName", leftMargin, yPosition, titlePaint)
        yPosition += 30f
        
        // Date
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
        canvas.drawText("Generado el ${dateFormat.format(Date())}", leftMargin, yPosition, lightPaint)
        yPosition += 40f
        
        // Summary
        canvas.drawText("Resumen: ${treatment.totalCount} medicamentos activos", leftMargin, yPosition, subtitlePaint)
        yPosition += 35f
        
        // Daily medications
        if (treatment.daily.isNotEmpty()) {
            canvas.drawText("📅 TODOS LOS DÍAS (${treatment.daily.size})", leftMargin, yPosition, headerPaint)
            yPosition += 20f
            
            treatment.daily.sortedBy { extractBaseName(it.medicationName) }.forEach { reminder ->
                canvas.drawText("• ${reminder.medicationName}", leftMargin + 15, yPosition, bodyPaint)
                yPosition += lineHeight
                canvas.drawText("  ${reminder.dosageFormatted} - ${reminder.timeFormatted}", leftMargin + 15, yPosition, lightPaint)
                yPosition += lineHeight + 5
            }
            yPosition += 15f
        }
        
        // Weekly medications
        if (treatment.weekly.isNotEmpty()) {
            val weeklyCount = treatment.weekly.values.sumOf { it.size }
            canvas.drawText("📆 SEMANALMENTE ($weeklyCount)", leftMargin, yPosition, headerPaint)
            yPosition += 20f
            
            treatment.weekly.forEach { (days, reminders) ->
                canvas.drawText(days, leftMargin + 15, yPosition, bodyPaint)
                yPosition += lineHeight
                
                reminders.sortedBy { extractBaseName(it.medicationName) }.forEach { reminder ->
                    canvas.drawText("  • ${reminder.medicationName}", leftMargin + 20, yPosition, bodyPaint)
                    yPosition += lineHeight
                    canvas.drawText("    ${reminder.dosageFormatted} - ${reminder.timeFormatted}", leftMargin + 20, yPosition, lightPaint)
                    yPosition += lineHeight + 3
                }
            }
            yPosition += 15f
        }
        
        // Monthly medications
        if (treatment.monthly.isNotEmpty()) {
            canvas.drawText("📅 MENSUALMENTE (${treatment.monthly.size})", leftMargin, yPosition, headerPaint)
            yPosition += 20f
            
            treatment.monthly.sortedBy { extractBaseName(it.medicationName) }.forEach { reminder ->
                canvas.drawText("• ${reminder.medicationName}", leftMargin + 15, yPosition, bodyPaint)
                yPosition += lineHeight
                canvas.drawText("  ${reminder.dosageFormatted} - ${reminder.scheduleFormatted} - ${reminder.timeFormatted}", leftMargin + 15, yPosition, lightPaint)
                yPosition += lineHeight + 5
            }
            yPosition += 15f
        }
        
        // Interval medications
        if (treatment.interval.isNotEmpty()) {
            canvas.drawText("🔄 POR INTERVALO (${treatment.interval.size})", leftMargin, yPosition, headerPaint)
            yPosition += 20f
            
            treatment.interval.sortedBy { extractBaseName(it.medicationName) }.forEach { reminder ->
                canvas.drawText("• ${reminder.medicationName}", leftMargin + 15, yPosition, bodyPaint)
                yPosition += lineHeight
                canvas.drawText("  ${reminder.dosageFormatted} - ${reminder.scheduleFormatted}", leftMargin + 15, yPosition, lightPaint)
                yPosition += lineHeight + 5
            }
        }
        
        // Footer
        yPosition = pageHeight - 40f
        canvas.drawText("Generado por MediTrack", leftMargin, yPosition, lightPaint)
        
        pdfDocument.finishPage(page)
        
        // Save to cache directory
        val fileName = "Tratamiento_${patientName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        
        // Share the PDF
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Tratamiento de $patientName")
            putExtra(Intent.EXTRA_TEXT, "Adjunto el tratamiento médico de $patientName generado desde MediTrack.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Compartir tratamiento"))
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
