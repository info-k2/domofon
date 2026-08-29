package com.domofon.app.data

data class LoginResult(
    val apiKey: String,
    val streamUrl: String,
    val githubToken: String = "",
    val githubRepo: String = AppConfig.GITHUB_REPO,
)
