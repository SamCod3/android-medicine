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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.samcod3.meditrack.domain.model.Medication
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onMedicationClick: (Medication) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    
    var showOcrDialog by remember { mutableStateOf(false) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar Medicamento") },
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
            // Search Input with OCR button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
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
                val cleanedText = cleanOcrText(recognizedText)
                viewModel.onQueryChange(cleanedText)
                showOcrDialog = false
                isProcessingOcr = false
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
