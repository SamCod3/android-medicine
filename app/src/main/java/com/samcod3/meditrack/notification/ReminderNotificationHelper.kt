package com.samcod3.meditrack.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.samcod3.meditrack.MainActivity
import com.samcod3.meditrack.R

/**
 * Helper class for creating and showing reminder notifications.
 */
class ReminderNotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "medication_reminders"
        const val CHANNEL_NAME = "Recordatorios de Medicación"
        const val CHANNEL_DESCRIPTION = "Notificaciones para recordatorios de toma de medicamentos"
        
        // Extras for deep linking
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showReminderNotification(
        reminderId: String, 
        medicationName: String, 
        dosage: String?,
        profileId: String,
        profileName: String
    ) {
        // Intent to open app at the correct profile
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_PROFILE_NAME, profileName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Title: "💊 Nombre del perfil"
        val title = if (profileName.isNotBlank()) "💊 $profileName" else "💊 Recordatorio"
        
        // Content: "Medicamento - dosis" (short and clear)
        val contentText = if (!dosage.isNullOrBlank()) {
            "$medicationName · $dosage"
        } else {
            medicationName
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "✓ Tomado",
                createMarkAsTakenIntent(reminderId)
            )
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
        }
    }
    
    private fun createMarkAsTakenIntent(reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MARK_TAKEN
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        
        return PendingIntent.getBroadcast(
            context,
            "taken_$reminderId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

