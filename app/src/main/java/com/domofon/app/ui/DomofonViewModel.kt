package com.domofon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.domofon.app.data.AppSettings
import com.domofon.app.data.AppUpdater
import com.domofon.app.data.HomeAssistantClient
import com.domofon.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DomofonViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val ha = HomeAssistantClient()
    private val updater = AppUpdater(application)

    val settings = store.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val updateStatus = updater.status
    val releases = updater.releases
    val releasesError = updater.releasesError

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun saveSettings(value: AppSettings) {
        viewModelScope.launch {
            store.save(value)
            _messages.emit("Сохранено")
            updater.refreshReleases(value.githubToken)
        }
    }

    fun refreshReleases(token: String = settings.value.githubToken) {
        viewModelScope.launch { updater.refreshReleases(token) }
    }

    fun openDoor() {
        viewModelScope.launch {
            ha.openDoor(settings.value)
                .onSuccess { _messages.emit("Команда открытия отправлена") }
                .onFailure { _messages.emit(it.message ?: "Не удалось открыть") }
        }
    }

    fun updateApp(apkUrl: String? = null) {
        viewModelScope.launch {
            updater.update(settings.value.githubToken, apkUrl)
                .onSuccess { _messages.emit("Установите скачанное обновление") }
                .onFailure { _messages.emit(it.message ?: "Обновление не удалось") }
        }
    }
}
