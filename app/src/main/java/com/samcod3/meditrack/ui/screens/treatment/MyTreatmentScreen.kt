package com.samcod3.meditrack.ui.screens.treatment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samcod3.meditrack.domain.model.Reminder
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTreatmentScreen(
    onBackClick: () -> Unit,
    viewModel: MyTreatmentViewModel = koinViewModel()
) {
    val treatment by viewModel.treatment.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Mi Tratamiento")
                        Text(
                            text = "${treatment.totalCount} medicamentos activos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (treatment.totalCount == 0) {
            EmptyTreatmentMessage(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // DAILY
                if (treatment.daily.isNotEmpty()) {
                    item {
                        ScheduleGroupHeader(
                            icon = Icons.Default.Today,
                            title = "Todos los días",
                            count = treatment.daily.size,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(treatment.daily, key = { "daily-${it.id}" }) { reminder ->
                        TreatmentReminderCard(reminder = reminder)
                    }
                }
                
                // WEEKLY - grouped by days
                if (treatment.weekly.isNotEmpty()) {
                    item {
                        ScheduleGroupHeader(
                            icon = Icons.Default.CalendarToday,
                            title = "Semanalmente",
                            count = treatment.weekly.values.sumOf { it.size },
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    treatment.weekly.forEach { (days, reminders) ->
                        item {
                            Text(
                                text = days,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                        items(reminders, key = { "weekly-${it.id}" }) { reminder ->
                            TreatmentReminderCard(
                                reminder = reminder,
                                showSchedule = false // Already shown in header
                            )
                        }
                    }
                }
                
                // MONTHLY
                if (treatment.monthly.isNotEmpty()) {
                    item {
                        ScheduleGroupHeader(
                            icon = Icons.Default.CalendarMonth,
                            title = "Mensualmente",
                            count = treatment.monthly.size,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    items(treatment.monthly, key = { "monthly-${it.id}" }) { reminder ->
                        TreatmentReminderCard(reminder = reminder)
                    }
                }
                
                // INTERVAL
                if (treatment.interval.isNotEmpty()) {
                    item {
                        ScheduleGroupHeader(
                            icon = Icons.Default.EventRepeat,
                            title = "Por intervalo",
                            count = treatment.interval.size,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(treatment.interval, key = { "interval-${it.id}" }) { reminder ->
                        TreatmentReminderCard(reminder = reminder)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTreatmentMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Medication,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin tratamiento configurado",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Añade medicamentos y configura recordatorios para ver tu tratamiento aquí",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScheduleGroupHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = color),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TreatmentReminderCard(
    reminder: Reminder,
    showSchedule: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.medicationName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = reminder.dosageFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (showSchedule) {
                    Text(
                        text = reminder.scheduleFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = reminder.timeFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
