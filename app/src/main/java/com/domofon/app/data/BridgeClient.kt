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
) {
    suspend fun openDoor(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val base = settings.bridgeUrl
        val key = settings.bridgeToken
        if (base.isBlank() || key.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Войдите на сервер в настройках"),
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
                return@withContext Result.failure(IllegalStateException("Укажите адрес моста"))
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
                    val ice = parsed.optJSONArray("ice_servers")?.toString() ?: "[]"
                    if (key.isBlank()) error("Мост не вернул ключ")
                    if (stream.isBlank()) error("Мост не вернул ссылку на видео")
                    LoginResult(apiKey = key, streamUrl = stream, iceServersJson = ice)
                }
            }
        }
}
