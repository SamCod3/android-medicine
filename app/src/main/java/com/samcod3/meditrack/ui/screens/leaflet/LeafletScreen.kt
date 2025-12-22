package com.samcod3.meditrack.ui.screens.leaflet

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samcod3.meditrack.R
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.model.ParsedLeaflet
import com.samcod3.meditrack.domain.model.ParsedSection
import com.samcod3.meditrack.domain.model.ContentBlock
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.samcod3.meditrack.domain.model.LeafletSummary
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeafletScreen(
    nationalCode: String,
    profileId: String,
    onNavigateBack: () -> Unit,
    onMedicationSaved: () -> Unit,
    onAddReminderClick: (String, String) -> Unit,
    viewModel: LeafletViewModel = koinViewModel { parametersOf(nationalCode, profileId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    
    // Refresh reminders when screen resumes (coming back from ReminderScreen)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshReminders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Handle events (Toasts)
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is LeafletEvent.ShowToast -> {
                     android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Handle save success
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onMedicationSaved()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Prospecto")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    // Show dosage/calendar icon if medication is saved
                    if (uiState.savedMedicationId != null) {
                        IconButton(onClick = { 
                            onAddReminderClick(
                                uiState.savedMedicationId!!,
                                uiState.medication?.name ?: "Medicamento"
                            )
                        }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Ver mi pauta")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            // Only show FAB if medication is loaded AND not already saved
            if (!uiState.isLoading && uiState.medication != null && uiState.savedMedicationId == null) {
                ExtendedFloatingActionButton(
                    text = { Text("Guardar en Botiquín") },
                    icon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                    onClick = { viewModel.saveMedication() },
                    expanded = !uiState.isSaving,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading || uiState.isSaving -> {
                LoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    message = if (uiState.isSaving) "Guardando..." else stringResource(R.string.leaflet_loading)
                )
            }
            uiState.error != null -> {
                ErrorState(
                    message = uiState.error!!,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            else -> {
                LeafletContent(
                    medication = uiState.medication,
                    parsedLeaflet = uiState.parsedLeaflet,
                    myDosages = uiState.myDosages,
                    savedMedicationId = uiState.savedMedicationId,
                    onManageReminders = {
                        if (uiState.savedMedicationId != null) {
                            onAddReminderClick(
                                uiState.savedMedicationId!!,
                                uiState.medication?.name ?: "Medicamento"
                            )
                        }
                    },
                    onRetry = { viewModel.retry() },
                    isAiProcessing = uiState.isAiProcessing,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeafletContent(
    medication: Medication?,
    parsedLeaflet: ParsedLeaflet?,
    myDosages: List<Reminder>,
    savedMedicationId: String?,
    onManageReminders: () -> Unit,
    onRetry: () -> Unit,
    isAiProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val sections = parsedLeaflet?.sections ?: emptyList()
    // State for reading mode
    var isReadingMode by remember { mutableStateOf(false) }
    var selectedSectionIndex by remember { mutableStateOf(0) }
    val readingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val readingListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Normal view: Info cards + Section selector
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Processing Indicator
        AnimatedVisibility(
            visible = isAiProcessing,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp), 
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Mejorando formato con IA...", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // AI Summary Card (Quick Job result)
        // Only show if we have a summary object
        if (parsedLeaflet?.summary != null) {
            StructuredSummaryCard(
                summary = parsedLeaflet.summary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // My dosages section (if saved)
        if (savedMedicationId != null) {
            MyDosageSection(
                dosages = myDosages,
                onAddReminder = onManageReminders,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Medication info card
        medication?.let {
            MedicationInfoCard(
                medication = it,
                modifier = Modifier.fillMaxWidth()
            )
            
            // If medication is imported (dummy registration number), show option to fetch official data
            if (it.registrationNumber == "00000") {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Datos oficiales no disponibles",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Este medicamento fue importado manualmente. Pulsa aquí para intentar descargar el prospecto y generar el resumen con IA.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Descargar Datos Oficiales")
                        }
                    }
                }
            }
        }
        
        // Single card to open reading mode
        if (sections.isNotEmpty()) {
            ReadLeafletCard(
                sectionCount = sections.size,
                onClick = {
                    selectedSectionIndex = 0
                    isReadingMode = true
                    coroutineScope.launch {
                        readingListState.scrollToItem(0)
                    }
                }
            )
        } else {
            // Fallback for when segmented content is not available
            NoSectionsAvailable(medication)
        }
        
        // Bottom spacing for FAB
        Spacer(modifier = Modifier.height(80.dp))
    }
    
    // Reading mode BottomSheet
    if (isReadingMode && sections.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { isReadingMode = false },
            sheetState = readingSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null // Custom header instead
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Custom header with close button and medication name
                val context = LocalContext.current
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isReadingMode = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Text(
                            text = medication?.name ?: "Prospecto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Share PDF button
                        IconButton(
                            onClick = {
                                medication?.let {
                                    shareLeafletAsPdf(context, it, sections)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir como PDF",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Section navigation
                ReadingSectionSelector(
                    sections = sections,
                    currentIndex = selectedSectionIndex,
                    onSectionSelected = { index ->
                        selectedSectionIndex = index
                        coroutineScope.launch {
                            readingListState.animateScrollToItem(index)
                        }
                    }
                )
                
                HorizontalDivider()
                
                // Update selectedSectionIndex when scrolling
                LaunchedEffect(readingListState) {
                    snapshotFlow { readingListState.firstVisibleItemIndex }
                        .collect { index ->
                            if (index in sections.indices) {
                                selectedSectionIndex = index
                            }
                        }
                }
                
                // Scrollable content
                LazyColumn(
                    state = readingListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(sections) { index, section ->
                        ParsedSectionCard(
                            sectionNumber = index + 1,
                            section = section,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

// Helper to clean section titles
private fun cleanSectionTitle(title: String): String {
    return title.replace(Regex("^\\d+\\.?\\d*\\.?\\s*"), "").trim()
}

// Removed cleanHtmlContent helper


// Removed parseHtmlToAnnotatedString helper


/**
 * Generates a PDF with the leaflet content and shares it.
 */
private fun shareLeafletAsPdf(
    context: Context,
    medication: Medication,
    sections: List<ParsedSection>
) {
    try {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        
        // Paints
        val titlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(50, 50, 150)
        }
        val bodyPaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.BLACK
        }
        val lightPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.GRAY
        }
        
        val leftMargin = 40f
        val rightMargin = 40f
        val maxWidth = pageWidth - leftMargin - rightMargin
        val lineHeight = 16f
        val sectionSpacing = 24f
        
        var currentPage = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var yPosition = 60f
        
        // Title
        canvas.drawText(medication.name, leftMargin, yPosition, titlePaint)
        yPosition += 24f
        
        // Subtitle with active ingredients
        if (medication.activeIngredients.isNotEmpty()) {
            val ingredients = medication.activeIngredients.take(2).joinToString(", ") { 
                "${it.name} ${it.quantity}${it.unit}" 
            }
            canvas.drawText(ingredients, leftMargin, yPosition, lightPaint)
            yPosition += 20f
        }
        
        yPosition += 16f
        
        // Sections
        for ((index, section) in sections.withIndex()) {
            val sectionTitle = "${index + 1}. ${cleanSectionTitle(section.title)}"
            // Convert content blocks to plain text for PDF
            val sectionContent = section.content.joinToString("\n\n") { block ->
                when(block) {
                    is ContentBlock.Paragraph -> block.text
                    is ContentBlock.Bold -> block.text
                    is ContentBlock.Italic -> block.text
                    is ContentBlock.BulletItem -> "• ${block.text}"
                    is ContentBlock.NumberedItem -> "${block.number}. ${block.text}"
                    is ContentBlock.SubHeading -> "\n${block.text.uppercase()}"
                }
            }
            
            // Check if we need a new page (rough estimate)
            val estimatedHeight = sectionSpacing + 20 + (sectionContent.length / 80 * lineHeight)
            if (yPosition + estimatedHeight > pageHeight - 60) {
                pdfDocument.finishPage(page)
                currentPage++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 60f
            }
            
            // Section header
            canvas.drawText(sectionTitle, leftMargin, yPosition, headerPaint)
            yPosition += 20f
            
            // Section content - wrap text
            val words = sectionContent.split(Regex("\\s+"))
            var currentLine = StringBuilder()
            
            for (word in words) {
                if (word == "\n" || word.contains("\n")) {
                    // Handle newlines
                    val parts = word.split("\n")
                    for ((i, part) in parts.withIndex()) {
                        if (part.isNotEmpty()) {
                            val testLine = if (currentLine.isEmpty()) part else "$currentLine $part"
                            if (bodyPaint.measureText(testLine) <= maxWidth) {
                                if (currentLine.isNotEmpty()) currentLine.append(" ")
                                currentLine.append(part)
                            } else {
                                if (currentLine.isNotEmpty()) {
                                    canvas.drawText(currentLine.toString(), leftMargin, yPosition, bodyPaint)
                                    yPosition += lineHeight
                                }
                                currentLine = StringBuilder(part)
                            }
                        }
                        if (i < parts.lastIndex) {
                            if (currentLine.isNotEmpty()) {
                                canvas.drawText(currentLine.toString(), leftMargin, yPosition, bodyPaint)
                                yPosition += lineHeight
                                currentLine = StringBuilder()
                            }
                        }
                    }
                } else {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (bodyPaint.measureText(testLine) <= maxWidth) {
                        if (currentLine.isNotEmpty()) currentLine.append(" ")
                        currentLine.append(word)
                    } else {
                        canvas.drawText(currentLine.toString(), leftMargin, yPosition, bodyPaint)
                        yPosition += lineHeight
                        currentLine = StringBuilder(word)
                        
                        // Check if we need a new page
                        if (yPosition > pageHeight - 60) {
                            pdfDocument.finishPage(page)
                            currentPage++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            yPosition = 60f
                        }
                    }
                }
            }
            
            // Draw remaining line
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine.toString(), leftMargin, yPosition, bodyPaint)
                yPosition += lineHeight
            }
            
            yPosition += sectionSpacing
        }
        
        pdfDocument.finishPage(page)
        
        // Save to cache
        val fileName = "Prospecto_${medication.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.pdf"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { output ->
            pdfDocument.writeTo(output)
        }
        pdfDocument.close()
        
        // Share
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Prospecto: ${medication.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Compartir prospecto"))
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun ReadLeafletCard(
    sectionCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Leer Prospecto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$sectionCount secciones",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Abrir",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ReadingSectionSelector(
    sections: List<ParsedSection>,
    currentIndex: Int,
    onSectionSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSection = sections.getOrNull(currentIndex)
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Current section header (clickable to expand)
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${currentIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = cleanSectionTitle(currentSection?.title ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Cambiar sección"
                )
            }
        }
        
        // Expanded section list
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                sections.forEachIndexed { index, section ->
                    val isSelected = index == currentIndex
                    Surface(
                        onClick = {
                            expanded = false
                            onSectionSelected(index)
                        },
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                        else 
                            MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp)
                            )
                            
                            Text(
                                text = cleanSectionTitle(section.title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    if (index < sections.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoSectionsAvailable(medication: Medication?) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No se pudo cargar el formato de lectura rápida.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "El prospecto no está disponible por secciones.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (!medication?.leafletUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { 
                    try {
                        uriHandler.openUri(medication!!.leafletUrl!!) 
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            ) {
                Text("Ver Prospecto Oficial (Web)")
            }
        }
    }
}


@Composable
private fun MedicationInfoCard(
    medication: Medication,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (medication.laboratory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = medication.laboratory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (medication.activeIngredients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = medication.activeIngredients.joinToString(", ") { 
                        "${it.name} ${it.quantity}${it.unit}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Warning indicators
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (medication.affectsDriving) {
                    WarningBadge(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        text = "Conducción"
                    )
                }
                
                if (medication.hasWarningTriangle) {
                    WarningBadge(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        text = "Especial",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningBadge(
    icon: @Composable () -> Unit,
    text: String,
    color: Color = MaterialTheme.colorScheme.tertiary
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color)
                    .padding(4.dp)
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}



@Composable
private fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.leaflet_loading)
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onRetry) {
                Text(stringResource(R.string.leaflet_retry))
            }
        }
    }
}

@Composable
private fun MyDosageSection(
    dosages: List<Reminder>,
    onAddReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row (always visible, clickable to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mi Pauta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (dosages.isNotEmpty() && !expanded) {
                        Text(
                            text = "${dosages.size} recordatorio${if (dosages.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (dosages.isEmpty()) {
                        Text(
                            text = "No tienes recordatorios activos para este medicamento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onAddReminder,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Text("Añadir Recordatorio")
                        }
                    } else {
                        dosages.forEach { reminder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "${reminder.dosageFormatted} a las ${reminder.timeFormatted}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Text(
                                text = reminder.scheduleFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Edit button
                        OutlinedCard(
                            onClick = onAddReminder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Gestionar recordatorios",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StructuredSummaryCard(
    summary: LeafletSummary,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    
    Card(
        modifier = modifier.padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Resumen Rápido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Contraer" else "Expandir",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    SummaryItem(
                        icon = Icons.Default.Info, 
                        title = "Para qué es", 
                        text = summary.indications,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    SummaryItem(
                        icon = Icons.Default.MedicalServices, 
                        title = "Cómo tomarlo", 
                        text = summary.dosage,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    SummaryItem(
                        icon = Icons.Default.Warning, 
                        title = "Precaución", 
                        text = summary.warnings,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
            )
        }
    }
}
