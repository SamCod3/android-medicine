package com.samcod3.meditrack.ui.screens.treatment

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.samcod3.meditrack.domain.model.ImportStatus
import com.samcod3.meditrack.domain.model.Reminder
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTreatmentScreen(
    profileId: String,
    profileName: String,
    onBackClick: () -> Unit,
    onMedicationClick: (nationalCode: String) -> Unit,
    viewModel: MyTreatmentViewModel = koinViewModel { org.koin.core.parameter.parametersOf(profileId) }
) {
    val treatment by viewModel.treatment.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // JSON Picker for backup import
    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { jsonUri ->
            scope.launch {
                try {
                    val jsonText = context.contentResolver.openInputStream(jsonUri)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (jsonText.isNotBlank()) {
                        viewModel.importBackup(jsonText) { result ->
                            if (result != null) {
                                val message = when {
                                    result.medicationsImported > 0 && result.medicationsSkipped > 0 ->
                                        "Importados ${result.medicationsImported} medicamentos (${result.medicationsSkipped} ya existían)"
                                    result.medicationsImported > 0 ->
                                        "Importados ${result.medicationsImported} medicamentos"
                                    result.medicationsSkipped > 0 ->
                                        "Todos los ${result.medicationsSkipped} medicamentos ya existían"
                                    else ->
                                        "Backup vacío"
                                }
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                val error = viewModel.backupError.value ?: "Error desconocido"
                                android.widget.Toast.makeText(
                                    context, 
                                    "Falló la importación: $error", 
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context, 
                        "Error al leer el archivo: ${e.message}", 
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
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
                    var showImportMenu by remember { mutableStateOf(false) }
                    
                    // Import button with dropdown menu
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Importar tratamiento"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Importar desde JSON") },
                                onClick = {
                                    showImportMenu = false
                                    jsonPickerLauncher.launch("application/json")
                                },
                                leadingIcon = { Icon(Icons.Default.DataObject, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Exportar backup JSON") },
                                onClick = {
                                    showImportMenu = false
                                    viewModel.exportBackup { json ->
                                        shareBackupJson(context, profileName, json)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, null) }
                            )
                        }
                    }
                    
                    // Share button (only if has content)
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
            // Expansion states for each group (collapsed by default, saved across navigation)
            var dailyExpanded by rememberSaveable { mutableStateOf(false) }
            var weeklyExpanded by rememberSaveable { mutableStateOf(false) }
            var monthlyExpanded by rememberSaveable { mutableStateOf(false) }
            var intervalExpanded by rememberSaveable { mutableStateOf(false) }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // DAILY
                if (treatment.daily.isNotEmpty()) {
                    // Group reminders by medication
                    val dailyGrouped = treatment.daily
                        .groupBy { it.medicationId }
                        .map { (_, reminders) -> 
                            reminders.first().medicationName to reminders 
                        }
                        .sortedBy { it.first.lowercase() }
                    
                    item(key = "daily-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.Today,
                            title = "Todos los días",
                            count = dailyGrouped.size,
                            color = MaterialTheme.colorScheme.primary,
                            isExpanded = dailyExpanded,
                            onToggle = { dailyExpanded = !dailyExpanded }
                        )
                    }
                    
                    if (dailyExpanded) {
                        items(dailyGrouped, key = { "daily-${it.second.first().medicationId}" }) { (name, reminders) ->
                            GroupedMedicationCard(
                                medicationName = name,
                                nationalCode = reminders.first().nationalCode,
                                reminders = reminders,
                                onClick = { onMedicationClick(reminders.first().nationalCode) }
                            )
                        }
                    }
                }
                
                // WEEKLY
                if (treatment.weekly.isNotEmpty()) {
                    // Count unique medications across all weekly reminders
                    val allWeeklyMeds = treatment.weekly.values.flatten()
                        .groupBy { it.medicationId }
                        .size
                    
                    item(key = "weekly-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.CalendarToday,
                            title = "Semanalmente",
                            count = allWeeklyMeds,
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
                            
                            // Group by medication
                            val weeklyGrouped = reminders
                                .groupBy { it.medicationId }
                                .map { (_, meds) -> meds.first().medicationName to meds }
                                .sortedBy { it.first.lowercase() }
                            
                            items(weeklyGrouped, key = { "weekly-$days-${it.second.first().medicationId}" }) { (name, meds) ->
                                GroupedMedicationCard(
                                    medicationName = name,
                                    nationalCode = meds.first().nationalCode,
                                    reminders = meds,
                                    showSchedule = false,
                                    onClick = { onMedicationClick(meds.first().nationalCode) }
                                )
                            }
                        }
                    }
                }
                
                // MONTHLY
                if (treatment.monthly.isNotEmpty()) {
                    // Group reminders by medication
                    val monthlyGrouped = treatment.monthly
                        .groupBy { it.medicationId }
                        .map { (_, reminders) -> reminders.first().medicationName to reminders }
                        .sortedBy { it.first.lowercase() }
                    
                    item(key = "monthly-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.CalendarMonth,
                            title = "Mensualmente",
                            count = monthlyGrouped.size,
                            color = MaterialTheme.colorScheme.tertiary,
                            isExpanded = monthlyExpanded,
                            onToggle = { monthlyExpanded = !monthlyExpanded }
                        )
                    }
                    
                    if (monthlyExpanded) {
                        items(monthlyGrouped, key = { "monthly-${it.second.first().medicationId}" }) { (name, meds) ->
                            GroupedMedicationCard(
                                medicationName = name,
                                nationalCode = meds.first().nationalCode,
                                reminders = meds,
                                onClick = { onMedicationClick(meds.first().nationalCode) }
                            )
                        }
                    }
                }
                
                if (treatment.interval.isNotEmpty()) {
                    // Group reminders by medication
                    val intervalGrouped = treatment.interval
                        .groupBy { it.medicationId }
                        .map { (_, reminders) -> reminders.first().medicationName to reminders }
                        .sortedBy { it.first.lowercase() }
                    
                    item(key = "interval-header") {
                        CollapsibleScheduleHeader(
                            icon = Icons.Default.EventRepeat,
                            title = "Por intervalo",
                            count = intervalGrouped.size,
                            color = MaterialTheme.colorScheme.error,
                            isExpanded = intervalExpanded,
                            onToggle = { intervalExpanded = !intervalExpanded }
                        )
                    }
                    
                    if (intervalExpanded) {
                        items(intervalGrouped, key = { "interval-${it.second.first().medicationId}" }) { (name, meds) ->
                            GroupedMedicationCard(
                                medicationName = name,
                                nationalCode = meds.first().nationalCode,
                                reminders = meds,
                                onClick = { onMedicationClick(meds.first().nationalCode) }
                            )
                        }
                    }
                }

                // UNSCHEDULED (Added)
                if (treatment.unscheduled.isNotEmpty()) {
                    item(key = "unscheduled-header") {
                        // Using a distinct style for unscheduled
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Sin pauta definida (${treatment.unscheduled.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    items(treatment.unscheduled, key = { "unscheduled-${it.id}" }) { medication ->
                        Card(
                            onClick = { onMedicationClick(medication.nationalCode) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent) // Transparent to blend or use default
                        ) {
                           Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = medication.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Pulsa para configurar recordatorio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.rotate(180f), tint = MaterialTheme.colorScheme.outline) // Forward arrow
                            }
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
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
            
            // Count badge with high contrast
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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
private fun GroupedMedicationCard(
    medicationName: String,
    nationalCode: String,
    reminders: List<Reminder>,
    showSchedule: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
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
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Medication,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicationName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Group reminders with same dosage together
                val dosageGroups = reminders.groupBy { it.dosageFormatted }
                
                dosageGroups.forEach { (dosage, dosageReminders) ->
                    val times = dosageReminders
                        .sortedBy { it.hour * 60 + it.minute }
                        .map { it.timeFormatted }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = dosage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        
                        if (dosageReminders.size > 1) {
                            Text(
                                text = " × ${dosageReminders.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "(${times.joinToString(", ")})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (showSchedule && reminders.isNotEmpty()) {
                    Text(
                        text = reminders.first().scheduleFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
            
            treatment.daily.sortedBy { it.medicationName.lowercase() }.forEach { reminder ->
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
                
                reminders.sortedBy { it.medicationName.lowercase() }.forEach { reminder ->
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
            
            treatment.monthly.sortedBy { it.medicationName.lowercase() }.forEach { reminder ->
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
            
            treatment.interval.sortedBy { it.medicationName.lowercase() }.forEach { reminder ->
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

/**
 * Shares a JSON backup via Intent.
 */
private fun shareBackupJson(context: Context, profileName: String, jsonContent: String) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val filename = "meditrack_backup_${profileName}_${sdf.format(Date())}.json"
    
    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { fos ->
        fos.write(jsonContent.toByteArray())
    }
    
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(Intent.createChooser(shareIntent, "Compartir backup de $profileName"))
}
