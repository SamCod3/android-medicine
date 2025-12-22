package com.samcod3.meditrack.ui.screens.search

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.samcod3.meditrack.ai.AIService
import com.samcod3.meditrack.ai.AIStatusChecker
import com.samcod3.meditrack.domain.model.Medication
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.concurrent.Executors

/**
 * Data class to represent OCR token with active state
 * @param text The token text
 * @param isActive Whether this token is currently used in filtering (true = filter applied)
 * @param isFilterToken Whether this token should appear as a filter chip (modifier, dosage, presentation)
 */
data class OcrToken(
    val text: String, 
    var isActive: Boolean = true,
    val isFilterToken: Boolean = false // If true, shows as chip and persists
)

/**
 * Tokenize OCR result intelligently, keeping dosage together (e.g., "600 mg")
 * NOTE: Dosage tokens are set to INACTIVE by default to improve search flexibility
 */
private fun tokenizeOcrResult(text: String): List<OcrToken> {
    if (text.isBlank()) return emptyList()
    
    // Regex to match dosage patterns like "600 mg", "10 mg/ml", "0.5 g"
    val dosagePattern = Regex("""(\d+[\.,]?\d*\s*(mg|g|ml|mcg|UI|mg/ml|µg)(/ml)?)""", RegexOption.IGNORE_CASE)
    
    val tokens = mutableListOf<OcrToken>()
    var remaining = text.trim()
    
    // First extract dosage as a single token
    val dosageMatch = dosagePattern.find(remaining)
    if (dosageMatch != null) {
        val beforeDosage = remaining.substring(0, dosageMatch.range.first).trim()
        val dosage = dosageMatch.value.trim()
        val afterDosage = remaining.substring(dosageMatch.range.last + 1).trim()
        
        // Add words before dosage (active by default)
        beforeDosage.split(Regex("""\s+""")).filter { it.isNotBlank() }.forEach {
            tokens.add(OcrToken(it, isActive = true))
        }
        
        // Add dosage as single token - INACTIVE by default for better search
        tokens.add(OcrToken(dosage, isActive = false))
        
        // Add words after dosage (inactive - usually pharmaceutical form)
        afterDosage.split(Regex("""\s+""")).filter { it.isNotBlank() }.forEach {
            tokens.add(OcrToken(it, isActive = false))
        }
    } else {
        // No dosage found, just split by spaces
        remaining.split(Regex("""\s+""")).filter { it.isNotBlank() }.forEach {
            tokens.add(OcrToken(it, isActive = true))
        }
    }
    
    return tokens
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onMedicationClick: (Medication) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val aiService: AIService = koinInject()
    
    var showOcrDialog by remember { mutableStateOf(false) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    var usedAI by remember { mutableStateOf(false) }
    var aiStatus by remember { mutableStateOf(AIStatusChecker.AIStatus.CHECKING) }
    
    // OCR Tokens for interactive tag chips
    val ocrTokens = remember { mutableStateListOf<OcrToken>() }
    
    // Check AI availability on first composition
    LaunchedEffect(Unit) {
        aiStatus = AIStatusChecker.checkStatus()
    }
    
    // Clear state when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearState()
            ocrTokens.clear()
        }
    }
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Buscar")
                        // Compact AI status indicator
                        val aiStatusColor = when (aiStatus) {
                            AIStatusChecker.AIStatus.AVAILABLE -> Color(0xFF4CAF50)
                            AIStatusChecker.AIStatus.DOWNLOADABLE -> Color(0xFF2196F3)
                            AIStatusChecker.AIStatus.DOWNLOADING -> Color(0xFFFF9800)
                            AIStatusChecker.AIStatus.UNAVAILABLE -> Color(0xFFF44336)
                            AIStatusChecker.AIStatus.CHECKING -> Color.Gray
                        }
                        val aiText = when (aiStatus) {
                            AIStatusChecker.AIStatus.AVAILABLE -> "IA"
                            AIStatusChecker.AIStatus.DOWNLOADABLE -> "Descargar IA"
                            AIStatusChecker.AIStatus.DOWNLOADING -> "Descargando..."
                            AIStatusChecker.AIStatus.UNAVAILABLE -> "Sin IA"
                            AIStatusChecker.AIStatus.CHECKING -> "..."
                        }
                        val isClickable = aiStatus == AIStatusChecker.AIStatus.DOWNLOADABLE
                        
                        Row(
                            modifier = Modifier
                                .then(
                                    if (isClickable) {
                                        Modifier.clickable {
                                            coroutineScope.launch {
                                                AIStatusChecker.startDownload().collect { status ->
                                                    aiStatus = status
                                                }
                                            }
                                        }
                                    } else Modifier
                                )
                                .background(aiStatusColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(aiStatusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = aiText,
                                style = MaterialTheme.typography.labelSmall,
                                color = aiStatusColor
                            )
                            if (aiStatus == AIStatusChecker.AIStatus.DOWNLOADING) {
                                Spacer(modifier = Modifier.width(4.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = aiStatusColor
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search Input with OCR button and AI badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { 
                        viewModel.onQueryChange(it)
                        if (it != uiState.query) usedAI = false // Reset AI badge on manual edit
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nombre (ej. Paracetamol)") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            viewModel.search()
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.search()
                        focusManager.clearFocus()
                    })
                )
                
                // OCR Camera Button
                IconButton(
                    onClick = {
                        if (cameraPermissionState.status.isGranted) {
                            showOcrDialog = true
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Escanear nombre",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Warning when query is too generic
            if (uiState.queryTooGeneric) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Búsqueda muy amplia",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Añade el laboratorio o escanea la caja para mejores resultados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            
            // Expandable Filter Section - chips load in background
            val availableFilters = uiState.availableFilters
            val hasFilters = availableFilters.dosages.isNotEmpty() || availableFilters.presentations.isNotEmpty()
            val filterCount = availableFilters.dosages.size + availableFilters.presentations.size
            var filtersExpanded by remember { mutableStateOf(false) }
            
            if (uiState.allResults.size > 1 && !uiState.queryTooGeneric) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    // Expand/Collapse header button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filtersExpanded = !filtersExpanded }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasFilters) "Filtrar ($filterCount)" else "Cargando filtros...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (filtersExpanded) "Colapsar" else "Expandir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Expandable chips content
                    AnimatedVisibility(visible = filtersExpanded && hasFilters) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        ) {
                            // Dosage chips
                            if (availableFilters.dosages.isNotEmpty()) {
                                Text(
                                    text = "Dosis:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    availableFilters.dosages.forEach { dosage ->
                                        val isActive = uiState.activeFilters.contains(dosage)
                                        FilterChip(
                                            selected = isActive,
                                            onClick = {
                                                val newFilters = if (isActive) {
                                                    uiState.activeFilters - dosage
                                                } else {
                                                    uiState.activeFilters + dosage
                                                }
                                                viewModel.setActiveFilters(newFilters)
                                            },
                                            label = { 
                                                Text(
                                                    text = dosage,
                                                    style = MaterialTheme.typography.bodySmall
                                                ) 
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Presentation chips
                            if (availableFilters.presentations.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Presentación:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    availableFilters.presentations.forEach { presentation ->
                                        val isActive = uiState.activeFilters.contains(presentation)
                                        FilterChip(
                                            selected = isActive,
                                            onClick = {
                                                val newFilters = if (isActive) {
                                                    uiState.activeFilters - presentation
                                                } else {
                                                    uiState.activeFilters + presentation
                                                }
                                                viewModel.setActiveFilters(newFilters)
                                            },
                                            label = { 
                                                Text(
                                                    text = presentation,
                                                    style = MaterialTheme.typography.bodySmall
                                                ) 
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Results or Loading
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error: ${uiState.error}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.results) { medication ->
                            MedicationSearchItem(
                                medication = medication,
                                onClick = { onMedicationClick(medication) }
                            )
                        }
                        
                        if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
                            item {
                                Text(
                                    text = "No se encontraron resultados",
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // OCR Camera Dialog
    if (showOcrDialog) {
        OcrCaptureDialog(
            isProcessing = isProcessingOcr,
            onDismiss = { showOcrDialog = false },
            onTextCaptured = { recognizedText ->
                if (recognizedText.isNullOrBlank()) {
                    showOcrDialog = false
                    isProcessingOcr = false
                    return@OcrCaptureDialog
                }
                
                // Use AI service to extract medication name
                coroutineScope.launch {
                    val result = aiService.extractMedicationName(recognizedText)
                    
                    // Use AI result if available, otherwise use local fallback
                    if (result.usedAI && result.name.isNotBlank()) {
                        usedAI = true
                        
                        // Clear old tokens and add new ones from structured response
                        ocrTokens.clear()
                        
                        // Add name tokens (NOT filter tokens - used for search)
                        result.name.split(Regex("""\s+""")).filter { it.isNotBlank() }.forEach {
                            ocrTokens.add(OcrToken(text = it, isActive = true, isFilterToken = false))
                        }
                        
                        // Add modifier token (filter token, initially inactive)
                        result.modifier?.let { modifier ->
                            ocrTokens.add(OcrToken(text = modifier, isActive = false, isFilterToken = true))
                        }
                        
                        // Add dosage token (filter token, AUTO-APPLIED = isActive true)
                        result.dosage?.let { dosage ->
                            ocrTokens.add(OcrToken(text = dosage, isActive = true, isFilterToken = true))
                        }
                        
                        // Add presentation token (filter token, initially inactive)
                        result.presentation?.let { presentation ->
                            ocrTokens.add(OcrToken(text = presentation, isActive = false, isFilterToken = true))
                        }
                        
                        // Search using just the name (CIMA search)
                        viewModel.onQueryChange(result.name)
                        
                        // Set initial filter = just dosage (auto-applied)
                        val initialFilters = result.dosage?.let { listOf(it) } ?: emptyList()
                        viewModel.setActiveFilters(initialFilters)
                        
                        viewModel.search()
                        
                    } else {
                        // Fallback to old tokenization
                        usedAI = false
                        val fallbackText = cleanOcrText(recognizedText)
                        
                        if (fallbackText.isNotBlank()) {
                            ocrTokens.clear()
                            ocrTokens.addAll(tokenizeOcrResult(fallbackText))
                            
                            val activeQuery = ocrTokens
                                .filter { it.isActive }
                                .joinToString(" ") { it.text }
                            
                            viewModel.onQueryChange(activeQuery)
                            viewModel.setActiveFilters(emptyList())
                            if (activeQuery.isNotBlank()) {
                                viewModel.search()
                            }
                        }
                    }
                    
                    showOcrDialog = false
                    isProcessingOcr = false
                }
            },
            onProcessingStarted = { isProcessingOcr = true }
        )
    }
}

@Composable
private fun OcrCaptureDialog(
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onTextCaptured: (String) -> Unit,
    onProcessingStarted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    
    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(500.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Capturar nombre del medicamento",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss, enabled = !isProcessing) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
                
                // Camera Preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                imageCapture = capture
                                
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture
                                    )
                                } catch (e: Exception) {
                                    Log.e("SearchOCR", "Camera binding failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Processing overlay
                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Reconociendo texto...", color = Color.White)
                            }
                        }
                    }
                }
                
                // Capture button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable(enabled = !isProcessing) {
                                imageCapture?.let { capture ->
                                    onProcessingStarted()
                                    captureAndRecognizeText(capture, context) { text ->
                                        if (text != null) {
                                            onTextCaptured(text)
                                        } else {
                                            onTextCaptured("")
                                        }
                                    }
                                }
                            },
                        shape = CircleShape,
                        color = if (isProcessing) 
                            MaterialTheme.colorScheme.surfaceVariant 
                        else 
                            MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Capturar",
                                modifier = Modifier.size(28.dp),
                                tint = if (isProcessing) 
                                    MaterialTheme.colorScheme.onSurfaceVariant 
                                else 
                                    MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                
                // Help text
                Text(
                    text = "Enfoca el nombre del medicamento y pulsa para capturar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

private fun captureAndRecognizeText(
    imageCapture: ImageCapture,
    context: Context,
    onResult: (String?) -> Unit
) {
    val executor = Executors.newSingleThreadExecutor()
    
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage, 
                        imageProxy.imageInfo.rotationDegrees
                    )
                    
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            Log.d("SearchOCR", "Raw OCR: ${visionText.text}")
                            onResult(visionText.text)
                        }
                        .addOnFailureListener { e ->
                            Log.e("SearchOCR", "OCR failed", e)
                            onResult(null)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                            recognizer.close()
                        }
                } else {
                    imageProxy.close()
                    onResult(null)
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e("SearchOCR", "Capture failed", exception)
                onResult(null)
            }
        }
    )
}

/**
 * Cleans up OCR text for medication name search.
 * - Takes the first meaningful line (likely the medication name)
 * - Removes special characters but keeps accents
 * - Trims and normalizes whitespace
 */
private fun cleanOcrText(rawText: String): String {
    if (rawText.isBlank()) return ""
    
    val lines = rawText.lines()
        .map { it.trim() }
        .filter { it.length >= 3 } // Filter out very short lines
    
    // Try to find the most likely medication name line
    // Prioritize lines that are MOSTLY alphabetic (to avoid CN numbers)
    // but can contain some digits (for dosage like "10 mg")
    val candidateLine = lines.firstOrNull { line ->
        val letterCount = line.count { it.isLetter() }
        val digitCount = line.count { it.isDigit() }
        // Must have more letters than digits (name, not CN)
        // and reasonable length
        letterCount > digitCount && letterCount >= 3 && line.length in 3..60
    } ?: lines.firstOrNull { it.any { c -> c.isLetter() } } ?: rawText
    
    // Clean the line: keep letters, spaces, and digits (for dosage like "10 mg")
    // Remove only special chars that aren't useful for medication names
    return candidateLine
        .replace(Regex("[^a-zA-Z0-9áéíóúüñÁÉÍÓÚÜÑ\\s.,/-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60) // Limit length
}

@Composable
fun MedicationSearchItem(
    medication: Medication,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleMedium
            )
            if (medication.laboratory.isNotBlank()) {
                Text(
                    text = medication.laboratory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (medication.nationalCode != null) {
                Text(
                    text = "CN: ${medication.nationalCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
