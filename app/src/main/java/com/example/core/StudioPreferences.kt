package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "raven_studio_prefs")

data class StudioSettings(
    val defaultAspectRatio: String = "9:16",
    val defaultResolution: String = "1080p",
    val defaultWatermark: Boolean = true,
    val autoVocalSeparation: Boolean = true
)

class StudioPreferences(private val context: Context) {
    private object Keys {
        val ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val RESOLUTION = stringPreferencesKey("default_resolution")
        val WATERMARK = booleanPreferencesKey("default_watermark")
        val VOCAL_SEPARATION = booleanPreferencesKey("auto_vocal_separation")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            defaultAspectRatio = prefs[Keys.ASPECT_RATIO] ?: "9:16",
            defaultResolution = prefs[Keys.RESOLUTION] ?: "1080p",
            defaultWatermark = prefs[Keys.WATERMARK] ?: true,
            autoVocalSeparation = prefs[Keys.VOCAL_SEPARATION] ?: true
        )
    }

    suspend fun updateAspectRatio(ratio: String) {
        context.studioDataStore.edit { it[Keys.ASPECT_RATIO] = ratio }
    }

    suspend fun updateResolution(resolution: String) {
        context.studioDataStore.edit { it[Keys.RESOLUTION] = resolution }
    }

    suspend fun updateWatermark(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.WATERMARK] = enabled }
    }

    suspend fun updateVocalSeparation(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.VOCAL_SEPARATION] = enabled }
    }
}
