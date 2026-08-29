package com.domofon.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun DomofonRoot(viewModel: DomofonViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val isLiveActive = backStack?.destination?.route == "live"

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val updateOffer by viewModel.updateOffer.collectAsStateWithLifecycle()
    val releases by viewModel.releases.collectAsStateWithLifecycle()
    val releasesError by viewModel.releasesError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(settings.isLoggedIn, settings.githubToken, settings.githubRepo) {
        if (settings.isLoggedIn) {
            viewModel.checkUpdatesAfterLogin()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        NavHost(
            navController = nav,
            startDestination = "live",
            modifier = Modifier.padding(padding),
        ) {
            composable("live") {
                LiveScreen(
                    settings = settings,
                    isActive = isLiveActive,
                    onOpenDoor = viewModel::openDoor,
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    current = settings,
                    updateStatus = updateStatus,
                    updateOffer = updateOffer,
                    releases = releases,
                    releasesError = releasesError,
                    onLogin = viewModel::login,
                    onLogout = viewModel::logout,
                    onLoadReleaseHistory = viewModel::loadReleaseHistory,
                    onUpdate = viewModel::updateApp,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
