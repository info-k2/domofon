package com.domofon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
                IllegalStateException("Заполните адрес моста и получите ключ в домашней сети"),
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

    suspend fun pair(bridgeUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val base = bridgeUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Сначала укажите адрес моста"))
        }
        val request = Request.Builder().url("$base/v1/pair").get().build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 403) {
                    error("Ключ выдаётся только из домашнего Wi‑Fi")
                }
                if (!response.isSuccessful) {
                    error("Мост: HTTP ${response.code} $body")
                }
                val key = org.json.JSONObject(body).optString("api_key")
                if (key.isBlank()) error("Мост не вернул ключ")
                key
            }
        }
    }
}
