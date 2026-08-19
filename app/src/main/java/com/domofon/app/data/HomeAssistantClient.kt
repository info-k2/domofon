package com.domofon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HomeAssistantClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun openDoor(settings: AppSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val base = settings.haBaseUrl
        val token = settings.haToken
        val entityId = settings.haEntityId
        if (base.isBlank() || token.isBlank() || entityId.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Заполните адрес Home Assistant, токен и сущность"),
            )
        }
        val domain = entityId.substringBefore('.', missingDelimiterValue = "switch")
        val service = when (domain) {
            "lock" -> "unlock"
            "input_button", "button" -> "press"
            else -> "turn_on"
        }
        val body = """{"entity_id":"$entityId"}"""
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$base/api/services/$domain/$service")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val details = response.body?.string()?.take(180).orEmpty()
                    error("Home Assistant: HTTP ${response.code}" + details)
                }
            }
        }
    }
}
