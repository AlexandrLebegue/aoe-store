package dev.proxboss.aoestore.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.storeDataStore by preferencesDataStore(name = "aoe_store_settings")

class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.storeDataStore

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CACHED_CATALOG = stringPreferencesKey("cached_catalog")
        val CACHED_SERVER = stringPreferencesKey("cached_server")
        val CACHED_AT = longPreferencesKey("cached_at")
    }

    val serverUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.SERVER_URL]?.takeIf { it.isNotBlank() } ?: UrlMapper.DEFAULT_SERVER
    }

    val themeMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.THEME_MODE] ?: "dark"
    }

    suspend fun setServerUrl(value: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SERVER_URL] = UrlMapper.normalizeBase(value)
        }
    }

    suspend fun setThemeMode(value: String) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = value
        }
    }

    suspend fun cacheCatalog(serverUrl: String, json: String) {
        dataStore.edit { preferences ->
            preferences[Keys.CACHED_SERVER] = UrlMapper.normalizeBase(serverUrl)
            preferences[Keys.CACHED_CATALOG] = json
            preferences[Keys.CACHED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun cachedCatalog(serverUrl: String): String? {
        val preferences = dataStore.data.first()
        val cachedServer = preferences[Keys.CACHED_SERVER] ?: return null
        if (cachedServer != UrlMapper.normalizeBase(serverUrl)) return null
        return preferences[Keys.CACHED_CATALOG]
    }
}
