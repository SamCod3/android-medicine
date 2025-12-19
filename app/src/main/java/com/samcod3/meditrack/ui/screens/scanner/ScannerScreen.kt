package com.samcod3.meditrack.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import com.samcod3.meditrack.domain.util.BarcodeExtractor
import com.samcod3.meditrack.domain.util.BarcodeType
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class ScanMode {
    BARCODE, CAJA
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
    
    // Zoom state
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    
    // Focus indicator state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusIndicator by remember { mutableStateOf(false) }
    
    // PreviewView reference for focus calculations
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    
    // Parafarmacia detection dialog
    var showParafarmaciaDialog by remember { mutableStateOf(false) }
    
    // Update zoom state when camera changes
    LaunchedEffect(camera) {
        camera?.let { cam ->
            val zoomState = cam.cameraInfo.zoomState
            // Initial values
            zoomState.value?.let { state ->
                zoomRatio = state.zoomRatio
                minZoom = state.minZoomRatio
                maxZoom = state.maxZoomRatio
            }
        }
    }
    
    // Hide focus indicator after delay
    LaunchedEffect(showFocusIndicator) {
        if (showFocusIndicator) {
            delay(1500)
            showFocusIndicator = false
        }
    }
    
    // Reset zoom when scan mode changes (camera will rebind)
    LaunchedEffect(scanMode) {
        zoomRatio = 1f
        camera?.cameraControl?.setZoomRatio(1f)
    }
    
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
                            // Classify barcode type before processing
                            when (BarcodeExtractor.classifyBarcode(barcode)) {
                                BarcodeType.MEDICATION, BarcodeType.HEALTH_PRODUCT -> {
                                    viewModel.onBarcodeScanned(barcode)
                                }
                                BarcodeType.COMMERCIAL -> {
                                    showParafarmaciaDialog = true
                                }
                                BarcodeType.UNKNOWN -> {
                                    // Try anyway, might be a plain CN
                                    viewModel.onBarcodeScanned(barcode)
                                }
                            }
                        }
                    },
                    onCameraReady = { cam, imgCap, pv ->
                        camera = cam
                        imageCapture = imgCap
                        previewView = pv
                        // Reset zoom indicator to match camera's actual zoom
                        cam.cameraInfo.zoomState.value?.let { state ->
                            zoomRatio = state.zoomRatio
                            minZoom = state.minZoomRatio
                            maxZoom = state.maxZoomRatio
                        }
                    }
                )
                
                // Tap-to-focus gesture layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                // Trigger focus at tap point
                                camera?.let { cam ->
                                    previewView?.let { pv ->
                                        val factory = SurfaceOrientedMeteringPointFactory(
                                            pv.width.toFloat(),
                                            pv.height.toFloat()
                                        )
                                        val point = factory.createPoint(offset.x, offset.y)
                                        val action = FocusMeteringAction.Builder(point)
                                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                            .build()
                                        cam.cameraControl.startFocusAndMetering(action)
                                        
                                        // Show focus indicator
                                        focusPoint = offset
                                        showFocusIndicator = true
                                    }
                                }
                            }
                        }
                )
                
                // Focus indicator
                AnimatedVisibility(
                    visible = showFocusIndicator && focusPoint != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    focusPoint?.let { point ->
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((point.x - 30.dp.toPx()).toInt(), (point.y - 30.dp.toPx()).toInt()) }
                                .size(60.dp)
                                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        )
                    }
                }
                
                // Scan overlay
                ScanOverlay(scanMode)
                
                // Controls Layer
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Mode Toggle + Zoom level
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ModeToggle(
                            currentMode = scanMode,
                            onModeChanged = { scanMode = it }
                        )
                        
                        // Zoom chips (only show if camera supports zoom > 1x)
                        if (maxZoom > 1.1f) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Calculate zoom levels: start at 1x, step 0.5x, cap at 3x or camera max
                            val effectiveMaxZoom = minOf(maxZoom, 3f)
                            val zoomLevels = remember(minZoom, effectiveMaxZoom) {
                                val levels = mutableListOf<Float>()
                                var level = 1f
                                while (level <= effectiveMaxZoom) {
                                    if (level >= minZoom) levels.add(level)
                                    level += 0.5f
                                }
                                // Ensure we have at least the min zoom
                                if (levels.isEmpty()) levels.add(minZoom)
                                levels
                            }
                            
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                zoomLevels.forEach { level ->
                                    val isSelected = kotlin.math.abs(zoomRatio - level) < 0.1f
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isSelected) Color.White 
                                                else Color.Transparent
                                            )
                                            .clickable { 
                                                camera?.cameraControl?.setZoomRatio(level)
                                                zoomRatio = level
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (level == level.toInt().toFloat()) "${level.toInt()}x" else "%.1fx".format(level),
                                            color = if (isSelected) Color.Black else Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
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
                            if (scanMode == ScanMode.CAJA) {
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
                                            // No CN found - give helpful feedback
                                            Toast.makeText(
                                                context, 
                                            "No se encontró código (ej: 713615.6). Centra y reintenta", 
                                                Toast.LENGTH_LONG
                                            ).show()
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
    
    // Parafarmacia detected dialog
    if (showParafarmaciaDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showParafarmaciaDialog = false },
            title = { 
                Text(
                    "Producto de parafarmacia",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Text(
                    "Este código parece de parafarmacia o cosmética, no es un medicamento registrado en CIMA.\n\nPuedes probar con el modo 'Caja' para leer el CN impreso en el envase, o buscar por nombre.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showParafarmaciaDialog = false
                        scanMode = ScanMode.CAJA
                    }
                ) {
                    Text("Modo Caja")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showParafarmaciaDialog = false
                        onSearchRequested()
                    }
                ) {
                    Text("Buscar por nombre")
                }
            }
        )
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
                text = "Caja",
                isSelected = currentMode == ScanMode.CAJA,
                onClick = { onModeChanged(ScanMode.CAJA) }
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
                                val cn = BarcodeExtractor.extractNationalCode(foundBarcode)
                                Log.d("ScannerHybrid", "Barcode from capture: $cn")
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
            val foundCN = BarcodeExtractor.findCNInText(visionText.text)
            Log.d("ScannerHybrid", "OCR CN: $foundCN")
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

@Composable
private fun CameraPreview(
    scanMode: ScanMode,
    onBarcodeDetected: (String) -> Unit,
    onCameraReady: (Camera, ImageCapture, PreviewView) -> Unit
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
                                    val nationalCode = BarcodeExtractor.extractNationalCode(code)
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
                
                val camera = if (scanMode == ScanMode.BARCODE) {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis, imageCapture)
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                }
                
                onCameraReady(camera, imageCapture, previewView)
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Scan area indicator
            Box(
                modifier = Modifier
                    .size(if (scanMode == ScanMode.BARCODE) 280.dp else 300.dp, if (scanMode == ScanMode.BARCODE) 150.dp else 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Instructions based on mode
            if (scanMode == ScanMode.BARCODE) {
                Text(
                    text = stringResource(R.string.scanner_instruction),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                // OCR Mode - Show example of CN format
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Busca este formato:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Visual example of format (6 digits + dot + digit)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "713615",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = ".6",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Centra el código y pulsa capturar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
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

