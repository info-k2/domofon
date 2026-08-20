package com.domofon.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "domofon")

data class AppSettings(
    val rtspUrl: String = "rtsp://192.168.1.2:8554/door",
    val bridgeUrl: String = "",
    val bridgeToken: String = "",
    val githubToken: String = "",
)

class SettingsStore(private val context: Context) {
    private val rtspUrl = stringPreferencesKey("rtsp_url")
    private val bridgeUrl = stringPreferencesKey("bridge_url")
    private val bridgeToken = stringPreferencesKey("bridge_token")
    private val githubToken = stringPreferencesKey("github_token")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            rtspUrl = prefs[rtspUrl] ?: AppSettings().rtspUrl,
            bridgeUrl = prefs[bridgeUrl] ?: "",
            bridgeToken = prefs[bridgeToken] ?: "",
            githubToken = prefs[githubToken] ?: "",
        )
    }

    suspend fun save(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[rtspUrl] = value.rtspUrl.trim()
            prefs[bridgeUrl] = value.bridgeUrl.trim().trimEnd('/')
            prefs[bridgeToken] = value.bridgeToken.trim()
            prefs[githubToken] = value.githubToken.trim()
        }
    }
}
