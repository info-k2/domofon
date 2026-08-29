package com.domofon.app.data

data class LoginResult(
    val apiKey: String,
    val streamUrl: String,
)

data class ServerUpdateInfo(
    val version: String,
    val versionCode: Int,
    val apkUrl: String,
)
