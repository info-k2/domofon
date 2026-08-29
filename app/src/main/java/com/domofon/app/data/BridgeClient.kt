package com.domofon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BridgeClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val downloadHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .build(),
) {
    suspend fun openDoor(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val base = settings.bridgeUrl
        val key = settings.bridgeToken
        if (base.isBlank() || key.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Войдите в аккаунт в настройках"),
            )
        }
        val request = Request.Builder()
            .url("$base/v1/door/open")
            .header("X-Api-Key", key)
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val details = response.body?.string()?.take(180).orEmpty()
                    error("Мост: HTTP ${response.code} $details")
                }
            }
        }
    }

    suspend fun login(bridgeUrl: String, username: String, password: String): Result<LoginResult> =
        withContext(Dispatchers.IO) {
            val base = bridgeUrl.trim().trimEnd('/')
            if (base.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Укажите адрес сервера"))
            }
            if (username.isBlank() || password.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Введите логин и пароль"))
            }
            val json = JSONObject()
                .put("username", username)
                .put("password", password)
                .toString()
            val request = Request.Builder()
                .url("$base/v1/login")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            runCatching {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code == 401) error("Неверный логин или пароль")
                    if (!response.isSuccessful) error("Мост: HTTP ${response.code} $body")
                    val parsed = JSONObject(body)
                    val key = parsed.optString("api_key")
                    val stream = parsed.optString("stream_url")
                    if (key.isBlank()) error("Мост не вернул ключ")
                    if (stream.isBlank()) error("Мост не вернул RTSP-ссылку")
                    LoginResult(apiKey = key, streamUrl = stream)
                }
            }
        }

    suspend fun fetchUpdate(settings: AppSettings): Result<ServerUpdateInfo?> = withContext(Dispatchers.IO) {
        val base = settings.bridgeUrl.trim().trimEnd('/')
        val key = settings.bridgeToken
        if (base.isBlank() || key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Войдите в аккаунт"))
        }
        val request = Request.Builder()
            .url("$base/v1/app/update")
            .header("X-Api-Key", key)
            .get()
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (response.code == 404) return@runCatching null
                if (!response.isSuccessful) error("Мост: HTTP ${response.code}")
                val parsed = JSONObject(response.body?.string().orEmpty())
                ServerUpdateInfo(
                    version = parsed.optString("version"),
                    versionCode = parsed.optInt("version_code"),
                    apkUrl = parsed.optString("apk_url"),
                )
            }
        }
    }

    suspend fun downloadApk(
        settings: AppSettings,
        url: String,
        target: java.io.File,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("X-Api-Key", settings.bridgeToken)
                .header("User-Agent", "domofon-android")
                .get()
                .build()
            downloadHttp.newCall(request).execute().use { response ->
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
                            onProgress(percent.coerceIn(0, 100))
                        }
                    }
                }
            }
            if (!target.exists() || target.length() < 1_000_000) {
                error("Скачанный файл слишком маленький — это не APK")
            }
        }
    }
}
