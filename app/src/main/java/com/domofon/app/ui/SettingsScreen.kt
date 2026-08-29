package com.domofon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.domofon.app.BuildConfig
import com.domofon.app.data.AppSettings
import com.domofon.app.data.UpdateOffer
import com.domofon.app.data.UpdateStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: AppSettings,
    updateStatus: UpdateStatus,
    updateOffer: UpdateOffer?,
    onLogin: (AppSettings, String) -> Unit,
    onLogout: () -> Unit,
    onUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    var bridgeUrl by rememberSaveable(current.bridgeUrl) { mutableStateOf(current.bridgeUrl) }
    var bridgeUser by rememberSaveable(current.bridgeUser) { mutableStateOf(current.bridgeUser) }
    var bridgePassword by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Аккаунт", style = MaterialTheme.typography.titleMedium)

            if (current.isLoggedIn) {
                Text(
                    "Вы вошли как ${current.bridgeUser.ifBlank { "пользователь" }}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Сервер: ${current.bridgeUrl}", style = MaterialTheme.typography.bodySmall)
                Text("RTSP: ${current.rtspUrl}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Выйти")
                }
            } else {
                OutlinedTextField(
                    value = bridgeUrl,
                    onValueChange = { bridgeUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Адрес сервера") },
                    placeholder = { Text("http://192.168.0.34:8080") },
                    supportingText = { Text("Docker-мост в домашней сети") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = bridgeUser,
                    onValueChange = { bridgeUser = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Логин") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = bridgePassword,
                    onValueChange = { bridgePassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        onLogin(
                            current.copy(
                                bridgeUrl = bridgeUrl,
                                bridgeUser = bridgeUser,
                            ),
                            bridgePassword,
                        )
                        bridgePassword = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Войти")
                }
            }

            Text(
                "Обновления",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Текущая версия: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (current.isLoggedIn) {
                when {
                    updateOffer != null -> {
                        Text(
                            "Доступна новая версия ${updateOffer.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val updating = updateStatus !is UpdateStatus.Idle
                        Button(
                            onClick = onUpdate,
                            enabled = !updating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                            val label = when (updateStatus) {
                                UpdateStatus.Checking -> "  Проверка…"
                                is UpdateStatus.Downloading -> "  Скачивание ${updateStatus.percent}%"
                                UpdateStatus.ReadyToInstall -> "  Установка…"
                                UpdateStatus.Idle -> "  Обновить"
                            }
                            Text(label)
                        }
                    }
                    updateStatus is UpdateStatus.Checking -> {
                        Text("Проверяем обновления…", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text(
                            "У вас последняя версия с сервера",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (updateStatus is UpdateStatus.Downloading) {
                    LinearProgressIndicator(
                        progress = { updateStatus.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    "Войдите — обновление качается с вашего сервера",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
