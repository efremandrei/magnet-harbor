package com.efremushkin.magnetharbor.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.efremushkin.magnetharbor.data.source.SearchSourceConfig
import com.efremushkin.magnetharbor.data.source.SourceKind
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val hideZeroSeeders: Boolean = true,
    val darkTheme: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private val credentials by lazy {
        EncryptedSharedPreferences.create(
            context,
            "source_credentials",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            hideZeroSeeders = preferences[HIDE_ZERO_SEEDERS] ?: true,
            darkTheme = preferences[DARK_THEME] ?: false,
        )
    }

    val sources: Flow<List<SearchSourceConfig>> = context.settingsDataStore.data.map { preferences ->
        decodeSources(preferences[SOURCES_JSON])
    }

    suspend fun setHideZeroSeeders(enabled: Boolean) {
        context.settingsDataStore.edit { it[HIDE_ZERO_SEEDERS] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.settingsDataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun saveSource(config: SearchSourceConfig) {
        context.settingsDataStore.edit { preferences ->
            val current = decodeSources(preferences[SOURCES_JSON]).toMutableList()
            val index = current.indexOfFirst { it.id == config.id }
            if (config.apiKey.isNotBlank()) credentials.edit().putString(config.id, config.apiKey).apply()
            val persisted = config.copy(apiKey = "")
            if (index >= 0) current[index] = persisted else current += persisted
            preferences[SOURCES_JSON] = encodeSources(current)
        }
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOURCES_JSON] = encodeSources(
                decodeSources(preferences[SOURCES_JSON]).map { if (it.id == id) it.copy(enabled = enabled) else it },
            )
        }
    }

    suspend fun deleteSource(id: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOURCES_JSON] = encodeSources(decodeSources(preferences[SOURCES_JSON]).filterNot { it.id == id })
            credentials.edit().remove(id).apply()
        }
    }

    private companion object {
        val HIDE_ZERO_SEEDERS = booleanPreferencesKey("hide_zero_seeders")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SOURCES_JSON = stringPreferencesKey("sources_json")
    }

    private fun decodeSources(value: String?): List<SearchSourceConfig> = runCatching {
        val array = JSONArray(value ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val name = item.optString("name").trim()
                val endpoint = item.optString("endpoint").trim()
                if (name.isNotBlank() && endpoint.isNotBlank()) {
                    add(
                        SearchSourceConfig(
                            id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            name = name,
                            kind = runCatching { SourceKind.valueOf(item.optString("kind")) }.getOrDefault(SourceKind.TORZNAB),
                            endpoint = endpoint,
                        apiKey = credentials.getString(item.optString("id"), "").orEmpty(),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeSources(sources: List<SearchSourceConfig>): String = JSONArray().apply {
        sources.forEach { source ->
            put(JSONObject().apply {
                put("id", source.id)
                put("name", source.name)
                put("kind", source.kind.name)
                put("endpoint", source.endpoint)
                put("enabled", source.enabled)
            })
        }
    }.toString()
}
