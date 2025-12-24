package com.samcod3.meditrack.ui.screens.allreminders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.with
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import com.samcod3.meditrack.domain.model.Reminder
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRemindersScreen(
    profileId: String,
    profileName: String,
    onChangeProfile: () -> Unit,
    onReminderClick: (medicationId: String, medicationName: String) -> Unit,
    onTreatmentClick: () -> Unit,
    viewModel: AllRemindersViewModel = koinViewModel { org.koin.core.parameter.parametersOf(profileId) }
) {
    val todayReminders by viewModel.todayReminders.collectAsState()
    val totalReminders by viewModel.totalReminders.collectAsState()
    
    val today = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")).format(Date())
    
    // Group reminders by time
    val groupedByTime = remember(todayReminders) {
        todayReminders.groupBy { it.timeFormatted }
    }
    
    // Calculate which time slot should be expanded (next upcoming)
    val currentHourMinute = remember {
        val cal = java.util.Calendar.getInstance()
        cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }
    
    val nextTimeSlot = remember(groupedByTime) {
        groupedByTime.keys.firstOrNull { timeStr ->
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val timeMinutes = parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
                timeMinutes >= currentHourMinute
            } else false
        } ?: groupedByTime.keys.lastOrNull()
    }
    
    // State for expanded sections
    var expandedSections by remember(nextTimeSlot) { 
        mutableStateOf(setOf(nextTimeSlot ?: ""))
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { 
                Column {
                    Text("Mi Agenda", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = today.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            },
            actions = {
                IconButton(onClick = onTreatmentClick) {
                    Icon(Icons.Default.List, contentDescription = "Mi Tratamiento")
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
        
        if (todayReminders.isEmpty()) {
            EmptyAgendaMessage(profileName = profileName)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)  // More separation
            ) {
                // Header with summary
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${todayReminders.size} recordatorios para hoy",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$totalReminders en total activos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Collapsible time sections - unified design
                groupedByTime.forEach { (time, reminders) ->
                    val isExpanded = expandedSections.contains(time)
                    val isNextSlot = time == nextTimeSlot
                    
                    item(key = "section_$time") {
                        UnifiedTimeSection(
                            time = time,
                            reminders = reminders,
                            isExpanded = isExpanded,
                            isHighlighted = isNextSlot,
                            onHeaderClick = {
                                expandedSections = if (isExpanded) {
                                    expandedSections - time
                                } else {
                                    expandedSections + time
                                }
                            },
                            onReminderClick = onReminderClick,
                            onToggle = { id, enabled -> viewModel.toggleReminder(id, enabled) },
                            onDelete = { id -> viewModel.deleteReminder(id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAgendaMessage(profileName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "¡Hola, $profileName!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No tienes recordatorios para hoy",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ve al Botiquín para añadir medicamentos y configurar recordatorios",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun TimeHeader(time: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AgendaReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = reminder.dosageFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = reminder.scheduleFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle
            )
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Unified time section - header and content in a single visual container.
 */
@Composable
private fun UnifiedTimeSection(
    time: String,
    reminders: List<Reminder>,
    isExpanded: Boolean,
    isHighlighted: Boolean,
    onHeaderClick: () -> Unit,
    onReminderClick: (medicationId: String, medicationName: String) -> Unit,
    onToggle: (reminderId: String, enabled: Boolean) -> Unit,
    onDelete: (reminderId: String) -> Unit
) {
    val backgroundColor = if (isHighlighted) 
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else 
        MaterialTheme.colorScheme.surfaceContainer
    
    val borderColor = when {
        isHighlighted -> MaterialTheme.colorScheme.primary
        isExpanded -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) // Visible colored border
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }
    
    val borderWidth = when {
        isHighlighted -> 2.dp
        isExpanded -> 1.5.dp
        else -> 0.5.dp
    }
    
    // Elevation for 3D effect - expanded sections come forward
    val elevation = if (isExpanded || isHighlighted) 12.dp else 0.dp
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        shadowElevation = elevation,
        tonalElevation = if (isExpanded) 6.dp else 0.dp
    ) {
        Column {
            // Header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlighted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Count badge
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "(${reminders.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) 
                        Icons.Default.KeyboardArrowDown 
                    else 
                        Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Content - only when expanded
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp
                    )
                ) {

                    reminders.forEachIndexed { index, reminder ->
                        CompactReminderCard(
                            reminder = reminder,
                            containerColor = backgroundColor, // Pass opaque background color
                            onClick = { onReminderClick(reminder.medicationId, reminder.medicationName) },
                            onToggle = { enabled -> onToggle(reminder.id, enabled) },
                            onDelete = { onDelete(reminder.id) }
                        )
                        // Add divider between items (not after last)
                        if (index < reminders.size - 1) {
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collapsible time header for accordion behavior.
 */
@Composable
private fun CollapsibleTimeHeader(
    time: String,
    count: Int,
    isExpanded: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isExpanded && isHighlighted) 
        MaterialTheme.colorScheme.primary 
    else 
        androidx.compose.ui.graphics.Color.Transparent
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .then(
                if (borderColor != androidx.compose.ui.graphics.Color.Transparent)
                    Modifier.padding(2.dp)
                else
                    Modifier
            ),
        color = if (isHighlighted) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        border = if (isExpanded && isHighlighted) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Count badge
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "(${count})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isExpanded) 
                    Icons.Default.KeyboardArrowDown 
                else 
                    Icons.Default.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Compact reminder card for accordion content with swipe gestures.
 * Swipe right = delete, swipe left = toggle enabled/disabled.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
private fun CompactReminderCard(
    reminder: Reminder,
    containerColor: Color, // New parameter
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    // Estado local para manejar la confirmación en línea
    var pendingAction by remember { mutableStateOf<SwipeActionType?>(null) }
    
    // Animación de contenido: Tarjeta Normal vs Barra de Confirmación
    androidx.compose.animation.AnimatedContent(
        targetState = pendingAction,
        transitionSpec = {
            androidx.compose.animation.fadeIn() with androidx.compose.animation.fadeOut()
        },
        label = "card_transformation"
    ) { action ->
        if (action != null) {
            // --- MODO CONFIRMACIÓN (La fila se transforma) ---
            ConfirmationRow(
                action = action,
                medicationName = reminder.medicationName,
                isEnabled = reminder.enabled,
                onConfirm = {
                    when (action) {
                        SwipeActionType.DELETE -> onDelete()
                        SwipeActionType.TOGGLE -> onToggle(!reminder.enabled)
                    }
                    pendingAction = null
                },
                onCancel = { pendingAction = null }
            )
        } else {
            // --- MODO NORMAL (Swipeable) ---
            // Key on enabled state to force recomposition when toggled externally
            androidx.compose.runtime.key(reminder.id, reminder.enabled) {
                val dismissState = rememberSwipeToDismissBoxState(
                    positionalThreshold = { totalDistance -> totalDistance * 0.4f }, // Un poco antes para reaccionar rápido
                    confirmValueChange = { dismissValue ->
                        when (dismissValue) {
                            SwipeToDismissBoxValue.StartToEnd -> {
                                // Detectado swipe a derecha -> Solicitar confirmación de BORRAR
                                pendingAction = SwipeActionType.DELETE
                                false // No descartar automáticamente
                            }
                            SwipeToDismissBoxValue.EndToStart -> {
                                // Detectado swipe a izquierda -> Solicitar confirmación de TOGGLE
                                pendingAction = SwipeActionType.TOGGLE
                                false // No descartar automáticamente
                            }
                            else -> false
                        }
                    }
                )

                // Haptic feedback simple al cruzar umbral
                val context = androidx.compose.ui.platform.LocalContext.current
                val isAboveThreshold = dismissState.progress >= 0.4f && 
                    dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
                
                LaunchedEffect(isAboveThreshold) {
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
                        // Mismo fondo visual que antes durante el arrastre
                         val direction = dismissState.dismissDirection
                        val progress = dismissState.progress
                        val threshold = 0.4f
                        
                        // Color Transition Logic: DEEP -> INTENSE
                        val backgroundColor = when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> {
                                val startColor = Color(0xFF5D1010) 
                                val endColor = Color(0xFFB71C1C) 
                                val fraction = (progress / threshold).coerceIn(0f, 1f)
                                lerp(startColor, endColor, fraction)
                            }
                            SwipeToDismissBoxValue.EndToStart -> {
                                val startColor = if (reminder.enabled) Color(0xFF263238) else Color(0xFF0D47A1)
                                val endColor = if (reminder.enabled) Color(0xFF455A64) else Color(0xFF1565C0)
                                val fraction = (progress / threshold).coerceIn(0f, 1f)
                                lerp(startColor, endColor, fraction)
                            }
                            else -> Color.Transparent
                        }
                        
                        val icon = when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Delete
                            SwipeToDismissBoxValue.EndToStart -> if (reminder.enabled) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp) // Altura ajustada a 72dp
                                .background(containerColor) // Use the passed OPAQUE color
                                .clickable(onClick = onClick)
                                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), // Padding explícito y ajustado
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (reminder.enabled) 
                                    Icons.Default.NotificationsActive 
                                else 
                                    Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = if (reminder.enabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reminder.medicationName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (reminder.enabled)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${reminder.dosageFormatted} · ${reminder.scheduleFormatted}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (reminder.enabled) 0.8f else 0.4f
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

// Estados posibles de acción
private enum class SwipeActionType {
    DELETE, TOGGLE
}

@Composable
private fun ConfirmationRow(
    action: SwipeActionType,
    medicationName: String,
    isEnabled: Boolean, // Solo relevante para toggle
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val backgroundColor = when (action) {
        SwipeActionType.DELETE -> Color(0xFFB71C1C) // Intense Red
        SwipeActionType.TOGGLE -> if (isEnabled) Color(0xFF455A64) else Color(0xFF1565C0) // Dark Blue/Grey
    }
    
    val text = when (action) {
        SwipeActionType.DELETE -> "¿ELIMINAR?"
        SwipeActionType.TOGGLE -> if (isEnabled) "¿DESACTIVAR?" else "¿ACTIVAR?"
    }
    
    val icon = when (action) {
        SwipeActionType.DELETE -> Icons.Default.Delete
        SwipeActionType.TOGGLE -> if (isEnabled) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp) // Altura ajustada a 72dp
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Parte Izquierda: Pregunta
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        // Parte Derecha: Botones de Acción
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Cancelar (Círculo pequeño transparente)
            androidx.compose.material3.IconButton(
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
            
            Spacer(modifier = Modifier.width(32.dp)) // Espaciado aumentado entre botones
            
            // Confirmar (Círculo grande blanco)
            androidx.compose.material3.FilledIconButton(
                onClick = onConfirm,
                modifier = Modifier.size(44.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = backgroundColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Confirmar",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// Constante para altura si no existe, o simplemente usar 64.dp
private val MinTouchTargetSize = 48.dp // O usar heightIn
