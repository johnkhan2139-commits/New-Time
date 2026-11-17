package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.timeDataStore: DataStore<Preferences> by preferencesDataStore(name = "time_prefs")

data class TurnServerConfig(
    val stunUrl: String = "stun:stun.l.google.com:19302",
    val turnUrl: String = "",
    val username: String = "",
    val credential: String = ""
) {
    val isTurnConfigured: Boolean
        get() = turnUrl.isNotBlank() && username.isNotBlank() && credential.isNotBlank()
}

class TimePreferences(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val STUN_URL = stringPreferencesKey("stun_url")
        val TURN_URL = stringPreferencesKey("turn_url")
        val TURN_USERNAME = stringPreferencesKey("turn_username")
        val TURN_CREDENTIAL = stringPreferencesKey("turn_credential")
    }

    val isDarkTheme: Flow<Boolean> = context.timeDataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME] ?: true
    }

    val turnConfig: Flow<TurnServerConfig> = context.timeDataStore.data.map { prefs ->
        TurnServerConfig(
            stunUrl = prefs[Keys.STUN_URL] ?: "stun:stun.l.google.com:19302",
            turnUrl = prefs[Keys.TURN_URL] ?: "",
            username = prefs[Keys.TURN_USERNAME] ?: "",
            credential = prefs[Keys.TURN_CREDENTIAL] ?: ""
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.timeDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    suspend fun saveTurnConfig(config: TurnServerConfig) {
        context.timeDataStore.edit { prefs ->
            prefs[Keys.STUN_URL] = config.stunUrl.trim()
            prefs[Keys.TURN_URL] = config.turnUrl.trim()
            prefs[Keys.TURN_USERNAME] = config.username.trim()
            prefs[Keys.TURN_CREDENTIAL] = config.credential
        }
    }

    suspend fun clearAll() {
        context.timeDataStore.edit { it.clear() }
    }
}
