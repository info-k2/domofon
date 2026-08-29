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
    val githubToken: String = "",
    val githubRepo: String = AppConfig.GITHUB_REPO,
) {
    val isLoggedIn: Boolean get() = bridgeToken.isNotBlank()
}

class SettingsStore(private val context: Context) {
    private val rtspUrl = stringPreferencesKey("rtsp_url")
    private val bridgeUrl = stringPreferencesKey("bridge_url")
    private val bridgeUser = stringPreferencesKey("bridge_user")
    private val bridgeToken = stringPreferencesKey("bridge_token")
    private val githubToken = stringPreferencesKey("github_token")
    private val githubRepo = stringPreferencesKey("github_repo")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            rtspUrl = prefs[rtspUrl] ?: "",
            bridgeUrl = prefs[bridgeUrl] ?: "",
            bridgeUser = prefs[bridgeUser] ?: "",
            bridgeToken = prefs[bridgeToken] ?: "",
            githubToken = prefs[githubToken] ?: "",
            githubRepo = prefs[githubRepo] ?: AppConfig.GITHUB_REPO,
        )
    }

    suspend fun save(value: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[rtspUrl] = value.rtspUrl.trim()
            prefs[bridgeUrl] = value.bridgeUrl.trim().trimEnd('/')
            prefs[bridgeUser] = value.bridgeUser.trim()
            prefs[bridgeToken] = value.bridgeToken.trim()
            prefs[githubToken] = value.githubToken.trim()
            prefs[githubRepo] = value.githubRepo.trim().ifBlank { AppConfig.GITHUB_REPO }
        }
    }
}
