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

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun saveSettings(value: AppSettings) {
        viewModelScope.launch {
            store.save(value)
            _messages.emit("Сохранено")
        }
    }

    fun setUseLocalRtsp(enabled: Boolean) {
        viewModelScope.launch { store.setUseLocalRtsp(enabled) }
    }

    fun openDoor() {
        viewModelScope.launch {
            ha.openDoor(settings.value)
                .onSuccess { _messages.emit("Команда открытия отправлена") }
                .onFailure { _messages.emit(it.message ?: "Не удалось открыть") }
        }
    }

    fun updateApp() {
        viewModelScope.launch {
            updater.update(settings.value)
                .onSuccess { _messages.emit("Установите скачанное обновление") }
                .onFailure { _messages.emit(it.message ?: "Обновление не удалось") }
        }
    }
}
