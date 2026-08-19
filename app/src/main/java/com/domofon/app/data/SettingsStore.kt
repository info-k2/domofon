package com.domofon.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "domofon")

data class AppSettings(
    val rtspUrl: String = "rtsp://192.168.1.2:8554/door",
    val rtspLocalUrl: String = "",
    val useLocalRtsp: Boolean = false,
    val haBaseUrl: String = "",
    val haToken: String = "",
    val haEntityId: String = "switch.domofon_open",
    val githubRepo: String = "",
    val githubToken: String = "",
) {
    val activeRtspUrl: String
        get() = if (useLocalRtsp && rtspLocalUrl.isNotBlank()) rtspLocalUrl else rtspUrl
}

class SettingsStore(private val context: Context) {
    private val rtspUrl = stringPreferencesKey("rtsp_url")
    private val rtspLocalUrl = stringPreferencesKey("rtsp_local_url")
    private val useLocalRtsp = booleanPreferencesKey("use_local_rtsp")
    private val haBaseUrl = stringPreferencesKey("ha_base_url")
    private val haToken = stringPreferencesKey("ha_token")
    private val haEntityId = stringPreferencesKey("ha_entity_id")
    private val githubRepo = stringPreferencesKey("github_repo")
    private val githubToken = stringPreferencesKey("github_token")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            rtspUrl = prefs[rtspUrl] ?: AppSettings().rtspUrl,
            rtspLocalUrl = prefs[rtspLocalUrl] ?: "",
            useLocalRtsp = prefs[useLocalRtsp] ?: false,
            haBaseUrl = prefs[haBaseUrl] ?: "",
            haToken = prefs[haToken] ?: "",
            haEntityId = prefs[haEntityId] ?: AppSettings().haEntityId,
            githubRepo = prefs[githubRepo] ?: "",
            githubToken = prefs[githubToken] ?: "",
        )
    }

    suspend fun save(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[rtspUrl] = value.rtspUrl.trim()
            prefs[rtspLocalUrl] = value.rtspLocalUrl.trim()
            prefs[useLocalRtsp] = value.useLocalRtsp
            prefs[haBaseUrl] = value.haBaseUrl.trim().trimEnd('/')
            prefs[haToken] = value.haToken.trim()
            prefs[haEntityId] = value.haEntityId.trim()
            prefs[githubRepo] = value.githubRepo.trim()
            prefs[githubToken] = value.githubToken.trim()
        }
    }

    suspend fun setUseLocalRtsp(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[useLocalRtsp] = enabled
        }
    }
}
