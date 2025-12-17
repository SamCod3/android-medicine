package com.samcod3.meditrack.ui.screens.scanner

import android.Manifest
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.samcod3.meditrack.R
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onMedicationScanned: (String) -> Unit,
    viewModel: ScannerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    
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
                    onBarcodeDetected = { barcode ->
                        viewModel.onBarcodeScanned(barcode)
                    },
                    onCameraReady = { cam ->
                        camera = cam
                    }
                )
                
                // Scan overlay
                ScanOverlay()
                
                // Flashlight button
                FloatingActionButton(
                    onClick = {
                        camera?.let { cam ->
                            if (cam.cameraInfo.hasFlashUnit()) {
                                isFlashOn = !isFlashOn
                                cam.cameraControl.enableTorch(isFlashOn)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = if (isFlashOn) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isFlashOn) "Apagar linterna" else "Encender linterna",
                        tint = if (isFlashOn)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
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
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    onCameraReady: (Camera) -> Unit
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
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_DATA_MATRIX,  // DataMatrix for Spanish medications
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
    
    LaunchedEffect(previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(executor) { imageProxy ->
                        @androidx.camera.core.ExperimentalGetImage
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let { code ->
                                        Log.d("Scanner", "Raw barcode: $code")
                                        // Extract national code from barcode
                                        val nationalCode = extractNationalCode(code)
                                        Log.d("Scanner", "Extracted CN: $nationalCode")
                                        onBarcodeDetected(nationalCode)
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                onCameraReady(camera)
            } catch (e: Exception) {
                Log.e("ScannerScreen", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Extracts the national code (CN) from various barcode formats used in Spanish medications.
 * 
 * Supported formats:
 * - EAN-13: 84XXXXXXXC (Spanish prefix 84, CN is digits 3-9)
 * - GS1 DataMatrix: (01)GTIN(17)YYMMDD(10)LOT(21)SN - CN is in GTIN
 * - Plain national code
 */
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
        // Extract digits 3-9 (the national code portion) and remove leading zeros
        return cleanCode.substring(2, 9).trimStart('0')
    }
    
    // Check if it's already a plain national code (6-7 digits)
    if (cleanCode.length in 6..7 && cleanCode.all { it.isDigit() }) {
        return cleanCode.trimStart('0')
    }
    
    // Return as-is if format not recognized
    return cleanCode
}

/**
 * Extracts national code from GS1-128 or GS1 DataMatrix format.
 * Format: (01)GTIN(17)EXPIRY(10)LOT(21)SERIAL
 * 
 * GTIN for Spanish meds: 08470007058328 where:
 * - 0847 = GS1 Spain prefix for pharma
 * - 00070583 = Contains the National Code (CN) - 6-7 digits with possible leading zeros
 * - 28 = check digit area
 * 
 * The CN is typically found at GTIN positions 4-11 (8 digits), removing leading zeros.
 * Example: GTIN 08470007058328 → 00070583 → CN 7058328 or 705832
 */
private fun extractFromGS1(code: String): String {
    Log.d("Scanner", "Parsing GS1: $code")
    
    // AI 01 = GTIN (14 digits)
    val gtinStart = code.indexOf("01")
    if (gtinStart != -1 && code.length >= gtinStart + 16) {
        val gtin = code.substring(gtinStart + 2, gtinStart + 16)
        Log.d("Scanner", "GTIN: $gtin")
        
        // Spanish pharma GTIN: 08470007058328
        // National code is in positions 4-12 (8 digits), before the check digit
        if (gtin.startsWith("0847") || gtin.startsWith("847")) {
            val cnStart = if (gtin.startsWith("0847")) 4 else 3
            // Take 8 digits (positions 4-11), which contains the CN with possible leading zeros
            val cnRaw = gtin.substring(cnStart, minOf(cnStart + 8, gtin.length - 1))
            // Remove leading zeros to get the actual CN (usually 6-7 digits)
            val cn = cnRaw.trimStart('0')
            Log.d("Scanner", "Raw CN: $cnRaw, Extracted CN: $cn")
            return cn
        }
        
        // Alternative: look for 84 pattern anywhere in GTIN
        if (gtin.contains("84")) {
            val idx84 = gtin.indexOf("84")
            // After 84, take up to 8 digits (before check digit)
            val cnRaw = gtin.substring(idx84 + 2, minOf(idx84 + 10, gtin.length - 1))
            val cn = cnRaw.trimStart('0')
            Log.d("Scanner", "Alt Raw CN: $cnRaw, Extracted CN: $cn")
            return cn
        }
    }
    
    return code
}

@Composable
private fun ScanOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Semi-transparent background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
        
        // Scan area indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp, 150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
            ) {
                // Corner indicators
                ScanCorners()
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.scanner_instruction),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanCorners() {
    // Simple scan frame corners would go here
    // For now, just a visible frame
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(16.dp)
            )
    )
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
