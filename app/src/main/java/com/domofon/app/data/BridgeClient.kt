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
                IllegalStateException("Заполните адрес Docker-моста и ключ API"),
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
}
