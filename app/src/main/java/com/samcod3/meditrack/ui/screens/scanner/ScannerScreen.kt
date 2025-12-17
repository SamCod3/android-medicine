package com.samcod3.meditrack.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.samcod3.meditrack.R
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

enum class ScanMode {
    BARCODE, TEXT_OCR
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onMedicationScanned: (String) -> Unit,
    onSearchRequested: () -> Unit,
    viewModel: ScannerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var scanMode by remember { mutableStateOf(ScanMode.BARCODE) }
    var isProcessingText by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    LaunchedEffect(uiState.scannedCode) {
        uiState.scannedCode?.let { code ->
            onMedicationScanned(code)
            viewModel.resetScanner()
        }
    }
    
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cameraPermission.status.isGranted) {
                CameraPreview(
                    scanMode = scanMode,
                    onBarcodeDetected = { barcode ->
                        if (scanMode == ScanMode.BARCODE) {
                            viewModel.onBarcodeScanned(barcode)
                        }
                    },
                    onCameraReady = { cam, imgCap ->
                        camera = cam
                        imageCapture = imgCap
                    }
                )
                
                // Scan overlay
                ScanOverlay(scanMode)
                
                // Controls Layer
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ModeToggle(
                            currentMode = scanMode,
                            onModeChanged = { scanMode = it }
                        )
                    }
                    
                    // Bottom Bar: Flash & Capture + Manual Search
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Flash Button
                            FloatingActionButton(
                                onClick = {
                                    camera?.let { cam ->
                                        if (cam.cameraInfo.hasFlashUnit()) {
                                            isFlashOn = !isFlashOn
                                            cam.cameraControl.enableTorch(isFlashOn)
                                        }
                                    }
                                },
                                containerColor = if (isFlashOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Linterna"
                                )
                            }
                            
                            // Capture Button (Only for Text Mode)
                            if (scanMode == ScanMode.TEXT_OCR) {
                                FloatingActionButton(
                                    onClick = {
                                        if (!isProcessingText && imageCapture != null) {
                                            isProcessingText = true
                                    captureAndProcessText(
                                        imageCapture!!,
                                        context
                                    ) { resultCN, recognizedText ->
                                        isProcessingText = false
                                        if (resultCN != null) {
                                            onMedicationScanned(resultCN)
                                        } else {
                                            // Check if we found "SN" or "Serial" but no CN
                                            if (recognizedText?.contains("SN", ignoreCase = true) == true || 
                                                recognizedText?.contains("Serial", ignoreCase = true) == true) {
                                                Toast.makeText(context, "Has escaneado el Nº de Serie. Busca el código CN o PC", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "No se encontró CN válido. Prueba la búsqueda manual", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            if (isProcessingText) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Capturar", modifier = Modifier.size(32.dp))
                            }
                        }
                            } else {
                                Spacer(modifier = Modifier.size(56.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        androidx.compose.material3.TextButton(
                            onClick = onSearchRequested,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "¿Problemas? Buscar por nombre",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            } else {
                PermissionRequest(
                    onRequestPermission = { cameraPermission.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
fun ModeToggle(currentMode: ScanMode, onModeChanged: (ScanMode) -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeButton(
                icon = Icons.Default.QrCodeScanner,
                text = "Código",
                isSelected = currentMode == ScanMode.BARCODE,
                onClick = { onModeChanged(ScanMode.BARCODE) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            ModeButton(
                icon = Icons.Default.DocumentScanner,
                text = "Texto",
                isSelected = currentMode == ScanMode.TEXT_OCR,
                onClick = { onModeChanged(ScanMode.TEXT_OCR) }
            )
        }
    }
}

@Composable
fun ModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
    
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = contentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

private fun captureAndProcessText(
    imageCapture: ImageCapture,
    context: Context,
    onResult: (String?, String?) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    // 1. Try Barcode Scanning First (on the high-res image)
                    val barcodeOptions = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_DATA_MATRIX,
                            Barcode.FORMAT_QR_CODE
                        )
                        .build()
                    val barcodeScanner = BarcodeScanning.getClient(barcodeOptions)
                    
                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            val foundBarcode = barcodes.firstOrNull()?.rawValue
                            if (!foundBarcode.isNullOrBlank()) {
                                // Success! We found a barcode in the static image
                                val cn = extractNationalCode(foundBarcode)
                                Log.d("ScannerHybrid", "Barcode found in static image: $cn")
                                onResult(cn, null)
                                imageProxy.close()
                                barcodeScanner.close()
                            } else {
                                // No barcode found, proceed to Text OCR
                                processTextOcr(inputImage, imageProxy, onResult)
                                barcodeScanner.close()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ScannerHybrid", "Static barcode scan failed", e)
                            // Fallback to Text OCR
                            processTextOcr(inputImage, imageProxy, onResult)
                            barcodeScanner.close()
                        }
                } else {
                    imageProxy.close()
                    onResult(null, null)
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e("ScannerHybrid", "Photo capture failed", exception)
                onResult(null, null)
            }
        }
    )
}

private fun processTextOcr(
    inputImage: InputImage,
    imageProxy: ImageProxy,
    onResult: (String?, String?) -> Unit
) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
            val foundCN = findCNInText(visionText.text)
            Log.d("ScannerHybrid", "OCR Result: $foundCN")
            onResult(foundCN, visionText.text)
        }
        .addOnFailureListener { e ->
            Log.e("ScannerHybrid", "Text recognition failed", e)
            onResult(null, null)
        }
        .addOnCompleteListener {
            imageProxy.close()
            recognizer.close()
        }
}

private fun findCNInText(text: String): String? {
    Log.d("ScannerOCR", "Scanned text: $text")
    
    // 1. Explicit CN label: "CN: 123456" or "C.N. 123456.7"
    val cnLabelRegex = Regex("""(?:C\.?N\.?|C\.N)\s*[:\.]?\s*(\d{6,7})""", RegexOption.IGNORE_CASE)
    val matchLabel = cnLabelRegex.find(text)
    if (matchLabel != null) return matchLabel.groupValues[1]
    
    // 2. Look for GTIN-14 (0847...) or EAN-13 (84...) patterns often printed on boxes
    // The OCR might capture "08470007058328" which is a valid GTIN containing the CN.
    val longNumberRegex = Regex("""\b(\d{13,14})\b""")
    val longMatches = longNumberRegex.findAll(text)
    
    for (match in longMatches) {
        val number = match.groupValues[1]
        
        // GTIN-14 Spanish format: 0847XXXXXXXXXC
        if (number.length == 14 && number.startsWith("0847")) {
            // CN is in positions 4-12 (9 digits)
            val cnRaw = number.substring(4, 13)
            return cnRaw.trimStart('0')
        }
        
        // EAN-13 Spanish format: 84XXXXXXXC
        if (number.length == 13 && number.startsWith("84")) {
            // CN is in positions 2-9 (7 digits)
            val cnRaw = number.substring(2, 9)
            return cnRaw.trimStart('0')
        }
    }
    
    // 3. Fallback: look for 6-7 digits isolated
    val codeRegex = Regex("""\b(\d{6,7})\b""")
    // Filter out unlikely candidates (year numbers like 2024, 2025 unless they look like CN)
    val matches = codeRegex.findAll(text)
        .map { it.groupValues[1] }
        .filter { candidate -> 
            // Avoid typical year numbers if they appear isolated
            val num = candidate.toIntOrNull() ?: 0
            // CNs are usually > 600000. Low numbers usually imply other things.
            // But strict filtering might be bad. Let's just prefer starting with 6,7,8,9
            true 
        }
        .toList()
    
    // Heuristic preference
    return matches.firstOrNull { it.length == 7 } ?: matches.firstOrNull()
}

@Composable
private fun CameraPreview(
    scanMode: ScanMode,
    onBarcodeDetected: (String) -> Unit,
    onCameraReady: (Camera, ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            executor.shutdown()
        }
    }
    
    LaunchedEffect(previewView, scanMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            
            // Only attach analyzer if in Barcode mode to save CPU
            if (scanMode == ScanMode.BARCODE) {
                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    @androidx.camera.core.ExperimentalGetImage
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull()?.rawValue?.let { code ->
                                    val nationalCode = extractNationalCode(code)
                                    onBarcodeDetected(nationalCode)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
            }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                // Bind both analysis (for barcode) and capture (for text) always? 
                // Or selectively? Binding both is fine.
                // Note: Some low-end devices can't handle Preview + Analysis + Capture.
                // But modern ones can. To be safe, we can unbind Analysis in Text Mode if we wanted, 
                // but switching is smoother if bound. Let's bind all 3 use cases if possible.
                // If bind fails, we might need fallback.
                
                val camera = if (scanMode == ScanMode.BARCODE) {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis, imageCapture)
                } else {
                    // In Text mode, we might drop analysis to ensure Capture has resources
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                }
                
                onCameraReady(camera, imageCapture)
            } catch (e: Exception) {
                Log.e("ScannerScreen", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ScanOverlay(scanMode: ScanMode) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Semi-transparent background
        // ... (can add overlay visuals here)
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (scanMode == ScanMode.BARCODE) 280.dp else 300.dp, if (scanMode == ScanMode.BARCODE) 150.dp else 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    // Could add borders here
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = if (scanMode == ScanMode.BARCODE) 
                    stringResource(R.string.scanner_instruction) 
                else 
                    "Apunta al Código Nacional (CN) y captura",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp)
            )
        }
    }
}


private fun extractNationalCode(barcode: String): String {
    // Clean up the barcode (remove FNC1 characters and whitespace)
    val cleanCode = barcode.replace("\u001D", "").replace("\\s".toRegex(), "")
    
    Log.d("Scanner", "Clean code: $cleanCode (length: ${cleanCode.length})")
    
    // Check for GS1 DataMatrix format (starts with 01 for GTIN)
    if (cleanCode.startsWith("01") && cleanCode.length >= 16) {
        return extractFromGS1(cleanCode)
    }
    
    // Check for EAN-13 (Spanish medications start with 84)
    if (cleanCode.length == 13 && cleanCode.startsWith("84")) {
        // Extract digits 3-8 (the national code portion, 6 digits) and remove leading zeros
        return cleanCode.substring(2, 8).trimStart('0')
    }
    
    // Check if it's already a plain national code (6-7 digits)
    if (cleanCode.length in 6..7 && cleanCode.all { it.isDigit() }) {
        return cleanCode.trimStart('0')
    }
    
    // Return as-is if format not recognized
    return cleanCode
}

private fun extractFromGS1(code: String): String {
    Log.d("Scanner", "Parsing GS1: $code")
    
    // AI 01 = GTIN (14 digits)
    val gtinStart = code.indexOf("01")
    if (gtinStart != -1 && code.length >= gtinStart + 16) {
        val gtin = code.substring(gtinStart + 2, gtinStart + 16)
        Log.d("Scanner", "GTIN: $gtin")
        
        // Spanish pharma GTIN: 08470007058328
        // National code is in positions 4-12 (9 digits), the last digit is check digit
        if (gtin.startsWith("0847") || gtin.startsWith("847")) {
            val cnStart = if (gtin.startsWith("0847")) 4 else 3
            // Take 9 digits (positions 4-12), excluding the final check digit
            val cnRaw = gtin.substring(cnStart, minOf(cnStart + 9, gtin.length - 1))
            // Remove leading zeros to get the actual CN (usually 6-7 digits)
            val cn = cnRaw.trimStart('0')
            Log.d("Scanner", "Raw CN: $cnRaw, Extracted CN: $cn")
            return cn
        }
        
        // Alternative: look for 84 pattern anywhere in GTIN
        if (gtin.contains("84")) {
            val idx84 = gtin.indexOf("84")
            // After 84, take up to 9 digits (before check digit)
            val cnRaw = gtin.substring(idx84 + 2, minOf(idx84 + 11, gtin.length - 1))
            val cn = cnRaw.trimStart('0')
            Log.d("Scanner", "Alt Raw CN: $cnRaw, Extracted CN: $cn")
            return cn
        }
    }
    
    return code
}

@Composable
private fun ScanCorners() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(16.dp)
            )
    )
    // Could add real corner drawings (Canvas) here
}

@Composable
private fun PermissionRequest(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.scanner_permission_denied),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.scanner_grant_permission))
        }
    }
}

