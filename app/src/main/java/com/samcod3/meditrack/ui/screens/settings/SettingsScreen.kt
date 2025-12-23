package com.samcod3.meditrack.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samcod3.meditrack.ai.AIService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val aiService: AIService = koinInject()
    val scope = rememberCoroutineScope()
    
    // State
    var isAvailable by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(true) }
    var lastTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingGeneration by remember { mutableStateOf(false) }
    
    // Load initial status
    LaunchedEffect(Unit) {
        isChecking = true
        isAvailable = aiService.isAvailable()
        isChecking = false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gemini Nano Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Gemini Nano (AICore)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // Status Row
                    StatusRow(
                        label = "Estado del servicio",
                        value = if (isAvailable) "✅ Disponible" else "❌ No disponible",
                        isLoading = isChecking
                    )
                    
                    StatusRow(
                        label = "Modelo",
                        value = "Gemini Nano (on-device)"
                    )
                    
                    StatusRow(
                        label = "Proveedor",
                        value = "Google AICore"
                    )
                    
                    StatusRow(
                        label = "Procesamiento",
                        value = "🔒 Local (sin conexión)"
                    )
                    
                    HorizontalDivider()
                    
                    // Test Generation Button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTestingGeneration = true
                                lastTestResult = null
                                try {
                                    val result = aiService.generateTextResponse("Di 'Hola' en inglés en una sola palabra.")
                                    lastTestResult = if (result != null) {
                                        "✅ Respuesta: $result"
                                    } else {
                                        "❌ No se obtuvo respuesta"
                                    }
                                } catch (e: Exception) {
                                    lastTestResult = "❌ Error: ${e.message}"
                                }
                                isTestingGeneration = false
                            }
                        },
                        enabled = isAvailable && !isTestingGeneration,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingGeneration) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Probando...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Probar generación de texto")
                        }
                    }
                    
                    // Test result
                    if (lastTestResult != null) {
                        Surface(
                            color = if (lastTestResult!!.startsWith("✅")) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lastTestResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    
                    // Refresh button
                    TextButton(
                        onClick = {
                            scope.launch {
                                isChecking = true
                                lastTestResult = null
                                isAvailable = aiService.isAvailable()
                                isChecking = false
                            }
                        },
                        enabled = !isChecking,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Actualizar estado")
                    }
                }
            }
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Información",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Text(
                        "Gemini Nano se ejecuta completamente en tu dispositivo, " +
                        "sin enviar datos a la nube. Esto garantiza privacidad total " +
                        "pero el servicio puede estar ocupado si se hacen muchas peticiones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        "Posibles estados de error:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("• BUSY: Servicio ocupado procesando otra petición", style = MaterialTheme.typography.bodySmall)
                        Text("• QUOTA: Límite de batería alcanzado (reintenta más tarde)", style = MaterialTheme.typography.bodySmall)
                        Text("• BACKGROUND: Las peticiones deben hacerse con la app visible", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            // App version info
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Acerca de",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    StatusRow(
                        label = "Versión de la app",
                        value = "1.0.0"
                    )
                    
                    StatusRow(
                        label = "SDK Gemini",
                        value = "genai-prompt 1.0.0-alpha1"
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
