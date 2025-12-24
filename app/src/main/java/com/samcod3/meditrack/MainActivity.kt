package com.samcod3.meditrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.samcod3.meditrack.notification.ReminderNotificationHelper
import com.samcod3.meditrack.ui.navigation.MediTrackNavHost
import com.samcod3.meditrack.ui.theme.MediTrackTheme

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Extract profile from notification deep link
        val deepLinkProfileId = intent?.getStringExtra(ReminderNotificationHelper.EXTRA_PROFILE_ID)
        val deepLinkProfileName = intent?.getStringExtra(ReminderNotificationHelper.EXTRA_PROFILE_NAME)
        
        setContent {
            MediTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediTrackNavHost(
                        deepLinkProfileId = deepLinkProfileId,
                        deepLinkProfileName = deepLinkProfileName
                    )
                }
            }
        }
    }
}
