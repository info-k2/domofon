package com.domofon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.domofon.app.data.AppSettings
import com.domofon.app.data.UpdateStatus

@Composable
fun LiveScreen(
    settings: AppSettings,
    updateStatus: UpdateStatus,
    onOpenDoor: () -> Unit,
    onUseLocal: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onUpdate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Домофон",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
            }
        }

        if (settings.rtspLocalUrl.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !settings.useLocalRtsp,
                    onClick = { onUseLocal(false) },
                    label = { Text("MediaMTX / улица") },
                )
                FilterChip(
                    selected = settings.useLocalRtsp,
                    onClick = { onUseLocal(true) },
                    label = { Text("Прямая камера") },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val url = settings.activeRtspUrl
            if (url.isBlank()) {
                Text("Укажите RTSP-ссылку в настройках")
            } else {
                RtspPlayer(
                    url = url,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val updating = updateStatus !is UpdateStatus.Idle
        OutlinedButton(
            onClick = onUpdate,
            enabled = !updating,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
            val label = when (updateStatus) {
                UpdateStatus.Checking -> "  Проверка…"
                is UpdateStatus.Downloading -> "  Скачивание ${updateStatus.percent}%"
                UpdateStatus.ReadyToInstall -> "  Установка…"
                UpdateStatus.Idle -> "  Обновить приложение"
            }
            Text(label)
        }
        if (updateStatus is UpdateStatus.Downloading) {
            LinearProgressIndicator(
                progress = { updateStatus.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onOpenDoor,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null)
            Text(
                text = "  Открыть дверь",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
