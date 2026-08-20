package com.domofon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.domofon.app.BuildConfig
import com.domofon.app.data.AppSettings
import com.domofon.app.data.GithubRelease
import com.domofon.app.data.UpdateStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: AppSettings,
    updateStatus: UpdateStatus,
    releases: List<GithubRelease>,
    releasesError: String?,
    onSave: (AppSettings) -> Unit,
    onPairFromLan: (AppSettings) -> Unit,
    onRefreshReleases: (String) -> Unit,
    onUpdate: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var rtspUrl by remember(current.rtspUrl) { mutableStateOf(current.rtspUrl) }
    var bridgeUrl by remember(current.bridgeUrl) { mutableStateOf(current.bridgeUrl) }
    var bridgeToken by remember(current.bridgeToken) { mutableStateOf(current.bridgeToken) }
    var githubToken by remember(current.githubToken) { mutableStateOf(current.githubToken) }

    LaunchedEffect(current.githubToken) {
        onRefreshReleases(current.githubToken)
    }

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
            Text("Видео", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = rtspUrl,
                onValueChange = { rtspUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("RTSP") },
                placeholder = { Text("rtsp://192.168.1.2:8554/door") },
                supportingText = { Text("MediaMTX или прямая ссылка камеры") },
                singleLine = true,
            )

            Text(
                "Сервер",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = bridgeUrl,
                onValueChange = { bridgeUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес Docker-моста") },
                placeholder = { Text("http://192.168.1.2:8787") },
                supportingText = { Text("Сначала подключитесь из домашнего Wi‑Fi") },
                singleLine = true,
            )
            Text(
                if (bridgeToken.isNotBlank()) "Ключ получен" else "Ключ ещё не получен",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = {
                    onPairFromLan(
                        current.copy(
                            rtspUrl = rtspUrl,
                            bridgeUrl = bridgeUrl,
                            bridgeToken = bridgeToken,
                            githubToken = githubToken,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Получить ключ в домашней сети")
            }

            Button(
                onClick = {
                    onSave(
                        current.copy(
                            rtspUrl = rtspUrl,
                            bridgeUrl = bridgeUrl,
                            bridgeToken = bridgeToken,
                            githubToken = githubToken,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }

            Text(
                "О приложении",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Текущая версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Доступные версии",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (releasesError != null && releases.isEmpty()) {
                Text(releasesError, color = MaterialTheme.colorScheme.error)
            } else if (releases.isEmpty()) {
                Text("Пока нет релизов на GitHub", style = MaterialTheme.typography.bodySmall)
            } else {
                releases.forEach { release ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(release.title.ifBlank { release.tag })
                            Text(
                                listOf(release.tag, release.publishedAt).filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (release.apkUrl != null) {
                            TextButton(
                                onClick = { onUpdate(release.apkUrl) },
                                enabled = updateStatus is UpdateStatus.Idle,
                            ) {
                                Text("Установить")
                            }
                        }
                    }
                }
            }

            val updating = updateStatus !is UpdateStatus.Idle
            OutlinedButton(
                onClick = { onUpdate(null) },
                enabled = !updating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                val label = when (updateStatus) {
                    UpdateStatus.Checking -> "  Проверка…"
                    is UpdateStatus.Downloading -> "  Скачивание ${updateStatus.percent}%"
                    UpdateStatus.ReadyToInstall -> "  Установка…"
                    UpdateStatus.Idle -> "  Обновить до последней"
                }
                Text(label)
            }
            if (updateStatus is UpdateStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { updateStatus.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = githubToken,
                onValueChange = { githubToken = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub token") },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text("Нужен, если репозиторий закрытый. Сохраните, затем проверьте обновления.")
                },
                singleLine = true,
            )
        }
    }
}
