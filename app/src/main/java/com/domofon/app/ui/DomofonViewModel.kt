package com.domofon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.domofon.app.data.AppSettings
import com.domofon.app.data.AppUpdater
import com.domofon.app.data.BridgeClient
import com.domofon.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DomofonViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val bridge = BridgeClient()
    private val updater = AppUpdater(application, bridge)

    val settings = store.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val updateStatus = updater.status
    val updateOffer = updater.updateOffer

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun login(draft: AppSettings, password: String) {
        viewModelScope.launch {
            bridge.login(draft.bridgeUrl, draft.bridgeUser, password)
                .onSuccess { result ->
                    val saved = draft.copy(
                        bridgeToken = result.apiKey,
                        rtspUrl = result.streamUrl,
                    )
                    store.save(saved)
                    updater.checkLatest(saved)
                    _messages.emit("Вход выполнен")
                }
                .onFailure { _messages.emit(it.message ?: "Не удалось войти") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val current = settings.value
            store.save(
                current.copy(
                    bridgeToken = "",
                    rtspUrl = "",
                ),
            )
            updater.clearUpdateState()
            _messages.emit("Вы вышли из аккаунта")
        }
    }

    fun openDoor() {
        viewModelScope.launch {
            bridge.openDoor(settings.value)
                .onSuccess { _messages.emit("Команда открытия отправлена") }
                .onFailure { _messages.emit(it.message ?: "Не удалось открыть") }
        }
    }

    fun updateApp() {
        viewModelScope.launch {
            val current = settings.value
            val offer = updateOffer.value
            if (offer == null) {
                _messages.emit("Обновление недоступно")
                return@launch
            }
            updater.update(current, offer.apkUrl)
                .onSuccess { _messages.emit("Установите скачанное обновление") }
                .onFailure { _messages.emit(it.message ?: "Обновление не удалось") }
        }
    }

    fun checkUpdatesAfterLogin() {
        viewModelScope.launch {
            val current = settings.value
            if (!current.isLoggedIn) return@launch
            updater.checkLatest(current)
        }
    }
}
