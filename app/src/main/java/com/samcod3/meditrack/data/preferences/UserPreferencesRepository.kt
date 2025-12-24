package com.samcod3.meditrack.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for user preferences stored with DataStore.
 * Used to persist the active profile across app restarts.
 */
class UserPreferencesRepository(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
        
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private val ACTIVE_PROFILE_NAME = stringPreferencesKey("active_profile_name")
    }
    
    /**
     * Flow of the active profile ID. Emits null if no profile is set.
     */
    val activeProfileIdFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[ACTIVE_PROFILE_ID] }
    
    /**
     * Flow of the active profile name. Emits null if no profile is set.
     */
    val activeProfileNameFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[ACTIVE_PROFILE_NAME] }
    
    /**
     * Save the active profile when user selects one.
     */
    suspend fun setActiveProfile(profileId: String, profileName: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_PROFILE_ID] = profileId
            preferences[ACTIVE_PROFILE_NAME] = profileName
        }
    }
    
    /**
     * Clear the active profile (when user explicitly logs out or changes profile).
     */
    suspend fun clearActiveProfile() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACTIVE_PROFILE_ID)
            preferences.remove(ACTIVE_PROFILE_NAME)
        }
    }
}
