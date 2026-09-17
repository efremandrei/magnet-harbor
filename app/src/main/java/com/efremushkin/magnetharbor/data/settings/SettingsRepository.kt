package com.efremushkin.magnetharbor.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val hideZeroSeeders: Boolean = true,
    val darkTheme: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            hideZeroSeeders = preferences[HIDE_ZERO_SEEDERS] ?: true,
            darkTheme = preferences[DARK_THEME] ?: false,
        )
    }

    suspend fun setHideZeroSeeders(enabled: Boolean) {
        context.settingsDataStore.edit { it[HIDE_ZERO_SEEDERS] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.settingsDataStore.edit { it[DARK_THEME] = enabled }
    }

    private companion object {
        val HIDE_ZERO_SEEDERS = booleanPreferencesKey("hide_zero_seeders")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }
}
