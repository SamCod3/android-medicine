package com.samcod3.meditrack.ui.screens.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.domain.model.Reminder
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    medicationId: String,
    medicationName: String,
    onBackClick: () -> Unit,
    viewModel: ReminderViewModel = koinViewModel { parametersOf(medicationId, medicationName) }
) {
    val reminders by viewModel.reminders.collectAsState()
    val showDialog by viewModel.showDialog.collectAsState()
    val editingReminder by viewModel.editingReminder.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Recordatorios")
                        Text(
                            text = medicationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir recordatorio")
            }
        }
    ) { paddingValues ->
        if (reminders.isEmpty()) {
            EmptyRemindersMessage(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onClick = { viewModel.showEditDialog(reminder) },
                        onToggle = { viewModel.toggleReminder(reminder.id, it) },
                        onDelete = { viewModel.deleteReminder(reminder.id) }
                    )
                }
            }
        }
    }
    
    if (showDialog) {
        ReminderDialog(
            initialReminder = editingReminder,
            onDismiss = { viewModel.hideDialog() },
            onConfirm = { hour, minute, scheduleType, daysOfWeek, intervalDays, dayOfMonth, quantity, type, portion ->
                viewModel.saveReminder(hour, minute, scheduleType, daysOfWeek, intervalDays, dayOfMonth, quantity, type, portion)
            }
        )
    }
}

@Composable
private fun EmptyRemindersMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.height(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin recordatorios",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Pulsa + para añadir uno",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.timeFormatted,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.enabled) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = reminder.scheduleFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = reminder.dosageFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(
    initialReminder: Reminder?,
    onDismiss: () -> Unit,
    onConfirm: (
        hour: Int, 
        minute: Int, 
        scheduleType: ScheduleType,
        daysOfWeek: Int,
        intervalDays: Int,
        dayOfMonth: Int,
        quantity: Int, 
        type: DosageType, 
        portion: Portion?
    ) -> Unit
) {
    val isEditing = initialReminder != null
    val timePickerState = rememberTimePickerState(
        initialHour = initialReminder?.hour ?: 8, 
        initialMinute = initialReminder?.minute ?: 0
    )
    
    // Schedule state
    var selectedScheduleType by remember { mutableStateOf(initialReminder?.scheduleType ?: ScheduleType.DAILY) }
    var selectedDays by remember { mutableIntStateOf(initialReminder?.daysOfWeek ?: 0) }
    var intervalDaysValue by remember { mutableFloatStateOf(initialReminder?.intervalDays?.toFloat() ?: 2f) }
    var dayOfMonthValue by remember { mutableFloatStateOf(initialReminder?.dayOfMonth?.toFloat() ?: 1f) }
    var expandedSchedule by remember { mutableStateOf(false) }
    
    // Dosage state
    var quantity by remember { mutableStateOf(initialReminder?.dosageQuantity?.toString() ?: "1") }
    var selectedDosageType by remember { mutableStateOf(initialReminder?.dosageType ?: DosageType.COMPRIMIDO) }
    var selectedPortion by remember { mutableStateOf(initialReminder?.dosagePortion ?: Portion.ENTERA) }
    var expandedType by remember { mutableStateOf(false) }
    var expandedPortion by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Text(
                    text = if (isEditing) "Editar recordatorio" else "Nuevo recordatorio",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Time picker
                TimePicker(state = timePickerState)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Schedule section
                Text(
                    text = "Frecuencia",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Schedule type dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedSchedule,
                    onExpandedChange = { expandedSchedule = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedScheduleType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSchedule) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedSchedule,
                        onDismissRequest = { expandedSchedule = false }
                    ) {
                        ScheduleType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedScheduleType = type
                                    expandedSchedule = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Schedule type specific options
                when (selectedScheduleType) {
                    ScheduleType.WEEKLY -> {
                        Text("Selecciona los días:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        DayOfWeekSelector(
                            selectedDays = selectedDays,
                            onDaysChanged = { selectedDays = it }
                        )
                    }
                    ScheduleType.INTERVAL -> {
                        Text("Cada ${intervalDaysValue.roundToInt()} días", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = intervalDaysValue,
                            onValueChange = { intervalDaysValue = it },
                            valueRange = 2f..30f,
                            steps = 27,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ScheduleType.MONTHLY -> {
                        Text("Día ${dayOfMonthValue.roundToInt()} de cada mes", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = dayOfMonthValue,
                            onValueChange = { dayOfMonthValue = it },
                            valueRange = 1f..31f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ScheduleType.DAILY -> { /* No extra options */ }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Dosage section
                Text(
                    text = "Dosis",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) quantity = it },
                        label = { Text("Cantidad") },
                        modifier = Modifier.width(100.dp),
                        singleLine = true
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedDosageType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false }
                        ) {
                            DosageType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        selectedDosageType = type
                                        expandedType = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (selectedDosageType == DosageType.PORCION) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedPortion,
                        onExpandedChange = { expandedPortion = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedPortion.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fracción") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPortion) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPortion,
                            onDismissRequest = { expandedPortion = false }
                        ) {
                            Portion.entries.forEach { portion ->
                                DropdownMenuItem(
                                    text = { Text(portion.displayName) },
                                    onClick = {
                                        selectedPortion = portion
                                        expandedPortion = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val qty = quantity.toIntOrNull() ?: 1
                        val portion = if (selectedDosageType == DosageType.PORCION) selectedPortion else null
                        onConfirm(
                            timePickerState.hour,
                            timePickerState.minute,
                            selectedScheduleType,
                            selectedDays,
                            intervalDaysValue.roundToInt(),
                            dayOfMonthValue.roundToInt(),
                            qty,
                            selectedDosageType,
                            portion
                        )
                    }) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
private fun DayOfWeekSelector(
    selectedDays: Int,
    onDaysChanged: (Int) -> Unit
) {
    val days = listOf(
        "L" to 1,
        "M" to 2,
        "X" to 4,
        "J" to 8,
        "V" to 16,
        "S" to 32,
        "D" to 64
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEach { (label, flag) ->
            val isSelected = (selectedDays and flag) != 0
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clickable {
                        onDaysChanged(if (isSelected) selectedDays and flag.inv() else selectedDays or flag)
                    },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
