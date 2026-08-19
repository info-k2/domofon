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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.domofon.app.data.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var rtspUrl by remember(current.rtspUrl) { mutableStateOf(current.rtspUrl) }
    var rtspLocalUrl by remember(current.rtspLocalUrl) { mutableStateOf(current.rtspLocalUrl) }
    var haBaseUrl by remember(current.haBaseUrl) { mutableStateOf(current.haBaseUrl) }
    var haToken by remember(current.haToken) { mutableStateOf(current.haToken) }
    var haEntityId by remember(current.haEntityId) { mutableStateOf(current.haEntityId) }
    var githubRepo by remember(current.githubRepo) { mutableStateOf(current.githubRepo) }
    var githubToken by remember(current.githubToken) { mutableStateOf(current.githubToken) }

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
            Text(
                "Видео — любая RTSP-ссылка: MediaMTX или камера напрямую.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = rtspUrl,
                onValueChange = { rtspUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("RTSP (MediaMTX или публичный)") },
                placeholder = { Text("rtsp://192.168.1.2:8554/door") },
                singleLine = true,
            )
            OutlinedTextField(
                value = rtspLocalUrl,
                onValueChange = { rtspLocalUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Прямой RTSP камеры (необязательно)") },
                placeholder = { Text("rtsp://192.168.1.10:554/...") },
                singleLine = true,
            )
            Text(
                "Home Assistant",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = haBaseUrl,
                onValueChange = { haBaseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес HA") },
                placeholder = { Text("https://ha.example.com") },
                singleLine = true,
            )
            OutlinedTextField(
                value = haToken,
                onValueChange = { haToken = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Long-lived token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            OutlinedTextField(
                value = haEntityId,
                onValueChange = { haEntityId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Сущность двери") },
                placeholder = { Text("switch.domofon_open") },
                supportingText = {
                    Text("switch / input_boolean / script → turn_on, lock → unlock")
                },
                singleLine = true,
            )
            Text(
                "Обновления с GitHub",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = githubRepo,
                onValueChange = { githubRepo = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Репозиторий") },
                placeholder = { Text("username/domofon") },
                singleLine = true,
            )
            OutlinedTextField(
                value = githubToken,
                onValueChange = { githubToken = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub token (если репозиторий закрытый)") },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text("Для публичного репозитория можно оставить пустым")
                },
                singleLine = true,
            )
            Button(
                onClick = {
                    onSave(
                        current.copy(
                            rtspUrl = rtspUrl,
                            rtspLocalUrl = rtspLocalUrl,
                            haBaseUrl = haBaseUrl,
                            haToken = haToken,
                            haEntityId = haEntityId,
                            githubRepo = githubRepo,
                            githubToken = githubToken,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
        }
    }
}
