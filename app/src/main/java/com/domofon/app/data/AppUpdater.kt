package com.domofon.app.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class GithubRelease(
    val tag: String,
    val title: String,
    val publishedAt: String,
    val apkUrl: String?,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Downloading(val percent: Int) : UpdateStatus
    data object ReadyToInstall : UpdateStatus
}

class AppUpdater(
    private val app: Application,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = _status.asStateFlow()

    private val _releases = MutableStateFlow<List<GithubRelease>>(emptyList())
    val releases = _releases.asStateFlow()

    private val _releasesError = MutableStateFlow<String?>(null)
    val releasesError = _releasesError.asStateFlow()

    suspend fun refreshReleases(token: String): Result<List<GithubRelease>> = withContext(Dispatchers.IO) {
        runCatching { fetchReleases(token) }
            .onSuccess {
                _releases.value = it
                _releasesError.value = null
            }
            .onFailure { _releasesError.value = it.message }
    }

    suspend fun update(token: String, apkUrl: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
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
                error("Разрешите установку из этого приложения и нажмите Обновить ещё раз")
            }

            _status.value = UpdateStatus.Checking
            val url = apkUrl ?: latestApkUrl(token)
            val file = File(app.cacheDir, "domofon-update.apk")
            download(url, token, file)
            _status.value = UpdateStatus.ReadyToInstall
            install(file)
        }.also {
            _status.value = UpdateStatus.Idle
        }
    }

    private fun fetchReleases(token: String): List<GithubRelease> {
        val request = githubRequest(
            "https://api.github.com/repos/${AppConfig.GITHUB_REPO}/releases?per_page=10",
            token,
        )
        http.newCall(request).execute().use { response ->
            if (response.code == 404) {
                error("Релизов ещё нет. Дождитесь сборки GitHub Actions.")
            }
            if (response.code == 401 || response.code == 403) {
                error("Нет доступа к репозиторию. Вставьте GitHub token с правом repo.")
            }
            if (!response.isSuccessful) error("GitHub: HTTP ${response.code}")
            val array = JSONArray(response.body!!.string())
            val result = mutableListOf<GithubRelease>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                result += GithubRelease(
                    tag = json.optString("tag_name"),
                    title = json.optString("name").ifBlank { json.optString("tag_name") },
                    publishedAt = json.optString("published_at").take(10),
                    apkUrl = apkUrlFrom(json),
                )
            }
            return result
        }
    }

    private fun latestApkUrl(token: String): String {
        val request = githubRequest(
            "https://api.github.com/repos/${AppConfig.GITHUB_REPO}/releases/latest",
            token,
        )
        http.newCall(request).execute().use { response ->
            if (response.code == 404) {
                error("На GitHub ещё нет Release с APK.")
            }
            if (response.code == 401 || response.code == 403) {
                error("Нет доступа к репозиторию. Вставьте GitHub token с правом repo.")
            }
            if (!response.isSuccessful) error("GitHub: HTTP ${response.code}")
            return apkUrlFrom(JSONObject(response.body!!.string()))
                ?: error("В последнем релизе нет APK")
        }
    }

    private fun apkUrlFrom(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                return asset.getString("browser_download_url")
            }
        }
        return null
    }

    private fun githubRequest(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "domofon-android")
            .apply {
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }
            .build()

    private fun download(url: String, token: String, target: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "domofon-android")
            .header("Accept", "application/octet-stream")
            .apply {
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Не удалось скачать APK: HTTP ${response.code}")
            val body = response.body ?: error("Пустой ответ при скачивании")
            val total = body.contentLength()
            target.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        read += n
                        val percent = if (total > 0) ((read * 100) / total).toInt() else 0
                        _status.value = UpdateStatus.Downloading(percent.coerceIn(0, 100))
                    }
                }
            }
        }
        if (!target.exists() || target.length() < 1_000_000) {
            error("Скачанный файл слишком маленький — это не APK")
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
}
