package com.domofon.app.data

data class LoginResult(
    val apiKey: String,
    val streamUrl: String,
    val iceServersJson: String = "[]",
    val videoMode: String = "",
)
