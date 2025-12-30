package com.openemf.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.openemf.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openemf_preferences")

/**
 * Repository for app preferences using DataStore.
 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val MONITORING_INTERVAL = longPreferencesKey("monitoring_interval")
        val DATA_CONTRIBUTION_ENABLED = booleanPreferencesKey("data_contribution_enabled")
    }

    /**
     * Get the current theme mode.
     */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val value = preferences[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    /**
     * Set the theme mode.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode.name
        }
    }

    /**
     * Get monitoring enabled state.
     */
    val monitoringEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.MONITORING_ENABLED] ?: false
    }

    /**
     * Set monitoring enabled state.
     */
    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MONITORING_ENABLED] = enabled
        }
    }

    /**
     * Get monitoring interval.
     */
    val monitoringInterval: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[Keys.MONITORING_INTERVAL] ?: 60_000L
    }

    /**
     * Set monitoring interval.
     */
    suspend fun setMonitoringInterval(interval: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MONITORING_INTERVAL] = interval
        }
    }

    /**
     * Get data contribution enabled state.
     */
    val dataContributionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.DATA_CONTRIBUTION_ENABLED] ?: false
    }

    /**
     * Set data contribution enabled state.
     */
    suspend fun setDataContributionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DATA_CONTRIBUTION_ENABLED] = enabled
        }
    }
}
