package com.samcod3.meditrack.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samcod3.meditrack.domain.model.SavedMedication
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profileId: String,
    profileName: String,
    onMedicationClick: (String) -> Unit,
    onReminderClick: (medicationId: String, medicationName: String) -> Unit,
    onChangeProfile: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = koinViewModel { parametersOf(profileId) }
) {
    val medications by viewModel.medications.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { 
                Column {
                    Text("Mi Botiquín", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                }
                IconButton(onClick = onChangeProfile) {
                    Icon(Icons.Default.Person, contentDescription = "Cambiar perfil")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            windowInsets = WindowInsets(0)
        )
        
        if (medications.isEmpty()) {
            EmptyMedicineKit()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(medications, key = { it.id }) { medication ->
                    SavedMedicationCard(
                        medication = medication,
                        onClick = { onMedicationClick(medication.nationalCode) },
                        onReminderClick = { onReminderClick(medication.id, medication.name) },
                        onDelete = { viewModel.deleteMedication(medication.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMedicineKit() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Medication,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tu botiquín está vacío",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Escanea o busca medicamentos para añadirlos aquí",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SavedMedicationCard(
    medication: SavedMedication,
    onClick: () -> Unit,
    onReminderClick: () -> Unit,
    onDelete: () -> Unit
) {
    // Estados posibles de acción para Botiquín
    // DELETE -> Muestra confirmación roja
    // NONE -> Estado normal
    // (REMINDERS -> No necesitamos estado persistente, es acción inmediata o visual)
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Animación de contenido: Tarjeta Normal vs Barra de Confirmación
    AnimatedContent(
        targetState = showDeleteConfirmation,
        transitionSpec = {
            fadeIn() with fadeOut()
        },
        label = "botiquin_card_transformation"
    ) { isConfirmingDelete ->
        if (isConfirmingDelete) {
            // --- MODO CONFIRMACIÓN DE BORRADO ---
            BotiquinConfirmationRow(
                medicationName = medication.name,
                onConfirm = {
                    onDelete()
                    showDeleteConfirmation = false
                },
                onCancel = { showDeleteConfirmation = false }
            )
        } else {
            // --- MODO NORMAL (Swipeable) ---
            // Truco para acceder al estado dentro de su propia lambda de confirmación
            // Esto permite ignorar "flings" (gestos rápidos) cortos y forzar el umbral de posición.
            val dismissStateRef = remember { androidx.compose.runtime.mutableStateOf<SwipeToDismissBoxState?>(null) }
            
            val dismissState = rememberSwipeToDismissBoxState(
                positionalThreshold = { totalDistance -> totalDistance * 0.5f },
                confirmValueChange = { dismissValue ->
                    // Verificación anti-gesto rápido:
                    // Si el progreso es menor al 50% (0.5f), rechazamos la acción aunque haya velocidad.
                    val progress = dismissStateRef.value?.progress ?: 0f
                    val isPastThreshold = progress >= 0.5f

                    if (!isPastThreshold && dismissValue != SwipeToDismissBoxValue.Settled) {
                         return@rememberSwipeToDismissBoxState false
                    }

                    when (dismissValue) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            // Derecha -> Borrar -> Solicitar confirmación
                            showDeleteConfirmation = true
                            false // No descartar automáticamente
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            // Izquierda -> Recordatorios -> Navegar INMEDIATAMENTE
                            onReminderClick()
                            false // No descartar (volvemos al centro al navegar/volver)
                        }
                        else -> false
                    }
                }
            )
            // Actualizamos la referencia
            dismissStateRef.value = dismissState

            // Haptic feedback
            val context = androidx.compose.ui.platform.LocalContext.current
            val isAboveThreshold = dismissState.progress >= 0.5f && 
                dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
            
            androidx.compose.runtime.LaunchedEffect(isAboveThreshold) {
                if (isAboveThreshold) {
                     val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (vibrator?.hasVibrator() == true) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(10, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(10)
                        }
                    }
                }
            }

            SwipeToDismissBox(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                state = dismissState,
                backgroundContent = {
                    val direction = dismissState.dismissDirection
                    val progress = dismissState.progress
                    val threshold = 0.5f
                    
                    // Colores Dinámicos
                    // Derecha (Borrar): Oscuro -> Rojo Intenso
                    // Izquierda (Recordatorios): Oscuro -> Azul/Campana
                    
                    val backgroundColor = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            // Delete gradient
                            val startColor = Color(0xFF5D1010) 
                            val endColor = Color(0xFFB71C1C) 
                            val fraction = (progress / threshold).coerceIn(0f, 1f)
                            lerp(startColor, endColor, fraction)
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            // Reminder gradient (Blue)
                            val startColor = Color(0xFF0D47A1)
                            val endColor = Color(0xFF1565C0)
                            val fraction = (progress / threshold).coerceIn(0f, 1f)
                            lerp(startColor, endColor, fraction)
                        }
                        else -> Color.Transparent
                    }
                    
                    val icon = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Delete
                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Notifications
                        else -> null
                    }
                    
                    val alignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        else -> Alignment.CenterEnd
                    }

                    if (direction != SwipeToDismissBoxValue.Settled) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor)
                                .padding(horizontal = 24.dp), 
                            contentAlignment = alignment
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                },
                content = {
                    // Contenido Limpio de la Fila
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .background(MaterialTheme.colorScheme.surface) // Opaco para evitar glitch
                            .clickable(onClick = onClick)
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp), // Un poco mas pequeño que 40 para ser sutil
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = medication.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!medication.description.isNullOrBlank()) {
                                Text(
                                    text = medication.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Código Nacional discreto
                            val code = if (medication.nationalCode.isNotBlank()) "CN: ${medication.nationalCode}" else ""
                            if (code.isNotEmpty()) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        // SIN ICONOS A LA DERECHA (CLEAN)
                    }
                }
            )
        }
    }
}

@Composable
private fun BotiquinConfirmationRow(
    medicationName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFB71C1C)) // Intense Red Background
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Pregunta
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "¿ELIMINAR?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = medicationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Botones de Acción
        Row(verticalAlignment = Alignment.CenterVertically) {
            // CANCELAR (X)
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp)) // Gran separación como en Agenda

            // CONFIRMAR (Check)
            FilledIconButton(
                onClick = onConfirm,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFB71C1C)
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Confirmar",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
