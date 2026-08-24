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
    val rtspUrl: String = "",
    val bridgeUrl: String = "",
    val bridgeUser: String = "",
    val bridgeToken: String = "",
    val iceServersJson: String = "[]",
    val videoMode: String = "",
    val githubToken: String = "",
)

class SettingsStore(private val context: Context) {
    private val rtspUrl = stringPreferencesKey("rtsp_url")
    private val bridgeUrl = stringPreferencesKey("bridge_url")
    private val bridgeUser = stringPreferencesKey("bridge_user")
    private val bridgeToken = stringPreferencesKey("bridge_token")
    private val iceServersJson = stringPreferencesKey("ice_servers_json")
    private val videoMode = stringPreferencesKey("video_mode")
    private val githubToken = stringPreferencesKey("github_token")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            rtspUrl = prefs[rtspUrl] ?: "",
            bridgeUrl = prefs[bridgeUrl] ?: "",
            bridgeUser = prefs[bridgeUser] ?: "",
            bridgeToken = prefs[bridgeToken] ?: "",
            iceServersJson = prefs[iceServersJson] ?: "[]",
            videoMode = prefs[videoMode] ?: "",
            githubToken = prefs[githubToken] ?: "",
        )
    }

    suspend fun save(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[rtspUrl] = value.rtspUrl.trim()
            prefs[bridgeUrl] = value.bridgeUrl.trim().trimEnd('/')
            prefs[bridgeUser] = value.bridgeUser.trim()
            prefs[bridgeToken] = value.bridgeToken.trim()
            prefs[iceServersJson] = value.iceServersJson.trim().ifBlank { "[]" }
            prefs[videoMode] = value.videoMode.trim()
            prefs[githubToken] = value.githubToken.trim()
        }
    }
}
