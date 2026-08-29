package com.domofon.app.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.domofon.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateOffer(
    val version: String,
    val apkUrl: String,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Downloading(val percent: Int) : UpdateStatus
    data object ReadyToInstall : UpdateStatus
}

class AppUpdater(
    private val app: Application,
    private val bridge: BridgeClient = BridgeClient(),
) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = _status.asStateFlow()

    private val _updateOffer = MutableStateFlow<UpdateOffer?>(null)
    val updateOffer = _updateOffer.asStateFlow()

    fun clearUpdateState() {
        _updateOffer.value = null
        _status.value = UpdateStatus.Idle
    }

    suspend fun checkLatest(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _status.value = UpdateStatus.Checking
            val info = bridge.fetchUpdate(settings).getOrThrow()
            _updateOffer.value = if (info != null && isNewer(info.versionCode)) {
                UpdateOffer(version = info.version, apkUrl = info.apkUrl)
            } else {
                null
            }
        }.onFailure {
            _updateOffer.value = null
        }.also {
            _status.value = UpdateStatus.Idle
        }.map { }
    }

    suspend fun update(settings: AppSettings, apkUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !app.packageManager.canRequestPackageInstalls()
            ) {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                error("Разрешите установку из этого приложения и нажмите «Обновить» ещё раз")
            }

            _status.value = UpdateStatus.Checking
            val file = File(app.cacheDir, "domofon-update.apk")
            bridge.downloadApk(settings, apkUrl, file) { percent ->
                _status.value = UpdateStatus.Downloading(percent)
            }.getOrThrow()
            _status.value = UpdateStatus.ReadyToInstall
            install(file)
        }.also {
            _status.value = UpdateStatus.Idle
        }
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }

    private fun isNewer(serverCode: Int): Boolean = serverCode > BuildConfig.VERSION_CODE
}
