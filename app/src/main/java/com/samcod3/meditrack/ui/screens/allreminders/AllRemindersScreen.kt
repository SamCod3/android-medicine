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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
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
import androidx.compose.ui.draw.alpha
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
import com.samcod3.meditrack.ui.components.SwipeableCard
import com.samcod3.meditrack.ui.components.SwipeActionConfig
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
    onLeafletClick: (nationalCode: String) -> Unit,
    onTreatmentClick: () -> Unit,
    viewModel: AllRemindersViewModel = koinViewModel { org.koin.core.parameter.parametersOf(profileId) }
) {
    val todayReminders by viewModel.todayReminders.collectAsState()
    val totalReminders by viewModel.totalReminders.collectAsState()
    val pendingReminders by viewModel.pendingReminders.collectAsState()
    val pastReminders by viewModel.pastReminders.collectAsState()
    
    val today = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")).format(Date())
    
    // Tab state: 0 = Pendientes, 1 = Pasados
    var selectedTab by remember { mutableStateOf(0) }
    
    // Use appropriate data based on tab
    val displayedReminders = when (selectedTab) {
        0 -> pendingReminders
        1 -> pastReminders
        else -> pendingReminders
    }
    
    // Calculate which time slot should be expanded (next upcoming for pending)
    val currentHourMinute = remember {
        val cal = java.util.Calendar.getInstance()
        cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }
    
    val nextTimeSlot = remember(displayedReminders) {
        displayedReminders.keys.firstOrNull { timeStr ->
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val timeMinutes = parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
                timeMinutes >= currentHourMinute
            } else false
        } ?: displayedReminders.keys.firstOrNull()
    }
    
    val expandedSections by viewModel.expandedSections.collectAsState()
    
    // Refresh time periodically
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000) // Every minute
            viewModel.refreshTime()
        }
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
        
        // Tab Row
        androidx.compose.material3.TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            androidx.compose.material3.Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pendientes")
                        if (pendingReminders.values.flatten().isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${pendingReminders.values.flatten().size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            )
            androidx.compose.material3.Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pasados")
                        if (pastReminders.values.flatten().isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${pastReminders.values.flatten().size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
        
        // Initialize expanded section with the first slot if empty
        LaunchedEffect(displayedReminders.keys.firstOrNull()) {
            displayedReminders.keys.firstOrNull()?.let {
                viewModel.initExpandedSection(it)
            }
        }

        if (displayedReminders.isEmpty()) {
            if (selectedTab == 0) {
                // No pending reminders
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "¡Todo al día!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No tienes recordatorios pendientes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // No past reminders
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sin recordatorios pasados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Todos los recordatorios anteriores fueron marcados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Collapsible time sections - unified design
                displayedReminders.forEach { (time, reminders) ->
                    val isExpanded = expandedSections.contains(time)
                    val isNextSlot = time == nextTimeSlot && selectedTab == 0
                    
                    item(key = "section_$time") {
                        UnifiedTimeSection(
                            time = time,
                            reminders = reminders,
                            isExpanded = isExpanded,
                            isHighlighted = isNextSlot,
                            isPastTab = selectedTab == 1,
                            onHeaderClick = { viewModel.toggleSection(time) },
                            onReminderClick = onReminderClick,
                            onLeafletClick = onLeafletClick,
                            onToggle = { id, enabled -> viewModel.toggleReminder(id, enabled) },
                            onDelete = { id -> viewModel.deleteReminder(id) },
                            onMarkTaken = { reminder -> 
                                viewModel.markDose(reminder, com.samcod3.meditrack.data.local.entity.DoseLogEntity.STATUS_TAKEN) 
                            },
                            onMarkSkipped = { reminder -> 
                                viewModel.markDose(reminder, com.samcod3.meditrack.data.local.entity.DoseLogEntity.STATUS_SKIPPED) 
                            }
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
    isPastTab: Boolean = false,
    onHeaderClick: () -> Unit,
    onReminderClick: (String, String) -> Unit,
    onLeafletClick: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onMarkTaken: (Reminder) -> Unit = {},
    onMarkSkipped: (Reminder) -> Unit = {}
) {
    // Background color based on highlight/expansion
    val containerColor = when {
        isHighlighted -> MaterialTheme.colorScheme.primaryContainer
        isExpanded -> MaterialTheme.colorScheme.surfaceContainerHigh 
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    
    // Elevation for 3D effect
    val elevation = if (isExpanded || isHighlighted) 6.dp else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(elevation, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(12.dp)
    ) {
        // --- Header Section ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onHeaderClick)
                .padding(vertical = 8.dp), // Reduced padding (12 -> 8)
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Time
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleLarge, // Kept TitleLarge but reduced padding
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Count Badge
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "(${reminders.size})", // Count restored
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (isHighlighted) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Próximo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // Expand/Collapse Icon
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }

        // --- Content Section (Medications) ---
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reminders.forEachIndexed { index, r ->
                    if (isPastTab) {
                        // Swipeable card for past reminders
                        PastReminderCard(
                            reminder = r,
                            containerColor = containerColor,
                            isFirst = index == 0,
                            onReminderClick = { onReminderClick(r.medicationId, r.medicationName) },
                            onLeafletClick = { onLeafletClick(r.nationalCode) },
                            onMarkTaken = { onMarkTaken(r) },
                            onMarkSkipped = { onMarkSkipped(r) }
                        )
                    } else {
                        // Regular card for pending reminders
                        CompactReminderCard(
                            reminder = r,
                            containerColor = containerColor,
                            onReminderClick = { onReminderClick(r.medicationId, r.medicationName) },
                            onLeafletClick = { onLeafletClick(r.nationalCode) },
                            onToggle = { enabled -> onToggle(r.id, enabled) },
                            onDelete = { onDelete(r.id) }
                        )
                    }
                    
                    // Add Divider if not last item
                    if (index < reminders.size - 1) {
                         HorizontalDivider(
                             modifier = Modifier.padding(horizontal = 8.dp),
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                         )
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CompactReminderCard(
    reminder: Reminder,
    containerColor: Color, // New parameter
    onReminderClick: () -> Unit,
    onLeafletClick: () -> Unit,
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
                // Truco para acceder al estado dentro de su propia lambda de confirmación
                // Esto permite ignorar "flings" (gestos rápidos) cortos y forzar el umbral de posición.
                val dismissStateRef = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }

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
                // Actualizamos la referencia
                dismissStateRef.value = dismissState

                // Haptic feedback simple al cruzar umbral
                val context = androidx.compose.ui.platform.LocalContext.current
                val isAboveThreshold = dismissState.progress >= 0.5f && 
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
                        val threshold = 0.5f
                        
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
                        val borderColor = if (false) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            androidx.compose.ui.graphics.Color.Transparent
                        
                        val haptic = LocalHapticFeedback.current

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = onReminderClick,
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLeafletClick()
                                    }
                                )
                                .then(
                                    if (borderColor != androidx.compose.ui.graphics.Color.Transparent)
                                        Modifier.padding(2.dp)
                                    else
                                        Modifier
                                ),
                            color = containerColor,
                            shape = RoundedCornerShape(12.dp),
                            border = null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
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

/**
 * Reminder card for past tab with swipe-reveal buttons.
 * Swipe to reveal action buttons, tap button to execute action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PastReminderCard(
    reminder: Reminder,
    containerColor: Color,
    isFirst: Boolean = false,
    onReminderClick: () -> Unit,
    onLeafletClick: () -> Unit,
    onMarkTaken: () -> Unit,
    onMarkSkipped: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // State for revealed buttons - keyed by reminder to reset when item changes
    var isRevealed by remember(reminder.id) { mutableStateOf(false) }
    
    // State for peek hint animation (temporary peek, not full reveal)
    var peekHint by remember(reminder.id) { mutableStateOf(false) }
    
    // Auto-close peek hint after showing it
    LaunchedEffect(peekHint) {
        if (peekHint) {
            kotlinx.coroutines.delay(400)
            peekHint = false
        }
    }
    
    // Animated offset for swipe - always go left to reveal buttons on right
    var offsetX by remember(reminder.id) { mutableStateOf(0f) }
    val animatedOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isRevealed) -160f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "reveal_animation"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .height(72.dp)
    ) {
        // Calculate reveal progress based on drag offset or peek hint
        val revealProgress = if (offsetX != 0f) {
            (kotlin.math.abs(offsetX) / 160f).coerceIn(0f, 1f)
        } else if (isRevealed) {
            1f
        } else if (peekHint) {
            0.4f  // Just a small peek (about 60px)
        } else {
            0f
        }
        
        // Animated reveal for smooth transitions
        val animatedReveal by androidx.compose.animation.core.animateFloatAsState(
            targetValue = revealProgress,
            animationSpec = if (offsetX != 0f) {
                androidx.compose.animation.core.snap()
            } else {
                androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 400f)
            },
            label = "reveal_progress"
        )
        
        // Background with buttons that grow progressively (on the LEFT)
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Taken button - grows from 0 to 80dp (first on left)
            Box(
                modifier = Modifier
                    .width((80 * animatedReveal).dp)
                    .fillMaxHeight()
                    .background(Color(0xFF4CAF50))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMarkTaken()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (animatedReveal > 0.3f) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha((animatedReveal - 0.3f) / 0.7f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        if (animatedReveal > 0.6f) {
                            Text(
                                text = "Tomado",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            // Skip button - grows from 0 to 80dp (second on left)
            Box(
                modifier = Modifier
                    .width((80 * animatedReveal).dp)
                    .fillMaxHeight()
                    .background(Color(0xFF757575))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMarkSkipped()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (animatedReveal > 0.3f) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha((animatedReveal - 0.3f) / 0.7f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        if (animatedReveal > 0.6f) {
                            Text(
                                text = "Omitir",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        
        // Foreground content (the card itself)
        val dragOffset = when {
            offsetX != 0f -> offsetX
            isRevealed -> 160f  // Positive: move card right to reveal left buttons
            peekHint -> 60f    // Small peek to show buttons exist
            else -> 0f
        }
        val displayOffset by androidx.compose.animation.core.animateFloatAsState(
            targetValue = dragOffset,
            animationSpec = if (offsetX != 0f) {
                androidx.compose.animation.core.snap()
            } else {
                androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f)
            },
            label = "offset_animation"
        )
        
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = displayOffset.dp)
                .pointerInput(reminder.id) {
                    var leftSwipeAttempt = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Check if user was trying to swipe left (wrong direction)
                            if (leftSwipeAttempt < -30f && offsetX == 0f) {
                                // Trigger hint animation (small peek that auto-closes)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                peekHint = true
                            } else {
                                // Normal right swipe logic
                                isRevealed = kotlin.math.abs(offsetX) > 50f
                            }
                            offsetX = 0f
                            leftSwipeAttempt = 0f
                        },
                        onDragCancel = {
                            offsetX = 0f
                            leftSwipeAttempt = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // Track left swipe attempts (wrong direction)
                            if (dragAmount < 0 && offsetX == 0f) {
                                leftSwipeAttempt += dragAmount
                            }
                            // Only allow swipe right (positive) to reveal buttons
                            offsetX = (offsetX + dragAmount).coerceIn(0f, 200f)
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (isRevealed) {
                            isRevealed = false
                        } else {
                            onReminderClick()
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLeafletClick()
                    }
                ),
            color = containerColor,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = if (isRevealed) 4.dp else 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Swipe indicator (6 dots in 2 columns - iOS style)
                Row(
                    modifier = Modifier.padding(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(2) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.6f),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }
                
                // Medication info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.medicationName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = reminder.dosageFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

            }
        }
    }
}
